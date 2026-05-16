package com.controlgasto.app.data.repository

import com.controlgasto.app.data.local.dao.CardClosingPeriodDao
import com.controlgasto.app.data.local.entity.toDomain
import com.controlgasto.app.data.local.entity.toEntity
import com.controlgasto.app.data.util.BillingPeriodHelper
import com.controlgasto.app.domain.model.CardClosingPeriod
import com.controlgasto.app.domain.repository.CardClosingPeriodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardClosingPeriodRepositoryImpl @Inject constructor(
    private val dao: CardClosingPeriodDao
) : CardClosingPeriodRepository {
    override fun getPeriodsForCard(cardId: String): Flow<List<CardClosingPeriod>> =
        dao.getByCardId(cardId).map { it.map { e -> e.toDomain() } }

    override fun getPeriodsForDueDateInMonthFlow(monthStart: Long, monthEnd: Long): Flow<List<CardClosingPeriod>> =
        dao.getPeriodsForDueDateRangeFlow(monthStart, monthEnd).map { it.map { e -> e.toDomain() } }

    override suspend fun getPeriodForCardAndMonth(cardId: String, month: String): CardClosingPeriod? =
        dao.getByCardIdAndMonth(cardId, month)?.toDomain()

    override suspend fun insertAll(periods: List<CardClosingPeriod>) =
        dao.insertAll(periods.map { it.toEntity() })

    override suspend fun update(period: CardClosingPeriod) =
        dao.update(period.toEntity())

    override suspend fun updateFromMonth(cardId: String, fromMonth: String, newClosingDay: Int) {
        val periods = dao.getFromMonth(cardId, fromMonth)
        periods.forEach { p ->
            val updated = BillingPeriodHelper.recalculatePeriod(p.toDomain(), newClosingDay)
            dao.update(updated.toEntity())
        }
    }

    override suspend fun deleteByCardId(cardId: String) = dao.deleteByCardId(cardId)
}
