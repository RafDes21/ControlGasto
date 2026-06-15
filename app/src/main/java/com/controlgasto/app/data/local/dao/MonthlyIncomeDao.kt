package com.controlgasto.app.data.local.dao

import androidx.room.*
import com.controlgasto.app.data.local.entity.MonthlyIncomeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyIncomeDao {

    @Query("SELECT * FROM monthly_incomes ORDER BY month DESC")
    fun getAll(): Flow<List<MonthlyIncomeEntity>>

    @Query("SELECT * FROM monthly_incomes WHERE month = :month LIMIT 1")
    fun getByMonth(month: String): Flow<MonthlyIncomeEntity?>

    @Query("SELECT * FROM monthly_incomes ORDER BY month DESC")
    suspend fun getAllOnce(): List<MonthlyIncomeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MonthlyIncomeEntity)

    @Delete
    suspend fun delete(entity: MonthlyIncomeEntity)

    @Query("DELETE FROM monthly_incomes")
    suspend fun deleteAll()
}
