package com.controlgasto.app.data.local.dao

import androidx.room.*
import com.controlgasto.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAll(): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT * FROM expenses
        WHERE totalInstallments = 1
        AND isRecurring = 0
        AND strftime('%Y', date/1000, 'unixepoch') = :year
        AND strftime('%m', date/1000, 'unixepoch') = :month
        ORDER BY date DESC
    """)
    fun getByMonth(year: String, month: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE totalInstallments > 1 AND isRecurring = 0 ORDER BY date DESC")
    fun getInstallmentExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE cardId IS NOT NULL AND totalInstallments > 1 AND isRecurring = 0 ORDER BY date DESC")
    fun getCardInstallmentExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE isRecurring = 1 ORDER BY date ASC")
    fun getRecurringExpenses(): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT e.* FROM expenses e
        INNER JOIN card_closing_periods ccp
            ON ccp.cardId = e.cardId AND ccp.month = e.billingPeriod
        WHERE e.cardId IS NOT NULL
          AND e.billingPeriod IS NOT NULL
          AND ccp.dueDate >= :monthStart
          AND ccp.dueDate <= :monthEnd
        ORDER BY e.cardId ASC, e.date DESC
    """)
    fun getCreditExpensesByDueMonth(monthStart: Long, monthEnd: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAllOnce(): List<ExpenseEntity>

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("DELETE FROM expenses WHERE cardId = :cardId")
    suspend fun deleteByCardId(cardId: String)
}
