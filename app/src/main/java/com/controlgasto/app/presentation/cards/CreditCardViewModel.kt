package com.controlgasto.app.presentation.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlgasto.app.core.UserPreferences
import com.controlgasto.app.data.util.BillingPeriodHelper
import com.controlgasto.app.domain.model.CardClosingPeriod
import com.controlgasto.app.domain.model.CreditCard
import com.controlgasto.app.domain.repository.CardClosingPeriodRepository
import com.controlgasto.app.domain.repository.CreditCardRepository
import com.controlgasto.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class CreditCardUiState(
    val cards: List<CreditCard> = emptyList(),
    val isProMode: Boolean = false,
    val canAddCard: Boolean = true,
    val isLoading: Boolean = true,
    val showDowngradeDialog: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CreditCardViewModel @Inject constructor(
    private val creditCardRepository: CreditCardRepository,
    private val expenseRepository: ExpenseRepository,
    private val cardClosingPeriodRepository: CardClosingPeriodRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<CreditCardUiState> = combine(
        creditCardRepository.getCreditCards(),
        userPreferences.isProMode
    ) { cards, isPro ->
        CreditCardUiState(cards = cards, isProMode = isPro, canAddCard = true, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CreditCardUiState())

    // Cacheo de flows por cardId para evitar que collectAsState reinicie en cada recomposición
    private val periodsFlowCache = mutableMapOf<String, Flow<List<CardClosingPeriod>>>()

    fun periodsForCard(cardId: String): Flow<List<CardClosingPeriod>> =
        periodsFlowCache.getOrPut(cardId) {
            cardClosingPeriodRepository.getPeriodsForCard(cardId)
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
        }

    fun addCard(name: String, lastFour: String, closingDateMillis: Long, dueDateMillis: Long, colorHex: Long) {
        if (!uiState.value.canAddCard) return
        if (name.isBlank()) return
        val closingCal = Calendar.getInstance().apply { timeInMillis = closingDateMillis }
        val dueCal = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
        val card = CreditCard(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            lastFourDigits = lastFour.trim(),
            closingDay = closingCal.get(Calendar.DAY_OF_MONTH),
            dueDay = dueCal.get(Calendar.DAY_OF_MONTH),
            colorHex = colorHex
        )
        viewModelScope.launch {
            val periods = BillingPeriodHelper.generatePeriodsFromDates(card.id, closingDateMillis, dueDateMillis)
            cardClosingPeriodRepository.insertAll(periods)
            creditCardRepository.addCreditCard(card)
        }
    }

    fun updateCard(card: CreditCard, name: String, lastFour: String, closingDateMillis: Long, dueDateMillis: Long, colorHex: Long, currentPeriod: CardClosingPeriod?) {
        if (name.isBlank()) return
        val closingCal = Calendar.getInstance().apply { timeInMillis = closingDateMillis }
        val dueCal = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
        val newClosingDay = closingCal.get(Calendar.DAY_OF_MONTH)
        val newDueDay = dueCal.get(Calendar.DAY_OF_MONTH)
        viewModelScope.launch {
            creditCardRepository.updateCreditCard(card.copy(
                name = name.trim(),
                lastFourDigits = lastFour.trim(),
                closingDay = newClosingDay,
                dueDay = newDueDay,
                colorHex = colorHex
            ))
            if (currentPeriod != null) {
                val updated = BillingPeriodHelper.buildPeriodFromExactDates(currentPeriod, closingDateMillis, dueDateMillis)
                cardClosingPeriodRepository.update(updated)
            }
        }
    }

    fun deleteCard(card: CreditCard) {
        periodsFlowCache.remove(card.id)
        viewModelScope.launch {
            cardClosingPeriodRepository.deleteByCardId(card.id)
            expenseRepository.deleteExpensesByCardId(card.id)
            creditCardRepository.deleteCreditCard(card)
        }
    }

    fun keepOnlyCard(cardToKeep: CreditCard) {
        viewModelScope.launch {
            val others = uiState.value.cards.filter { it.id != cardToKeep.id }
            others.forEach { card ->
                periodsFlowCache.remove(card.id)
                cardClosingPeriodRepository.deleteByCardId(card.id)
                expenseRepository.deleteExpensesByCardId(card.id)
                creditCardRepository.deleteCreditCard(card)
            }
        }
    }

    fun updatePeriodWithDates(period: CardClosingPeriod, closingMillis: Long, dueMillis: Long, fromThisMonthForward: Boolean) {
        val closingCal = Calendar.getInstance().apply { timeInMillis = closingMillis }
        val newClosingDay = closingCal.get(Calendar.DAY_OF_MONTH)
        val newDueDay = Calendar.getInstance().apply { timeInMillis = dueMillis }.get(Calendar.DAY_OF_MONTH)
        val dueDaysOffset = ((dueMillis - closingMillis) / (24L * 60 * 60 * 1000)).toInt()
        viewModelScope.launch {
            if (fromThisMonthForward) {
                cardClosingPeriodRepository.updateFromDates(period.cardId, period.month, newClosingDay, dueDaysOffset)
                creditCardRepository.getCardById(period.cardId)?.let { card ->
                    creditCardRepository.updateCreditCard(card.copy(closingDay = newClosingDay, dueDay = newDueDay))
                }
            } else {
                val updated = BillingPeriodHelper.buildPeriodFromExactDates(period, closingMillis, dueMillis)
                cardClosingPeriodRepository.update(updated)
                if (period.month == BillingPeriodHelper.currentMonth()) {
                    creditCardRepository.getCardById(period.cardId)?.let { card ->
                        creditCardRepository.updateCreditCard(card.copy(closingDay = newClosingDay, dueDay = newDueDay))
                    }
                }
            }
        }
    }
}
