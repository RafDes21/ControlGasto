package com.controlgasto.app.data.repository

import com.controlgasto.app.data.local.dao.MonthlyIncomeDao
import com.controlgasto.app.data.local.entity.toDomain
import com.controlgasto.app.data.local.entity.toEntity
import com.controlgasto.app.data.remote.FirestoreIncomeSource
import com.controlgasto.app.domain.model.MonthlyIncome
import com.controlgasto.app.domain.repository.MonthlyIncomeRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonthlyIncomeRepositoryImpl @Inject constructor(
    private val dao: MonthlyIncomeDao,
    private val firestoreSource: FirestoreIncomeSource,
    private val firebaseAuth: FirebaseAuth
) : MonthlyIncomeRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uid get() = firebaseAuth.currentUser?.uid

    private val authStateFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.uid) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    init {
        scope.launch {
            authStateFlow.collect { uid ->
                if (uid != null) migrateRoomToFirestore()
            }
        }
    }

    override fun getAllIncomes(): Flow<List<MonthlyIncome>> =
        authStateFlow.flatMapLatest { uid ->
            if (uid != null) firestoreSource.getIncomes(uid)
            else dao.getAll().map { list -> list.map { it.toDomain() } }
        }

    override fun getIncomeByMonth(month: String): Flow<MonthlyIncome?> =
        authStateFlow.flatMapLatest { uid ->
            if (uid != null) firestoreSource.getIncomeByMonth(uid, month)
            else dao.getByMonth(month).map { it?.toDomain() }
        }

    override suspend fun saveIncome(income: MonthlyIncome) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.saveIncome(currentUid, income)
        else dao.insert(income.toEntity())
    }

    override suspend fun deleteIncome(income: MonthlyIncome) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.deleteIncome(currentUid, income)
        else dao.delete(income.toEntity())
    }

    override suspend fun syncToRoomOnLogout() {
        val currentUid = uid ?: return
        runCatching {
            val firestoreIncomes = firestoreSource.getIncomesOnce(currentUid)
            dao.deleteAll()
            firestoreIncomes.forEach { dao.insert(it.toEntity()) }
        }
        runCatching { firestoreSource.deleteAllIncomes(currentUid) }
    }

    private suspend fun migrateRoomToFirestore() {
        val currentUid = uid ?: return
        val roomIncomes = dao.getAllOnce()
        if (roomIncomes.isEmpty()) return
        runCatching {
            val existing = firestoreSource.getIncomesOnce(currentUid)
            roomIncomes.forEach { entity ->
                if (existing.none { it.id == entity.id }) {
                    firestoreSource.saveIncome(currentUid, entity.toDomain())
                }
            }
            dao.deleteAll()
        }
    }
}
