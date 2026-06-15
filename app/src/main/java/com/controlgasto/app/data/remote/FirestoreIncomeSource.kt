package com.controlgasto.app.data.remote

import com.controlgasto.app.domain.model.MonthlyIncome
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreIncomeSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun userIncomes(uid: String) =
        firestore.collection("users").document(uid).collection("monthlyIncomes")

    fun getIncomes(uid: String): Flow<List<MonthlyIncome>> = callbackFlow {
        val listener = userIncomes(uid)
            .orderBy("month", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { doc.toIncome() }.getOrNull()
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getIncomeByMonth(uid: String, month: String): Flow<MonthlyIncome?> = callbackFlow {
        val listener = userIncomes(uid).document(month)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val income = if (snapshot?.exists() == true) runCatching { snapshot.toIncome() }.getOrNull() else null
                trySend(income)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveIncome(uid: String, income: MonthlyIncome) {
        userIncomes(uid).document(income.month).set(income.toMap()).await()
    }

    suspend fun deleteIncome(uid: String, income: MonthlyIncome) {
        userIncomes(uid).document(income.month).delete().await()
    }

    suspend fun getIncomesOnce(uid: String): List<MonthlyIncome> =
        userIncomes(uid).get().await().documents.mapNotNull { doc ->
            runCatching { doc.toIncome() }.getOrNull()
        }

    suspend fun deleteAllIncomes(uid: String) {
        userIncomes(uid).get().await().documents.forEach { it.reference.delete().await() }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toIncome() = MonthlyIncome(
        id = id,
        month = getString("month") ?: id,
        amount = getDouble("amount") ?: 0.0,
        description = getString("description") ?: "",
        isHidden = getBoolean("isHidden") ?: false
    )

    private fun MonthlyIncome.toMap() = mapOf(
        "month" to month,
        "amount" to amount,
        "description" to description,
        "isHidden" to isHidden
    )
}
