package com.controlgasto.app.domain.model

import java.util.UUID

data class CreditCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lastFourDigits: String,
    val dueDay: Int,
    val closingDay: Int,
    val colorHex: Long = 0xFF1565C0L,
    val bank: String = "",
    val type: String = "credit"
)
