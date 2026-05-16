package com.controlgasto.app.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlgasto.app.core.UserPreferences
import com.controlgasto.app.domain.model.Category
import com.controlgasto.app.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryUiState(
    val categories: List<Category> = emptyList(),
    val isProMode: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<CategoryUiState> = combine(
        categoryRepository.getCategories(),
        userPreferences.isProMode
    ) { cats, isPro ->
        CategoryUiState(
            categories = if (isPro) cats else cats.filter { it.isDefault },
            isProMode = isPro,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryUiState())

    fun deleteCategory(category: Category) {
        if (category.isDefault) return
        viewModelScope.launch { categoryRepository.deleteCategory(category) }
    }
}
