package com.controlgasto.app.presentation.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlgasto.app.domain.model.MonthlyIncome
import com.controlgasto.app.domain.repository.MonthlyIncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class IncomesUiState(
    val incomes: List<MonthlyIncome> = emptyList(),
    val isLoading: Boolean = true,
    val showSheet: Boolean = false,
    val editingIncome: MonthlyIncome? = null,
    val sheetAmount: String = "",
    val sheetDescription: String = "",
    val sheetMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val sheetYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val sheetIsHidden: Boolean = false,
    val showMonthPicker: Boolean = false
)

@HiltViewModel
class IncomesViewModel @Inject constructor(
    private val repository: MonthlyIncomeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(IncomesUiState())
    val state: StateFlow<IncomesUiState> = _state

    init {
        viewModelScope.launch {
            repository.getAllIncomes().collect { incomes ->
                _state.update { it.copy(incomes = incomes, isLoading = false) }
            }
        }
    }

    fun openAddSheet(income: MonthlyIncome? = null) {
        val now = Calendar.getInstance()
        _state.update {
            it.copy(
                showSheet = true,
                editingIncome = income,
                sheetAmount = income?.amount?.let { a -> a.toLong().toString() } ?: "",
                sheetDescription = income?.description ?: "",
                sheetMonth = income?.month?.split("-")?.getOrNull(1)?.toIntOrNull()
                    ?: (now.get(Calendar.MONTH) + 1),
                sheetYear = income?.month?.split("-")?.getOrNull(0)?.toIntOrNull()
                    ?: now.get(Calendar.YEAR),
                sheetIsHidden = income?.isHidden ?: false
            )
        }
    }

    fun closeSheet() {
        _state.update { it.copy(showSheet = false, editingIncome = null, showMonthPicker = false) }
    }

    fun updateAmount(v: String) = _state.update { it.copy(sheetAmount = v) }
    fun updateDescription(v: String) = _state.update { it.copy(sheetDescription = v) }
    fun updateMonth(m: Int) = _state.update { it.copy(sheetMonth = m, showMonthPicker = false) }
    fun updateYear(y: Int) = _state.update { it.copy(sheetYear = y) }
    fun updateIsHidden(v: Boolean) = _state.update { it.copy(sheetIsHidden = v) }
    fun showMonthPicker() = _state.update { it.copy(showMonthPicker = true) }
    fun hideMonthPicker() = _state.update { it.copy(showMonthPicker = false) }

    fun saveIncome() {
        val s = _state.value
        val amount = s.sheetAmount.replace(",", ".").toDoubleOrNull() ?: return
        val monthStr = "%04d-%02d".format(s.sheetYear, s.sheetMonth)
        val income = MonthlyIncome(
            id = monthStr,
            month = monthStr,
            amount = amount,
            description = s.sheetDescription.trim(),
            isHidden = s.sheetIsHidden
        )
        viewModelScope.launch {
            repository.saveIncome(income)
            closeSheet()
        }
    }

    fun deleteIncome(income: MonthlyIncome) {
        viewModelScope.launch { repository.deleteIncome(income) }
    }
}
