package com.controlgasto.app.data.util

import com.controlgasto.app.domain.model.CardClosingPeriod
import java.util.Calendar
import java.util.UUID

object BillingPeriodHelper {

    fun formatMonth(year: Int, month: Int): String = "%04d-%02d".format(year, month)

    fun currentMonth(): String {
        val cal = Calendar.getInstance()
        return formatMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun monthFromMillis(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return formatMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun monthLabel(month: String): String {
        val parts = month.split("-")
        val year = parts[0].toInt()
        val m = parts[1].toInt()
        val names = arrayOf("Enero","Febrero","Marzo","Abril","Mayo","Junio",
            "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")
        return "${names[m - 1]} $year"
    }

    fun formatDateShort(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val months = arrayOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")
        return "$day ${months[cal.get(Calendar.MONTH)]}"
    }

    fun formatDateFull(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val months = arrayOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")
        return "$day ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }

    fun generatePeriodsForYear(cardId: String, closingDay: Int, dueDay: Int, year: Int): List<CardClosingPeriod> =
        (1..12).map { month -> buildPeriod(cardId, year, month, closingDay, dueDay) }

    fun generatePeriodsFromMonth(cardId: String, closingDay: Int, dueDay: Int, fromYear: Int, fromMonth: Int): List<CardClosingPeriod> =
        (0 until 12).map { offset ->
            val totalMonths = (fromMonth - 1) + offset
            val year = fromYear + totalMonths / 12
            val month = totalMonths % 12 + 1
            buildPeriod(cardId, year, month, closingDay, dueDay)
        }

    fun buildPeriod(cardId: String, year: Int, month: Int, closingDay: Int, dueDay: Int): CardClosingPeriod {
        val safeClosingDay = closingDay.coerceIn(1, 28)
        val safeDueDay = dueDay.coerceIn(1, 28)

        val periodEnd = Calendar.getInstance().apply {
            set(year, month - 1, safeClosingDay, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val prevMonth = if (month == 1) 12 else month - 1
        val prevYear = if (month == 1) year - 1 else year
        val periodStart = Calendar.getInstance().apply {
            set(prevYear, prevMonth - 1, safeClosingDay, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        val nextMonth = if (month == 12) 1 else month + 1
        val nextYear = if (month == 12) year + 1 else year
        val dueDate = Calendar.getInstance().apply {
            set(nextYear, nextMonth - 1, safeDueDay, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return CardClosingPeriod(
            id = UUID.randomUUID().toString(),
            cardId = cardId,
            month = formatMonth(year, month),
            closingDay = safeClosingDay,
            dueDate = dueDate,
            periodStart = periodStart,
            periodEnd = periodEnd
        )
    }

    fun recalculatePeriod(existing: CardClosingPeriod, newClosingDay: Int): CardClosingPeriod {
        val parts = existing.month.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val dueDayCal = Calendar.getInstance().apply { timeInMillis = existing.dueDate }
        val dueDay = dueDayCal.get(Calendar.DAY_OF_MONTH)
        return buildPeriod(existing.cardId, year, month, newClosingDay, dueDay)
            .copy(id = existing.id, createdAt = existing.createdAt)
    }

    fun calculateBillingPeriod(expenseDateMillis: Long, period: CardClosingPeriod): String {
        return if (expenseDateMillis <= period.periodEnd) {
            period.month
        } else {
            val parts = period.month.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            if (month == 12) formatMonth(year + 1, 1) else formatMonth(year, month + 1)
        }
    }

    fun isDueSoon(dueDateMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        val fiveDaysMillis = 5L * 24 * 60 * 60 * 1000
        return dueDateMillis > now && dueDateMillis - now <= fiveDaysMillis
    }
}
