package com.controlgasto.app.domain.repository

import com.controlgasto.app.domain.model.CreditCard
import kotlinx.coroutines.flow.Flow

interface CreditCardRepository {
    fun getCreditCards(): Flow<List<CreditCard>>
    suspend fun addCreditCard(card: CreditCard)
    suspend fun updateCreditCard(card: CreditCard)
    suspend fun deleteCreditCard(card: CreditCard)
    suspend fun getCardById(id: String): CreditCard?
    suspend fun downgradeToFree(cardToKeep: CreditCard)
    suspend fun syncToRoomOnLogout()
}
