package com.controlgasto.app.core.di

import com.controlgasto.app.data.repository.AuthRepositoryImpl
import com.controlgasto.app.data.repository.CardClosingPeriodRepositoryImpl
import com.controlgasto.app.data.repository.CategoryRepositoryImpl
import com.controlgasto.app.data.repository.CreditCardRepositoryImpl
import com.controlgasto.app.data.repository.ExpenseRepositoryImpl
import com.controlgasto.app.data.repository.MonthlyIncomeRepositoryImpl
import com.controlgasto.app.domain.repository.AuthRepository
import com.controlgasto.app.domain.repository.CardClosingPeriodRepository
import com.controlgasto.app.domain.repository.CategoryRepository
import com.controlgasto.app.domain.repository.CreditCardRepository
import com.controlgasto.app.domain.repository.ExpenseRepository
import com.controlgasto.app.domain.repository.MonthlyIncomeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindCreditCardRepository(impl: CreditCardRepositoryImpl): CreditCardRepository

    @Binds @Singleton
    abstract fun bindCardClosingPeriodRepository(impl: CardClosingPeriodRepositoryImpl): CardClosingPeriodRepository

    @Binds @Singleton
    abstract fun bindMonthlyIncomeRepository(impl: MonthlyIncomeRepositoryImpl): MonthlyIncomeRepository
}
