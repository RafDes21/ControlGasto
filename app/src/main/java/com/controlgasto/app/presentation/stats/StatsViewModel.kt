package com.controlgasto.app.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlgasto.app.domain.model.Category
import com.controlgasto.app.domain.repository.CategoryRepository
import com.controlgasto.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

data class CategoryStats(
    val category: Category,
    val amount: Double,
    val percentage: Float
)

data class MonthlyTotal(
    val label: String,
    val total: Double,
    val isCurrentMonth: Boolean,
    val isFuture: Boolean = false
)

data class StatsUiState(
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val currentMonthName: String = "",
    val totalSpent: Double = 0.0,
    val categoryStats: List<CategoryStats> = emptyList(),
    val monthlyTrend: List<MonthlyTotal> = emptyList(),
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val now = Calendar.getInstance()
    private val _selectedMonth = MutableStateFlow(now.get(Calendar.MONTH) + 1)
    private val _selectedYear = MutableStateFlow(now.get(Calendar.YEAR))

    private val maxFutureYearMonth: StateFlow<Pair<Int, Int>> = expenseRepository.getExpenses()
        .map { expenses ->
            val cal = Calendar.getInstance()
            val nowTotal = cal.get(Calendar.YEAR) * 12 + (cal.get(Calendar.MONTH) + 1)
            var maxTotal = nowTotal
            for (expense in expenses) {
                val start = Calendar.getInstance().apply { timeInMillis = expense.date }
                val startTotal = start.get(Calendar.YEAR) * 12 + (start.get(Calendar.MONTH) + 1)
                when {
                    expense.isInstallment -> {
                        val end = startTotal + expense.totalInstallments - 1
                        if (end > maxTotal) maxTotal = end
                    }
                    expense.isRecurring && expense.durationMonths > 0 -> {
                        val end = startTotal + expense.durationMonths - 1
                        if (end > maxTotal) maxTotal = end
                    }
                }
            }
            Pair((maxTotal - 1) / 12, (maxTotal - 1) % 12 + 1)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), run {
            val c = Calendar.getInstance()
            Pair(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        })

    val uiState: StateFlow<StatsUiState> = combine(
        combine(_selectedMonth, _selectedYear) { m, y -> m to y },
        maxFutureYearMonth
    ) { monthYear, maxFuture -> monthYear to maxFuture }
    .flatMapLatest { (monthYear, maxFuture) ->
        val (month, year) = monthYear
        val (maxFutureYear, maxFutureMonth) = maxFuture
        val yearStr = String.format("%04d", year)
        val monthStr = String.format("%02d", month)
        val canGoNext = (year * 12 + month) < (maxFutureYear * 12 + maxFutureMonth)

        // Rango dinámico: 3 meses atrás + actual + meses futuros hasta maxFuture
        val cal = Calendar.getInstance()
        val nowTotal = cal.get(Calendar.YEAR) * 12 + (cal.get(Calendar.MONTH) + 1)
        val maxTotal = maxFutureYear * 12 + maxFutureMonth
        val startTotal = nowTotal - 3
        val nowYearStr = String.format("%04d", cal.get(Calendar.YEAR))
        val nowMonthStr = String.format("%02d", cal.get(Calendar.MONTH) + 1)

        val trendMonths = (startTotal..maxTotal).map { total ->
            val ty = (total - 1) / 12
            val tm = (total - 1) % 12 + 1
            Triple(String.format("%04d", ty), String.format("%02d", tm), getMonthShortName(tm))
        }

        val trendFlows = trendMonths.map { (ty, tm, label) ->
            val monthTotal = ty.toInt() * 12 + tm.toInt()
            expenseRepository.getExpensesByMonth(ty, tm).map { expenses ->
                MonthlyTotal(
                    label = label,
                    total = expenses.sumOf { it.installmentAmount },
                    isCurrentMonth = ty == nowYearStr && tm == nowMonthStr,
                    isFuture = monthTotal > nowTotal
                )
            }
        }

        val dynamicTrendFlow: Flow<List<MonthlyTotal>> =
            if (trendFlows.isEmpty()) flowOf(emptyList())
            else combine(trendFlows) { it.toList() }

        combine(
            expenseRepository.getExpensesByMonth(yearStr, monthStr),
            categoryRepository.getCategories(),
            dynamicTrendFlow
        ) { expenses, categories, trend ->
            val catMap = categories.associateBy { it.id }
            val grouped = expenses
                .groupBy { catMap[it.categoryId] }
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { entry -> entry.value.sumOf { it.installmentAmount } }
                .toList()
                .sortedByDescending { it.second }

            val total = grouped.sumOf { it.second }

            val catStats = grouped.map { (cat, amount) ->
                CategoryStats(
                    category = cat,
                    amount = amount,
                    percentage = if (total > 0) (amount / total * 100).toFloat() else 0f
                )
            }

            StatsUiState(
                selectedMonth = month,
                selectedYear = year,
                currentMonthName = getMonthName(month),
                totalSpent = total,
                categoryStats = catStats,
                monthlyTrend = trend,
                canGoNext = canGoNext,
                isLoading = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun previousMonth() {
        val m = _selectedMonth.value
        val y = _selectedYear.value
        if (m == 1) { _selectedMonth.value = 12; _selectedYear.value = y - 1 }
        else _selectedMonth.value = m - 1
    }

    fun nextMonth() {
        if (!uiState.value.canGoNext) return
        val m = _selectedMonth.value
        val y = _selectedYear.value
        if (m == 12) { _selectedMonth.value = 1; _selectedYear.value = y + 1 }
        else _selectedMonth.value = m + 1
    }

    private fun getMonthName(month: Int) = when (month) {
        1 -> "Enero"; 2 -> "Febrero"; 3 -> "Marzo"; 4 -> "Abril"
        5 -> "Mayo"; 6 -> "Junio"; 7 -> "Julio"; 8 -> "Agosto"
        9 -> "Septiembre"; 10 -> "Octubre"; 11 -> "Noviembre"; else -> "Diciembre"
    }

    private fun getMonthShortName(month: Int) = when (month) {
        1 -> "Ene"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Abr"
        5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Ago"
        9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dic"
    }
}
