package com.controlgasto.app.data.local.dao

import androidx.room.*
import com.controlgasto.app.data.local.entity.CardClosingPeriodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardClosingPeriodDao {
    @Query("SELECT * FROM card_closing_periods WHERE cardId = :cardId ORDER BY month ASC")
    fun getByCardId(cardId: String): Flow<List<CardClosingPeriodEntity>>

    @Query("SELECT * FROM card_closing_periods WHERE cardId = :cardId AND month = :month LIMIT 1")
    suspend fun getByCardIdAndMonth(cardId: String, month: String): CardClosingPeriodEntity?

    @Query("SELECT * FROM card_closing_periods WHERE dueDate >= :monthStart AND dueDate <= :monthEnd ORDER BY dueDate ASC")
    suspend fun getPeriodsForDueDateRange(monthStart: Long, monthEnd: Long): List<CardClosingPeriodEntity>

    @Query("SELECT * FROM card_closing_periods WHERE dueDate >= :monthStart AND dueDate <= :monthEnd ORDER BY dueDate ASC")
    fun getPeriodsForDueDateRangeFlow(monthStart: Long, monthEnd: Long): Flow<List<CardClosingPeriodEntity>>

    @Query("SELECT * FROM card_closing_periods WHERE cardId = :cardId AND month >= :fromMonth ORDER BY month ASC")
    suspend fun getFromMonth(cardId: String, fromMonth: String): List<CardClosingPeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(periods: List<CardClosingPeriodEntity>)

    @Update
    suspend fun update(period: CardClosingPeriodEntity)

    @Query("DELETE FROM card_closing_periods WHERE cardId = :cardId")
    suspend fun deleteByCardId(cardId: String)

    @Query("SELECT * FROM card_closing_periods")
    suspend fun getAllOnce(): List<CardClosingPeriodEntity>

    @Query("DELETE FROM card_closing_periods")
    suspend fun deleteAll()
}
