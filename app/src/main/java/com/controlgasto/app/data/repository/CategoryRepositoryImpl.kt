package com.controlgasto.app.data.repository

import com.controlgasto.app.data.local.AppDatabase
import com.controlgasto.app.data.local.dao.CategoryDao
import com.controlgasto.app.data.local.entity.toDomain
import com.controlgasto.app.data.local.entity.toEntity
import com.controlgasto.app.data.remote.FirestoreCategorySource
import com.controlgasto.app.domain.model.Category
import com.controlgasto.app.domain.repository.CategoryRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val firestoreSource: FirestoreCategorySource,
    private val firebaseAuth: FirebaseAuth
) : CategoryRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uid get() = firebaseAuth.currentUser?.uid

    private val authStateFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.uid) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    init {
        scope.launch {
            if (dao.count() == 0) {
                AppDatabase.getDefaultCategories().forEach { dao.insert(it) }
            }
        }
        scope.launch {
            authStateFlow.collect { uid ->
                if (uid != null) migrateRoomToFirestore()
            }
        }
    }

    override fun getCategories(): Flow<List<Category>> =
        authStateFlow.flatMapLatest { uid ->
            if (uid != null) firestoreSource.getCategories(uid)
            else dao.getAll().map { it.map { c -> c.toDomain() } }
        }

    override suspend fun addCategory(category: Category) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.addCategory(currentUid, category)
        else dao.insert(category.toEntity())
    }

    override suspend fun deleteCategory(category: Category) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.deleteCategory(currentUid, category)
        else dao.delete(category.toEntity())
    }

    override suspend fun downgradeCategories() {
        val currentUid = uid ?: return
        runCatching {
            val firestoreCategories = firestoreSource.getCategoriesOnce(currentUid)
            firestoreCategories.forEach { dao.insert(it.toEntity()) }
            firestoreSource.deleteAllCategories(currentUid)
        }
    }

    private suspend fun migrateRoomToFirestore() {
        val currentUid = uid ?: return
        runCatching {
            val existing = firestoreSource.getCategoriesOnce(currentUid)
            if (existing.isEmpty()) {
                val roomCategories = dao.getAllOnce()
                val toUpload = if (roomCategories.isNotEmpty()) roomCategories.map { it.toDomain() }
                              else AppDatabase.getDefaultCategories().map { it.toDomain() }
                toUpload.forEach { firestoreSource.addCategory(currentUid, it) }
            } else {
                val roomCategories = dao.getAllOnce()
                roomCategories.filter { entity ->
                    !entity.isDefault && existing.none { it.id == entity.id }
                }.forEach { firestoreSource.addCategory(currentUid, it.toDomain()) }
            }
        }
    }
}
