package com.controlgasto.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlgasto.app.core.UserPreferences
import com.controlgasto.app.domain.model.CreditCard
import com.controlgasto.app.domain.repository.AuthRepository
import com.controlgasto.app.domain.repository.CreditCardRepository
import com.controlgasto.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isProMode: Boolean = false,
    val aiRequestsUsed: Int = 0,
    val userEmail: String = "",
    val userDisplayName: String = "",
    val isLoggedIn: Boolean = false,
    val cardCount: Int = 0,
    val cards: List<CreditCard> = emptyList(),
    val isLoaded: Boolean = false,
    val showCancelWarningDialog: Boolean = false,
    val showDowngradeDialog: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val creditCardRepository: CreditCardRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _showCancelWarningDialog = MutableStateFlow(false)
    private val _showDowngradeDialog = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(userPreferences.isProMode, userPreferences.aiRequestsCount) { isPro, ai -> Pair(isPro, ai) },
        combine(userPreferences.userEmail, userPreferences.userDisplayName) { email, name -> Pair(email, name) },
        combine(authRepository.currentUser, creditCardRepository.getCreditCards()) { user, cards -> Pair(user, cards) },
        combine(_showCancelWarningDialog, _showDowngradeDialog) { warning, downgrade -> Pair(warning, downgrade) }
    ) { (isPro, aiCount), (email, displayName), (user, cards), (showWarning, showDowngrade) ->
        SettingsUiState(
            isProMode = isPro,
            aiRequestsUsed = aiCount,
            userEmail = email,
            userDisplayName = displayName,
            isLoggedIn = user != null,
            cardCount = cards.size,
            cards = cards,
            isLoaded = true,
            showCancelWarningDialog = showWarning,
            showDowngradeDialog = showDowngrade
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    // Step 1: show warning with consequences
    fun cancelPro() {
        _showCancelWarningDialog.value = true
    }

    fun dismissCancelWarning() {
        _showCancelWarningDialog.value = false
    }

    // Step 2: user confirmed the warning — proceed with card selection or direct downgrade
    fun confirmCancelWarning() {
        _showCancelWarningDialog.value = false
        val state = uiState.value
        if (state.cardCount > UserPreferences.FREE_CARD_LIMIT) {
            _showDowngradeDialog.value = true
        } else {
            viewModelScope.launch {
                performDowngrade(state.cards.firstOrNull(), emptySet())
            }
        }
    }

    // Step 3 (only when >1 card): user picked which card to keep
    fun confirmDowngrade(cardToKeep: CreditCard) {
        viewModelScope.launch {
            _showDowngradeDialog.value = false
            val deletedCardIds = uiState.value.cards
                .filter { it.id != cardToKeep.id }
                .map { it.id }
                .toSet()
            performDowngrade(cardToKeep, deletedCardIds)
        }
    }

    fun cancelDowngrade() {
        _showDowngradeDialog.value = false
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    private suspend fun performDowngrade(cardToKeep: CreditCard?, deletedCardIds: Set<String>) {
        expenseRepository.downgradeExpenses(deletedCardIds)
        if (cardToKeep != null) {
            creditCardRepository.downgradeToFree(cardToKeep)
        }
        userPreferences.setProMode(false)
        authRepository.updateProStatus(false)
    }
}
