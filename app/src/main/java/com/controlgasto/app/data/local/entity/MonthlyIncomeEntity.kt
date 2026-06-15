package com.controlgasto.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.controlgasto.app.domain.model.MonthlyIncome

@Entity(tableName = "monthly_incomes")
data class MonthlyIncomeEntity(
    @PrimaryKey val id: String,
    val month: String,
    val amount: Double,
    val description: String,
    val isHidden: Boolean
)

fun MonthlyIncomeEntity.toDomain() = MonthlyIncome(
    id = id,
    month = month,
    amount = amount,
    description = description,
    isHidden = isHidden
)

fun MonthlyIncome.toEntity() = MonthlyIncomeEntity(
    id = id,
    month = month,
    amount = amount,
    description = description,
    isHidden = isHidden
)
