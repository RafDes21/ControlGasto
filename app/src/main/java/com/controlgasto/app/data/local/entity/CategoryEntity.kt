package com.controlgasto.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.controlgasto.app.domain.model.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val isDefault: Boolean
)

fun CategoryEntity.toDomain() = Category(id, name, icon, color, isDefault)
fun Category.toEntity() = CategoryEntity(id, name, icon, color, isDefault)
