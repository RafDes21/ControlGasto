package com.controlgasto.app.domain.model

import java.util.UUID

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val categoryId: String,
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val cardId: String? = null,
    val totalInstallments: Int = 1,
    val installmentDueDay: Int = 0,
    val isRecurring: Boolean = false,
    val durationMonths: Int = 0,
    val expenseMonth: String = "",
    val billingPeriod: String? = null
) {
    val isInstallment: Boolean get() = totalInstallments > 1 && !isRecurring
    val installmentAmount: Double get() = if (isInstallment) amount / totalInstallments else amount
}
