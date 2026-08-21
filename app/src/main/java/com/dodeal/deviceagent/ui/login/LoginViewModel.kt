package com.dodeal.deviceagent.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dodeal.deviceagent.domain.repository.AuthRepository
import com.dodeal.deviceagent.domain.repository.LoginOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onClientId(v: String) = _state.update { it.copy(clientId = v, error = null) }
    fun onLoginId(v: String) = _state.update { it.copy(loginId = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    fun submit() {
        val s = _state.value
        if (s.clientId.isBlank() || s.loginId.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Enter Client ID, Login ID and Password") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val outcome = authRepository.login(s.clientId, s.loginId, s.password)) {
                is LoginOutcome.Success ->
                    _state.update { it.copy(loading = false, loggedInUsername = s.loginId.trim()) }
                is LoginOutcome.InvalidCredentials ->
                    _state.update { it.copy(loading = false, error = outcome.reason) }
                is LoginOutcome.Failed ->
                    _state.update { it.copy(loading = false, error = outcome.reason) }
            }
        }
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoginViewModel(authRepository) as T
    }
}
