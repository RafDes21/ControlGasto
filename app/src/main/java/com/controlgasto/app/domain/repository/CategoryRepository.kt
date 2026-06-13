package com.controlgasto.app.domain.repository

import com.controlgasto.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getCategories(): Flow<List<Category>>
    suspend fun addCategory(category: Category)
    suspend fun deleteCategory(category: Category)
    suspend fun downgradeCategories()
    suspend fun syncToRoomOnLogout()
}
