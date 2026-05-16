package com.controlgasto.app.core.di

import android.content.Context
import androidx.room.Room
import com.controlgasto.app.data.local.AppDatabase
import com.controlgasto.app.data.local.dao.CardClosingPeriodDao
import com.controlgasto.app.data.local.dao.CategoryDao
import com.controlgasto.app.data.local.dao.CreditCardDao
import com.controlgasto.app.data.local.dao.ExpenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addCallback(AppDatabase.prepopulateCallback)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideCreditCardDao(db: AppDatabase): CreditCardDao = db.creditCardDao()

    @Provides
    fun provideCardClosingPeriodDao(db: AppDatabase): CardClosingPeriodDao = db.cardClosingPeriodDao()
}
