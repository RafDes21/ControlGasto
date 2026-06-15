package com.controlgasto.app.domain.model

data class MonthlyIncome(
    val id: String,
    val month: String,
    val amount: Double,
    val description: String = "",
    val isHidden: Boolean = false
)
