package com.controlgasto.app.data.local.dao

import androidx.room.*
import com.controlgasto.app.data.local.entity.CreditCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardDao {

    @Query("SELECT * FROM credit_cards ORDER BY name ASC")
    fun getAll(): Flow<List<CreditCardEntity>>

    @Query("SELECT * FROM credit_cards ORDER BY name ASC")
    suspend fun getAllOnce(): List<CreditCardEntity>

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    suspend fun getById(id: String): CreditCardEntity?

    @Query("SELECT COUNT(*) FROM credit_cards")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CreditCardEntity)

    @Delete
    suspend fun delete(card: CreditCardEntity)
}
