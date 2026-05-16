package com.controlgasto.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.controlgasto.app.domain.model.Expense

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amount: Double,
    val categoryId: String,
    val date: Long,
    val note: String,
    val cardId: String? = null,
    val totalInstallments: Int = 1,
    val installmentDueDay: Int = 0,
    val isRecurring: Boolean = false,
    val durationMonths: Int = 0,
    val expenseMonth: String = "",
    val billingPeriod: String? = null
)

fun ExpenseEntity.toDomain() = Expense(
    id, title, amount, categoryId, date, note,
    cardId, totalInstallments, installmentDueDay, isRecurring, durationMonths,
    expenseMonth, billingPeriod
)

fun Expense.toEntity() = ExpenseEntity(
    id, title, amount, categoryId, date, note,
    cardId, totalInstallments, installmentDueDay, isRecurring, durationMonths,
    expenseMonth, billingPeriod
)
