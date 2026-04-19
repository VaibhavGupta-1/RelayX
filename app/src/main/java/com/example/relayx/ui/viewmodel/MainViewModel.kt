package com.example.relayx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.relayx.domain.usecase.GetUserCodeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the main screen.
 */
data class MainUiState(
    val userCode: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for the main screen.
 * Handles user initialization and exposes the current user code.
 */
class MainViewModel(
    private val getUserCodeUseCase: GetUserCodeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        initializeUser()
    }

    /**
     * Initializes the anonymous user identity.
     * Generates a new code if none exists, or loads the existing one.
     */
    private fun initializeUser() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                val code = getUserCodeUseCase()
                _uiState.value = _uiState.value.copy(
                    userCode = code,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to initialize user"
                )
            }
        }
    }

    /**
     * Retries user initialization after a failure.
     */
    fun retry() {
        initializeUser()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
