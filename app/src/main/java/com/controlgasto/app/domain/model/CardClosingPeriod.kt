package com.controlgasto.app.domain.model

import java.util.UUID

data class CardClosingPeriod(
    val id: String = UUID.randomUUID().toString(),
    val cardId: String,
    val month: String,
    val closingDay: Int,
    val dueDate: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val createdAt: Long = System.currentTimeMillis()
)
