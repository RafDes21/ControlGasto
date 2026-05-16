package com.controlgasto.app.data.remote

import com.controlgasto.app.domain.model.CreditCard
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreCreditCardSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun userCards(uid: String) =
        firestore.collection("users").document(uid).collection("credit_cards")

    fun getCards(uid: String): Flow<List<CreditCard>> = callbackFlow {
        val listener = userCards(uid).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val list = snapshot?.documents?.mapNotNull { doc ->
                runCatching {
                    CreditCard(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        lastFourDigits = doc.getString("lastFourDigits") ?: "",
                        dueDay = (doc.getLong("dueDay") ?: 1).toInt(),
                        closingDay = (doc.getLong("closingDay") ?: 28).toInt(),
                        colorHex = doc.getLong("colorHex") ?: 0xFF1565C0L,
                        bank = doc.getString("bank") ?: "",
                        type = doc.getString("type") ?: "credit"
                    )
                }.getOrNull()
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun addCard(uid: String, card: CreditCard) {
        userCards(uid).document(card.id).set(card.toMap()).await()
    }

    suspend fun deleteCard(uid: String, card: CreditCard) {
        userCards(uid).document(card.id).delete().await()
    }

    suspend fun getCardsOnce(uid: String): List<CreditCard> {
        return runCatching {
            val snapshot = userCards(uid).get().await()
            snapshot.documents.mapNotNull { doc ->
                runCatching {
                    CreditCard(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        lastFourDigits = doc.getString("lastFourDigits") ?: "",
                        dueDay = (doc.getLong("dueDay") ?: 1).toInt(),
                        closingDay = (doc.getLong("closingDay") ?: 28).toInt(),
                        colorHex = doc.getLong("colorHex") ?: 0xFF1565C0L,
                        bank = doc.getString("bank") ?: "",
                        type = doc.getString("type") ?: "credit"
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    suspend fun getCardById(uid: String, id: String): CreditCard? {
        val doc = userCards(uid).document(id).get().await()
        return if (doc.exists()) CreditCard(
            id = doc.id,
            name = doc.getString("name") ?: "",
            lastFourDigits = doc.getString("lastFourDigits") ?: "",
            dueDay = (doc.getLong("dueDay") ?: 1).toInt(),
            closingDay = (doc.getLong("closingDay") ?: 28).toInt(),
            colorHex = doc.getLong("colorHex") ?: 0xFF1565C0L,
            bank = doc.getString("bank") ?: "",
            type = doc.getString("type") ?: "credit"
        ) else null
    }

    private fun CreditCard.toMap() = mapOf(
        "name" to name,
        "lastFourDigits" to lastFourDigits,
        "dueDay" to dueDay,
        "closingDay" to closingDay,
        "colorHex" to colorHex,
        "bank" to bank,
        "type" to type
    )
}
