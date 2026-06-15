package com.controlgasto.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlgasto.app.core.UserPreferences
import com.controlgasto.app.domain.model.Category
import com.controlgasto.app.domain.model.Expense
import com.controlgasto.app.domain.model.MonthlyIncome
import com.controlgasto.app.domain.repository.AuthRepository
import com.controlgasto.app.domain.repository.CardClosingPeriodRepository
import com.controlgasto.app.domain.repository.CategoryRepository
import com.controlgasto.app.domain.repository.CreditCardRepository
import com.controlgasto.app.domain.repository.ExpenseRepository
import com.controlgasto.app.domain.repository.MonthlyIncomeRepository
import com.controlgasto.app.presentation.expense.CardExpenseGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val monthlyTotal: Double = 0.0,
    val cardGroups: List<CardExpenseGroup> = emptyList(),
    val recentExpenses: List<Expense> = emptyList(),
    val categoryTotals: Map<Category, Double> = emptyMap(),
    val isProMode: Boolean = false,
    val aiRequestsLeft: Int = UserPreferences.FREE_AI_LIMIT,
    val currentMonth: String = "",
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val monthlyIncome: MonthlyIncome? = null
) {
    val cardTotal: Double get() = cardGroups.sumOf { it.total }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val creditCardRepository: CreditCardRepository,
    private val cardClosingPeriodRepository: CardClosingPeriodRepository,
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val incomeRepository: MonthlyIncomeRepository
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
                    expense.isInstallment && expense.cardId != null -> {
                        val bp = expense.billingPeriod
                        if (bp != null) {
                            val parts = bp.split("-")
                            val bpYear = parts[0].toIntOrNull()
                            val bpMonth = parts[1].toIntOrNull()
                            if (bpYear != null && bpMonth != null) {
                                val end = bpYear * 12 + bpMonth + expense.totalInstallments
                                if (end > maxTotal) maxTotal = end
                            }
                        }
                    }
                    expense.isInstallment -> {
                        val end = startTotal + expense.totalInstallments - 1
                        if (end > maxTotal) maxTotal = end
                    }
                    expense.isRecurring && expense.durationMonths > 0 -> {
                        val end = startTotal + expense.durationMonths - 1
                        if (end > maxTotal) maxTotal = end
                    }
                }
                expense.billingPeriod?.let { bp ->
                    val parts = bp.split("-")
                    val bpYear = parts[0].toIntOrNull() ?: return@let
                    val bpMonth = parts[1].toIntOrNull() ?: return@let
                    val dueTotal = bpYear * 12 + bpMonth + 1
                    if (dueTotal > maxTotal) maxTotal = dueTotal
                }
            }
            Pair((maxTotal - 1) / 12, (maxTotal - 1) % 12 + 1)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, run {
            val c = Calendar.getInstance()
            Pair(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        })

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            combine(_selectedMonth, _selectedYear, userPreferences.isProMode, userPreferences.aiRequestsCount) { m, y, isPro, ai ->
                Triple(Pair(m, y), isPro, ai)
            },
            authRepository.currentUser
        ) { (monthYear, isPro, aiCount), currentUser ->
            Triple(monthYear, isPro to aiCount, currentUser != null)
        },
        maxFutureYearMonth
    ) { params, maxFuture -> params to maxFuture }
    .flatMapLatest { (params, maxFuture) ->
        val (monthYear, proAi, isLoggedIn) = params
        val (month, year) = monthYear
        val (isPro, aiCount) = proAi
        val (maxFutureYear, maxFutureMonth) = maxFuture
        val yearStr = String.format("%04d", year)
        val monthStr = String.format("%02d", month)
        val monthKey = "$yearStr-$monthStr"
        val monthStart = monthStartMillis(year, month)
        val monthEnd = monthEndMillis(year, month)
        combine(
            combine(
                expenseRepository.getExpensesByMonth(yearStr, monthStr),
                expenseRepository.getCreditExpensesByDueMonth(monthStart, monthEnd),
                cardClosingPeriodRepository.getPeriodsForDueDateInMonthFlow(monthStart, monthEnd),
                creditCardRepository.getCreditCards(),
                categoryRepository.getCategories()
            ) { allMonth, creditExpenses, periods, cards, categories ->
                val cashExpenses = allMonth.filter { it.cardId == null }
                val cardGroups = creditExpenses
                    .groupBy { it.cardId!! }
                    .mapNotNull { (cardId, expenses) ->
                        val card = cards.find { it.id == cardId } ?: return@mapNotNull null
                        val period = periods.find { it.cardId == cardId } ?: return@mapNotNull null
                        CardExpenseGroup(card, period, expenses)
                    }
                    .sortedBy { it.period.dueDate }
                buildUiState(cashExpenses, cardGroups, categories, isPro, aiCount, month, year, isLoggedIn, maxFutureYear, maxFutureMonth)
            },
            incomeRepository.getIncomeByMonth(monthKey)
        ) { uiState, income ->
            uiState.copy(monthlyIncome = income)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private fun buildUiState(
        cashExpenses: List<Expense>,
        cardGroups: List<CardExpenseGroup>,
        categories: List<Category>,
        isPro: Boolean,
        aiCount: Int,
        month: Int,
        year: Int,
        isLoggedIn: Boolean = false,
        maxFutureYear: Int = Calendar.getInstance().get(Calendar.YEAR),
        maxFutureMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1
    ): HomeUiState {
        val catMap = categories.associateBy { it.id }
        val totals = cashExpenses.groupBy { catMap[it.categoryId] }
            .filterKeys { it != null }
            .mapKeys { it.key!! }
            .mapValues { entry -> entry.value.sumOf { it.installmentAmount } }

        val monthlyTotal = cashExpenses.sumOf { it.installmentAmount }
        val canGoNext = (year * 12 + month) < (maxFutureYear * 12 + maxFutureMonth)

        return HomeUiState(
            monthlyTotal = monthlyTotal,
            cardGroups = cardGroups,
            recentExpenses = cashExpenses.take(5),
            categoryTotals = totals,
            isProMode = isPro,
            aiRequestsLeft = if (isPro) -1 else UserPreferences.FREE_AI_LIMIT - aiCount,
            currentMonth = getMonthName(month),
            currentYear = year,
            selectedMonth = month,
            selectedYear = year,
            canGoNext = canGoNext,
            isLoading = false,
            isLoggedIn = isLoggedIn
        )
    }

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

    fun toggleIncomeVisibility() {
        val income = uiState.value.monthlyIncome ?: return
        viewModelScope.launch {
            incomeRepository.saveIncome(income.copy(isHidden = !income.isHidden))
        }
    }

    fun useAiRequest(onAllowed: () -> Unit, onBlocked: () -> Unit) {
        viewModelScope.launch {
            userPreferences.resetAiRequestsIfNeeded()
            val state = uiState.value
            if (state.isProMode || state.aiRequestsLeft > 0) {
                if (!state.isProMode) userPreferences.incrementAiRequests()
                onAllowed()
            } else {
                onBlocked()
            }
        }
    }

    private fun getMonthName(month: Int) = when (month) {
        1 -> "Enero"; 2 -> "Febrero"; 3 -> "Marzo"; 4 -> "Abril"
        5 -> "Mayo"; 6 -> "Junio"; 7 -> "Julio"; 8 -> "Agosto"
        9 -> "Septiembre"; 10 -> "Octubre"; 11 -> "Noviembre"; else -> "Diciembre"
    }

    private fun monthStartMillis(year: Int, month: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun monthEndMillis(year: Int, month: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
}
