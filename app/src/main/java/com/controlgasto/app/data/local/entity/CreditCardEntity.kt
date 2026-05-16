package com.controlgasto.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.controlgasto.app.domain.model.CreditCard

@Entity(tableName = "credit_cards")
data class CreditCardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lastFourDigits: String,
    val dueDay: Int,
    val closingDay: Int,
    val colorHex: Long,
    val bank: String = "",
    val type: String = "credit"
)

fun CreditCardEntity.toDomain() = CreditCard(id, name, lastFourDigits, dueDay, closingDay, colorHex, bank, type)
fun CreditCard.toEntity() = CreditCardEntity(id, name, lastFourDigits, dueDay, closingDay, colorHex, bank, type)
