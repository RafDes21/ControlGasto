package com.controlgasto.app.domain.repository

import com.controlgasto.app.domain.model.MonthlyIncome
import kotlinx.coroutines.flow.Flow

interface MonthlyIncomeRepository {
    fun getAllIncomes(): Flow<List<MonthlyIncome>>
    fun getIncomeByMonth(month: String): Flow<MonthlyIncome?>
    suspend fun saveIncome(income: MonthlyIncome)
    suspend fun deleteIncome(income: MonthlyIncome)
    suspend fun syncToRoomOnLogout()
}
