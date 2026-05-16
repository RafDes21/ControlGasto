package com.controlgasto.app.data.repository

import com.controlgasto.app.data.local.dao.CreditCardDao
import com.controlgasto.app.data.local.entity.toDomain
import com.controlgasto.app.data.local.entity.toEntity
import com.controlgasto.app.data.remote.FirestoreCreditCardSource
import com.controlgasto.app.domain.model.CreditCard
import com.controlgasto.app.domain.repository.CreditCardRepository
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
class CreditCardRepositoryImpl @Inject constructor(
    private val dao: CreditCardDao,
    private val firestoreSource: FirestoreCreditCardSource,
    private val firebaseAuth: FirebaseAuth
) : CreditCardRepository {

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

    override fun getCreditCards(): Flow<List<CreditCard>> =
        authStateFlow.flatMapLatest { uid ->
            if (uid != null) firestoreSource.getCards(uid)
            else dao.getAll().map { it.map { c -> c.toDomain() } }
        }

    override suspend fun addCreditCard(card: CreditCard) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.addCard(currentUid, card)
        else dao.insert(card.toEntity())
    }

    override suspend fun updateCreditCard(card: CreditCard) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.addCard(currentUid, card)
        else dao.insert(card.toEntity())
    }

    override suspend fun deleteCreditCard(card: CreditCard) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.deleteCard(currentUid, card)
        else dao.delete(card.toEntity())
    }

    override suspend fun getCardById(id: String): CreditCard? {
        val currentUid = uid
        return if (currentUid != null) firestoreSource.getCardById(currentUid, id)
        else dao.getById(id)?.toDomain()
    }

    override suspend fun downgradeToFree(cardToKeep: CreditCard) {
        val currentUid = uid ?: return
        val allCards = firestoreSource.getCardsOnce(currentUid)
        allCards.forEach { firestoreSource.deleteCard(currentUid, it) }
        dao.insert(cardToKeep.toEntity())
    }

    override suspend fun syncToRoomOnLogout() {
        val currentUid = uid ?: return
        runCatching {
            val firestoreCards = firestoreSource.getCardsOnce(currentUid)
            firestoreCards.forEach { card -> dao.insert(card.toEntity()) }
            firestoreCards.forEach { card -> firestoreSource.deleteCard(currentUid, card) }
        }
    }

    private suspend fun migrateRoomToFirestore() {
        val currentUid = uid ?: return
        val roomCards = dao.getAllOnce()
        if (roomCards.isEmpty()) return
        val firestoreCards = firestoreSource.getCardsOnce(currentUid)
        roomCards.forEach { entity ->
            if (firestoreCards.none { it.id == entity.id }) {
                firestoreSource.addCard(currentUid, entity.toDomain())
            }
        }
        roomCards.forEach { dao.delete(it) }
    }
}
