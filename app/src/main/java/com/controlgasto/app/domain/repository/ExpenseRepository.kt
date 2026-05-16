package com.controlgasto.app.domain.repository

import com.controlgasto.app.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getExpenses(): Flow<List<Expense>>
    fun getExpensesByMonth(year: String, month: String): Flow<List<Expense>>
    fun getCreditExpensesByDueMonth(monthStart: Long, monthEnd: Long): Flow<List<Expense>>
    suspend fun addExpense(expense: Expense)
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)
    suspend fun getExpenseById(id: String): Expense?
    suspend fun downgradeExpenses(deletedCardIds: Set<String>)
    suspend fun deleteExpensesByCardId(cardId: String)
}
