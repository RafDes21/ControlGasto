package com.controlgasto.app.domain.repository

import com.controlgasto.app.domain.model.CardClosingPeriod
import kotlinx.coroutines.flow.Flow

interface CardClosingPeriodRepository {
    fun getPeriodsForCard(cardId: String): Flow<List<CardClosingPeriod>>
    fun getPeriodsForDueDateInMonthFlow(monthStart: Long, monthEnd: Long): Flow<List<CardClosingPeriod>>
    suspend fun getPeriodForCardAndMonth(cardId: String, month: String): CardClosingPeriod?
    suspend fun getPeriodContainingDate(cardId: String, dateMillis: Long): CardClosingPeriod?
    suspend fun insertAll(periods: List<CardClosingPeriod>)
    suspend fun update(period: CardClosingPeriod)
    suspend fun updateFromMonth(cardId: String, fromMonth: String, newClosingDay: Int, newDueDay: Int? = null)
    suspend fun updateFromDates(cardId: String, fromMonth: String, closingDay: Int, dueDaysOffset: Int)
    suspend fun deleteByCardId(cardId: String)
    suspend fun syncToRoomOnLogout()
}
