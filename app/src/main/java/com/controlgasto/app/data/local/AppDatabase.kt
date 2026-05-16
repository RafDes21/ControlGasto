package com.controlgasto.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase
import com.controlgasto.app.data.local.dao.CardClosingPeriodDao
import com.controlgasto.app.data.local.dao.CategoryDao
import com.controlgasto.app.data.local.dao.CreditCardDao
import com.controlgasto.app.data.local.dao.ExpenseDao
import com.controlgasto.app.data.local.entity.CardClosingPeriodEntity
import com.controlgasto.app.data.local.entity.CategoryEntity
import com.controlgasto.app.data.local.entity.CreditCardEntity
import com.controlgasto.app.data.local.entity.ExpenseEntity

@Database(
    entities = [ExpenseEntity::class, CategoryEntity::class, CreditCardEntity::class, CardClosingPeriodEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun cardClosingPeriodDao(): CardClosingPeriodDao

    companion object {
        const val DATABASE_NAME = "controlgasto_db"

        private val defaultCategories = listOf(
            Triple("cat_1", "Comida", "🍔") to Pair(0xFFF44336L, true),
            Triple("cat_2", "Transporte", "🚗") to Pair(0xFF2196F3L, true),
            Triple("cat_3", "Hogar", "🏠") to Pair(0xFF4CAF50L, true),
            Triple("cat_4", "Salud", "💊") to Pair(0xFFE91E63L, true),
            Triple("cat_5", "Entretenimiento", "🎮") to Pair(0xFF9C27B0L, true),
            Triple("cat_6", "Ropa", "👗") to Pair(0xFFFF9800L, true),
            Triple("cat_7", "Educación", "📚") to Pair(0xFF00BCD4L, true),
            Triple("cat_8", "Trabajo", "💼") to Pair(0xFF607D8BL, true),
            Triple("cat_9", "Otros", "📦") to Pair(0xFF795548L, true),
        )

        val prepopulateCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                insertDefaultCategories(db)
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                insertDefaultCategories(db)
            }

            private fun insertDefaultCategories(db: SupportSQLiteDatabase) {
                defaultCategories.forEach { (info, extra) ->
                    val (id, name, icon) = info
                    val (color, isDefault) = extra
                    db.execSQL(
                        "INSERT OR IGNORE INTO categories (id, name, icon, color, isDefault) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(id, name, icon, color, if (isDefault) 1 else 0)
                    )
                }
            }
        }

        fun getDefaultCategories(): List<CategoryEntity> = defaultCategories.map { (info, extra) ->
            val (id, name, icon) = info
            val (color, isDefault) = extra
            CategoryEntity(id, name, icon, color, isDefault)
        }

    }
}
