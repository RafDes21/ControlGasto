package com.controlgasto.app.data.repository

import com.controlgasto.app.data.local.dao.CardClosingPeriodDao
import com.controlgasto.app.data.local.entity.toDomain
import com.controlgasto.app.data.local.entity.toEntity
import com.controlgasto.app.data.remote.FirestoreCardClosingPeriodSource
import com.controlgasto.app.data.util.BillingPeriodHelper
import com.controlgasto.app.domain.model.CardClosingPeriod
import com.controlgasto.app.domain.repository.CardClosingPeriodRepository
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
class CardClosingPeriodRepositoryImpl @Inject constructor(
    private val dao: CardClosingPeriodDao,
    private val firestoreSource: FirestoreCardClosingPeriodSource,
    private val firebaseAuth: FirebaseAuth
) : CardClosingPeriodRepository {

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
                if (uid != null) migrateRoomToFirestore(uid)
            }
        }
    }

    override fun getPeriodsForCard(cardId: String): Flow<List<CardClosingPeriod>> =
        authStateFlow.flatMapLatest { uid ->
            if (uid != null) firestoreSource.getPeriodsForCard(uid, cardId)
            else dao.getByCardId(cardId).map { it.map { e -> e.toDomain() } }
        }

    override fun getPeriodsForDueDateInMonthFlow(monthStart: Long, monthEnd: Long): Flow<List<CardClosingPeriod>> =
        authStateFlow.flatMapLatest { uid ->
            if (uid != null) firestoreSource.getPeriodsForDueDateRange(uid, monthStart, monthEnd)
            else dao.getPeriodsForDueDateRangeFlow(monthStart, monthEnd).map { it.map { e -> e.toDomain() } }
        }

    override suspend fun getPeriodForCardAndMonth(cardId: String, month: String): CardClosingPeriod? {
        val currentUid = uid
        return if (currentUid != null) firestoreSource.getPeriodForCardAndMonth(currentUid, cardId, month)
        else dao.getByCardIdAndMonth(cardId, month)?.toDomain()
    }

    override suspend fun getPeriodContainingDate(cardId: String, dateMillis: Long): CardClosingPeriod? {
        val currentUid = uid
        return if (currentUid != null) {
            firestoreSource.getPeriodsOnceForCard(currentUid, cardId)
                .find { dateMillis in it.periodStart..it.periodEnd }
        } else {
            dao.getPeriodContainingDate(cardId, dateMillis)?.toDomain()
        }
    }

    override suspend fun insertAll(periods: List<CardClosingPeriod>) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.insertAll(currentUid, periods)
        else dao.insertAll(periods.map { it.toEntity() })
    }

    override suspend fun update(period: CardClosingPeriod) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.update(currentUid, period)
        else dao.update(period.toEntity())
    }

    override suspend fun updateFromMonth(cardId: String, fromMonth: String, newClosingDay: Int, newDueDay: Int?) {
        val currentUid = uid
        if (currentUid != null) {
            val periods = firestoreSource.getPeriodsOnceForCard(currentUid, cardId)
                .filter { it.month >= fromMonth }
            periods.forEach { p ->
                firestoreSource.update(currentUid, BillingPeriodHelper.recalculatePeriod(p, newClosingDay, newDueDay))
            }
        } else {
            val periods = dao.getFromMonth(cardId, fromMonth)
            periods.forEach { p ->
                dao.update(BillingPeriodHelper.recalculatePeriod(p.toDomain(), newClosingDay, newDueDay).toEntity())
            }
        }
    }

    override suspend fun updateFromDates(cardId: String, fromMonth: String, closingDay: Int, dueDaysOffset: Int) {
        val currentUid = uid
        if (currentUid != null) {
            firestoreSource.getPeriodsOnceForCard(currentUid, cardId)
                .filter { it.month >= fromMonth }
                .forEach { p ->
                    val parts = p.month.split("-")
                    val updated = BillingPeriodHelper.buildPeriodWithOffset(cardId, parts[0].toInt(), parts[1].toInt(), closingDay, dueDaysOffset)
                        .copy(id = p.id, createdAt = p.createdAt)
                    firestoreSource.update(currentUid, updated)
                }
        } else {
            dao.getFromMonth(cardId, fromMonth).forEach { p ->
                val parts = p.month.split("-")
                val updated = BillingPeriodHelper.buildPeriodWithOffset(cardId, parts[0].toInt(), parts[1].toInt(), closingDay, dueDaysOffset)
                    .copy(id = p.id, createdAt = p.createdAt)
                dao.update(updated.toEntity())
            }
        }
    }

    override suspend fun deleteByCardId(cardId: String) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.deleteByCardId(currentUid, cardId)
        else dao.deleteByCardId(cardId)
    }

    override suspend fun syncToRoomOnLogout() {
        val currentUid = uid ?: return
        val periods = runCatching { firestoreSource.getPeriodsOnce(currentUid) }.getOrNull() ?: return
        runCatching {
            dao.deleteAll()
            dao.insertAll(periods.map { it.toEntity() })
        }
        runCatching { firestoreSource.deleteAll(currentUid) }
    }

    private suspend fun migrateRoomToFirestore(uid: String) {
        runCatching {
            val roomPeriods = dao.getAllOnce()
            if (roomPeriods.isEmpty()) return@runCatching
            val existingIds = firestoreSource.getPeriodsOnce(uid).map { it.id }.toSet()
            val toMigrate = roomPeriods.filter { it.id !in existingIds }
            if (toMigrate.isNotEmpty()) {
                firestoreSource.insertAll(uid, toMigrate.map { it.toDomain() })
            }
            dao.deleteAll()
        }
    }
}
