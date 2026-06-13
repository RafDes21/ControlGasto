package com.controlgasto.app.data.remote

import com.controlgasto.app.domain.model.CardClosingPeriod
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreCardClosingPeriodSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun userPeriods(uid: String) =
        firestore.collection("users").document(uid).collection("card_closing_periods")

    fun getPeriodsForCard(uid: String, cardId: String): Flow<List<CardClosingPeriod>> = callbackFlow {
        val listener = userPeriods(uid).whereEqualTo("cardId", cardId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { doc.toPeriod() }.getOrNull()
                }?.sortedBy { it.month } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getPeriodsForDueDateRange(uid: String, monthStart: Long, monthEnd: Long): Flow<List<CardClosingPeriod>> = callbackFlow {
        val listener = userPeriods(uid)
            .whereGreaterThanOrEqualTo("dueDate", monthStart)
            .whereLessThanOrEqualTo("dueDate", monthEnd)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { doc.toPeriod() }.getOrNull()
                }?.sortedBy { it.dueDate } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getPeriodForCardAndMonth(uid: String, cardId: String, month: String): CardClosingPeriod? {
        return runCatching {
            userPeriods(uid)
                .whereEqualTo("cardId", cardId)
                .whereEqualTo("month", month)
                .get().await()
                .documents.firstOrNull()?.let { runCatching { it.toPeriod() }.getOrNull() }
        }.getOrNull()
    }

    suspend fun insertAll(uid: String, periods: List<CardClosingPeriod>) {
        if (periods.isEmpty()) return
        val batch = firestore.batch()
        periods.forEach { period ->
            batch.set(userPeriods(uid).document(period.id), period.toMap())
        }
        batch.commit().await()
    }

    suspend fun update(uid: String, period: CardClosingPeriod) {
        userPeriods(uid).document(period.id).set(period.toMap()).await()
    }

    suspend fun getPeriodsOnce(uid: String): List<CardClosingPeriod> =
        userPeriods(uid).get().await().documents.mapNotNull { doc ->
            runCatching { doc.toPeriod() }.getOrNull()
        }

    suspend fun getPeriodsOnceForCard(uid: String, cardId: String): List<CardClosingPeriod> {
        return runCatching {
            userPeriods(uid).whereEqualTo("cardId", cardId).get().await()
                .documents.mapNotNull { doc -> runCatching { doc.toPeriod() }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    suspend fun deleteByCardId(uid: String, cardId: String) {
        val docs = runCatching {
            userPeriods(uid).whereEqualTo("cardId", cardId).get().await().documents
        }.getOrDefault(emptyList())
        if (docs.isEmpty()) return
        val batch = firestore.batch()
        docs.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    suspend fun deleteAll(uid: String) {
        val docs = runCatching {
            userPeriods(uid).get().await().documents
        }.getOrDefault(emptyList())
        if (docs.isEmpty()) return
        val batch = firestore.batch()
        docs.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    private fun DocumentSnapshot.toPeriod() = CardClosingPeriod(
        id = id,
        cardId = getString("cardId") ?: "",
        month = getString("month") ?: "",
        closingDay = (getLong("closingDay") ?: 1).toInt(),
        dueDate = getLong("dueDate") ?: 0L,
        periodStart = getLong("periodStart") ?: 0L,
        periodEnd = getLong("periodEnd") ?: 0L,
        createdAt = getLong("createdAt") ?: 0L
    )

    private fun CardClosingPeriod.toMap() = mapOf(
        "cardId" to cardId,
        "month" to month,
        "closingDay" to closingDay,
        "dueDate" to dueDate,
        "periodStart" to periodStart,
        "periodEnd" to periodEnd,
        "createdAt" to createdAt
    )
}
