package com.controlgasto.app.data.remote

import com.controlgasto.app.domain.model.Category
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreCategorySource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun userCategories(uid: String) =
        firestore.collection("users").document(uid).collection("categories")

    fun getCategories(uid: String): Flow<List<Category>> = callbackFlow {
        val listener = userCategories(uid).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val list = snapshot?.documents?.mapNotNull { doc ->
                runCatching { doc.toCategory() }.getOrNull()
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun addCategory(uid: String, category: Category) {
        userCategories(uid).document(category.id).set(category.toMap()).await()
    }

    suspend fun deleteCategory(uid: String, category: Category) {
        userCategories(uid).document(category.id).delete().await()
    }

    suspend fun getCategoriesOnce(uid: String): List<Category> =
        userCategories(uid).get().await().documents.mapNotNull { doc ->
            runCatching { doc.toCategory() }.getOrNull()
        }

    suspend fun deleteAllCategories(uid: String) {
        userCategories(uid).get().await().documents.forEach { it.reference.delete().await() }
    }

    private fun DocumentSnapshot.toCategory() = Category(
        id = id,
        name = getString("name") ?: "",
        icon = getString("icon") ?: "📦",
        color = getLong("color") ?: 0xFF795548L,
        isDefault = getBoolean("isDefault") ?: false
    )

    private fun Category.toMap() = mapOf(
        "name" to name,
        "icon" to icon,
        "color" to color,
        "isDefault" to isDefault
    )
}
