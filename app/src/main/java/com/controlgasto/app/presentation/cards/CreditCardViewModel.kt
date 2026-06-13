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

    fun addCard(name: String, lastFour: String, dueDay: Int, closingDay: Int, colorHex: Long) {
        val state = uiState.value
        if (!state.canAddCard) return
        if (name.isBlank() || lastFour.isBlank()) return
        val card = CreditCard(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            lastFourDigits = lastFour.trim(),
            dueDay = dueDay,
            closingDay = closingDay,
            colorHex = colorHex
        )
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val periods = BillingPeriodHelper.generatePeriodsFromMonth(card.id, closingDay, dueDay, year, month)
            // Insertar períodos ANTES de la tarjeta para que estén disponibles
            // cuando el listener de Firestore dispare la actualización de cards
            cardClosingPeriodRepository.insertAll(periods)
            creditCardRepository.addCreditCard(card)
        }
    }

    fun updateCard(card: CreditCard, name: String, lastFour: String, dueDay: Int, closingDay: Int, colorHex: Long) {
        if (name.isBlank() || lastFour.isBlank()) return
        viewModelScope.launch {
            creditCardRepository.updateCreditCard(
                card.copy(
                    name = name.trim(),
                    lastFourDigits = lastFour.trim(),
                    dueDay = dueDay,
                    closingDay = closingDay,
                    colorHex = colorHex
                )
            )
            if (closingDay != card.closingDay || dueDay != card.dueDay) {
                val currentMonth = BillingPeriodHelper.currentMonth()
                val currentPeriod = cardClosingPeriodRepository.getPeriodForCardAndMonth(card.id, currentMonth)
                if (currentPeriod != null) {
                    val parts = currentMonth.split("-")
                    val updated = BillingPeriodHelper.buildPeriod(card.id, parts[0].toInt(), parts[1].toInt(), closingDay, dueDay)
                        .copy(id = currentPeriod.id, createdAt = currentPeriod.createdAt)
                    cardClosingPeriodRepository.update(updated)
                }
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

    fun updatePeriodDays(period: CardClosingPeriod, newClosingDay: Int, newDueDay: Int, fromThisMonthForward: Boolean) {
        viewModelScope.launch {
            if (fromThisMonthForward) {
                cardClosingPeriodRepository.updateFromMonth(period.cardId, period.month, newClosingDay, newDueDay)
                creditCardRepository.getCardById(period.cardId)?.let { card ->
                    creditCardRepository.updateCreditCard(card.copy(closingDay = newClosingDay, dueDay = newDueDay))
                }
            } else {
                val updated = BillingPeriodHelper.recalculatePeriod(period, newClosingDay, newDueDay)
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
