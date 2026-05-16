package com.controlgasto.app.presentation.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlgasto.app.core.UserPreferences
import com.controlgasto.app.data.util.BillingPeriodHelper
import com.controlgasto.app.domain.model.CardClosingPeriod
import com.controlgasto.app.domain.model.Category
import com.controlgasto.app.domain.model.CreditCard
import com.controlgasto.app.domain.model.Expense
import com.controlgasto.app.domain.repository.CardClosingPeriodRepository
import com.controlgasto.app.domain.repository.CategoryRepository
import com.controlgasto.app.domain.repository.CreditCardRepository
import com.controlgasto.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CardExpenseGroup(
    val card: CreditCard,
    val period: CardClosingPeriod,
    val expenses: List<Expense>
) {
    val total: Double get() = expenses.sumOf { it.installmentAmount }
}

data class BillingPeriodInfo(
    val period: CardClosingPeriod,
    val billingMonth: String,
    val dueDateMillis: Long
)

data class ExpenseListState(
    val cardGroups: List<CardExpenseGroup> = emptyList(),
    val cashExpenses: List<Expense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isProMode: Boolean = false,
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val maxFutureYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val maxFutureMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = cardGroups.isEmpty() && cashExpenses.isEmpty()
    val totalAmount: Double get() = cardGroups.sumOf { it.total } + cashExpenses.sumOf { it.installmentAmount }
    val currentMonthLabel: String get() {
        val name = when (selectedMonth) {
            1 -> "Enero"; 2 -> "Febrero"; 3 -> "Marzo"; 4 -> "Abril"
            5 -> "Mayo"; 6 -> "Junio"; 7 -> "Julio"; 8 -> "Agosto"
            9 -> "Septiembre"; 10 -> "Octubre"; 11 -> "Noviembre"; else -> "Diciembre"
        }
        return "$name $selectedYear"
    }
    val canGoNext: Boolean get() {
        val selectedTotal = selectedYear * 12 + selectedMonth
        val maxTotal = maxFutureYear * 12 + maxFutureMonth
        return selectedTotal < maxTotal
    }
}

data class AddExpenseState(
    val title: String = "",
    val amount: String = "",
    val categoryId: String = "",
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val cardId: String? = null,
    val hasInstallments: Boolean = false,
    val installmentCount: String = "3",
    val installmentDueDay: String = "",
    val isRecurring: Boolean = false,
    val durationMonths: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val billingInfo: BillingPeriodInfo? = null,
    val noPeriodForMonth: Boolean = false
)

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val creditCardRepository: CreditCardRepository,
    private val cardClosingPeriodRepository: CardClosingPeriodRepository,
    private val userPreferences: UserPreferences
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
                // Credit card expenses: payment is due in the month after the billing period
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

    val listState: StateFlow<ExpenseListState> = combine(
        combine(_selectedMonth, _selectedYear, userPreferences.isProMode) { m, y, p -> Triple(m, y, p) },
        maxFutureYearMonth
    ) { triple, maxFuture -> Pair(triple, maxFuture) }
        .flatMapLatest { (triple, maxFuture) ->
            val (month, year, isPro) = triple
            val (maxYear, maxMonth) = maxFuture
            val yearStr = "%04d".format(year)
            val monthStr = "%02d".format(month)
            val monthStart = monthStartMillis(year, month)
            val monthEnd = monthEndMillis(year, month)

            combine(
                expenseRepository.getExpensesByMonth(yearStr, monthStr),
                expenseRepository.getCreditExpensesByDueMonth(monthStart, monthEnd),
                cardClosingPeriodRepository.getPeriodsForDueDateInMonthFlow(monthStart, monthEnd),
                creditCardRepository.getCreditCards(),
                categoryRepository.getCategories()
            ) { allMonth, creditExpenses, periods, cards, cats ->
                val cashExpenses = allMonth.filter { it.cardId == null }
                val cardGroups = creditExpenses
                    .groupBy { it.cardId!! }
                    .mapNotNull { (cardId, expenses) ->
                        val card = cards.find { it.id == cardId } ?: return@mapNotNull null
                        val period = periods.find { it.cardId == cardId } ?: return@mapNotNull null
                        CardExpenseGroup(card, period, expenses)
                    }
                    .sortedBy { it.period.dueDate }
                ExpenseListState(
                    cardGroups = cardGroups,
                    cashExpenses = cashExpenses,
                    categories = cats,
                    isProMode = isPro,
                    selectedMonth = month,
                    selectedYear = year,
                    maxFutureYear = maxYear,
                    maxFutureMonth = maxMonth,
                    isLoading = false
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpenseListState())

    val availableCards: StateFlow<List<CreditCard>> = creditCardRepository.getCreditCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _addState = MutableStateFlow(AddExpenseState())
    val addState = _addState.asStateFlow()

    fun previousMonth() {
        val m = _selectedMonth.value; val y = _selectedYear.value
        if (m == 1) { _selectedMonth.value = 12; _selectedYear.value = y - 1 }
        else _selectedMonth.value = m - 1
    }

    fun nextMonth() {
        val state = listState.value
        if (!state.canGoNext) return
        val m = _selectedMonth.value; val y = _selectedYear.value
        if (m == 12) { _selectedMonth.value = 1; _selectedYear.value = y + 1 }
        else _selectedMonth.value = m + 1
    }

    fun loadExpense(id: String) {
        viewModelScope.launch {
            expenseRepository.getExpenseById(id)?.let { e ->
                _addState.value = AddExpenseState(
                    title = e.title,
                    amount = e.amount.toString(),
                    categoryId = e.categoryId,
                    note = e.note,
                    date = e.date,
                    cardId = e.cardId,
                    hasInstallments = e.isInstallment,
                    installmentCount = e.totalInstallments.toString(),
                    installmentDueDay = if (e.installmentDueDay > 0) e.installmentDueDay.toString() else "",
                    isRecurring = e.isRecurring,
                    durationMonths = if (e.durationMonths > 0) e.durationMonths.toString() else ""
                )
            }
        }
    }

    fun onTitleChange(v: String) { _addState.value = _addState.value.copy(title = v) }
    fun onAmountChange(v: String) { _addState.value = _addState.value.copy(amount = v) }
    fun onCategoryChange(v: String) { _addState.value = _addState.value.copy(categoryId = v) }
    fun onNoteChange(v: String) { _addState.value = _addState.value.copy(note = v) }
    fun onDateChange(v: Long) {
        _addState.value = _addState.value.copy(date = v)
        refreshBillingInfo()
    }
    fun onDurationMonthsChange(v: String) { _addState.value = _addState.value.copy(durationMonths = v) }
    fun onCardChange(v: String?) {
        _addState.value = _addState.value.copy(
            cardId = v,
            hasInstallments = if (v == null) false else _addState.value.hasInstallments,
            date = if (v != null) System.currentTimeMillis() else _addState.value.date
        )
        refreshBillingInfo()
    }
    fun onHasInstallmentsChange(v: Boolean) {
        _addState.value = _addState.value.copy(
            hasInstallments = v,
            isRecurring = if (v) false else _addState.value.isRecurring
        )
    }
    fun onInstallmentCountChange(v: String) { _addState.value = _addState.value.copy(installmentCount = v) }
    fun onInstallmentDueDayChange(v: String) { _addState.value = _addState.value.copy(installmentDueDay = v) }
    fun onIsRecurringChange(v: Boolean) {
        _addState.value = _addState.value.copy(
            isRecurring = v,
            hasInstallments = if (v) false else _addState.value.hasInstallments,
            durationMonths = if (!v) "" else _addState.value.durationMonths
        )
    }

    private fun refreshBillingInfo() {
        val s = _addState.value
        val cardId = s.cardId
        if (cardId == null) {
            _addState.value = s.copy(billingInfo = null, noPeriodForMonth = false)
            return
        }
        viewModelScope.launch {
            val month = BillingPeriodHelper.monthFromMillis(s.date)
            val period = cardClosingPeriodRepository.getPeriodForCardAndMonth(cardId, month)
            if (period == null) {
                _addState.value = _addState.value.copy(billingInfo = null, noPeriodForMonth = true)
                return@launch
            }
            val billingMonth = BillingPeriodHelper.calculateBillingPeriod(s.date, period)
            val duePeriod = if (billingMonth == period.month) period
                           else cardClosingPeriodRepository.getPeriodForCardAndMonth(cardId, billingMonth)
            val dueDate = duePeriod?.dueDate ?: period.dueDate
            _addState.value = _addState.value.copy(
                billingInfo = BillingPeriodInfo(period, billingMonth, dueDate),
                noPeriodForMonth = false
            )
        }
    }

    fun saveExpense(existingId: String? = null) {
        val s = _addState.value
        if (s.title.isBlank()) { _addState.value = s.copy(error = "El título es obligatorio"); return }
        val amount = s.amount.replace(",", ".").toDoubleOrNull()
        if (amount == null || amount <= 0) { _addState.value = s.copy(error = "Ingresá un monto válido"); return }
        if (s.categoryId.isBlank()) { _addState.value = s.copy(error = "Seleccioná una categoría"); return }
        val totalInstallments = if (s.hasInstallments && s.cardId != null && !s.isRecurring) {
            s.installmentCount.toIntOrNull()?.takeIf { it >= 2 } ?: run {
                _addState.value = s.copy(error = "Cantidad de cuotas inválida (mínimo 2)")
                return
            }
        } else 1
        val durationMonths = if (s.isRecurring) s.durationMonths.toIntOrNull()?.takeIf { it > 0 } ?: 0 else 0

        viewModelScope.launch {
            _addState.value = s.copy(isLoading = true)
            val expenseMonth = BillingPeriodHelper.monthFromMillis(s.date)
            var billingPeriod: String? = null

            if (s.cardId != null) {
                val period = cardClosingPeriodRepository.getPeriodForCardAndMonth(s.cardId, expenseMonth)
                if (period == null) {
                    _addState.value = _addState.value.copy(
                        isLoading = false,
                        noPeriodForMonth = true,
                        error = "Sin cierre configurado para este mes. Configurá el período en Tarjetas."
                    )
                    return@launch
                }
                billingPeriod = BillingPeriodHelper.calculateBillingPeriod(s.date, period)
            }

            val expense = Expense(
                id = existingId ?: java.util.UUID.randomUUID().toString(),
                title = s.title.trim(),
                amount = amount,
                categoryId = s.categoryId,
                date = s.date,
                note = s.note.trim(),
                cardId = s.cardId,
                totalInstallments = totalInstallments,
                installmentDueDay = s.installmentDueDay.toIntOrNull() ?: 0,
                isRecurring = s.isRecurring,
                durationMonths = durationMonths,
                expenseMonth = expenseMonth,
                billingPeriod = billingPeriod
            )
            if (existingId != null) expenseRepository.updateExpense(expense)
            else expenseRepository.addExpense(expense)
            _addState.value = _addState.value.copy(isSaved = true, isLoading = false)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { expenseRepository.deleteExpense(expense) }
    }

    fun deleteAndNavigateBack(expenseId: String) {
        viewModelScope.launch {
            expenseRepository.getExpenseById(expenseId)?.let { expenseRepository.deleteExpense(it) }
            _addState.value = _addState.value.copy(isSaved = true)
        }
    }

    fun resetAddState() { _addState.value = AddExpenseState() }

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
