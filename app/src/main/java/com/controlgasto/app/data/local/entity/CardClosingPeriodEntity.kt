package com.controlgasto.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.controlgasto.app.domain.model.CardClosingPeriod

@Entity(tableName = "card_closing_periods", indices = [Index("cardId"), Index("month")])
data class CardClosingPeriodEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val month: String,
    val closingDay: Int,
    val dueDate: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val createdAt: Long
)

fun CardClosingPeriodEntity.toDomain() = CardClosingPeriod(id, cardId, month, closingDay, dueDate, periodStart, periodEnd, createdAt)
fun CardClosingPeriod.toEntity() = CardClosingPeriodEntity(id, cardId, month, closingDay, dueDate, periodStart, periodEnd, createdAt)
