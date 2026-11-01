package com.liyue.mctoolbox.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val nickname: String = "",
    val registerMode: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    val user: StateFlow<AuthUser?> = repository.user
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onNicknameChange(value: String) = _state.update { it.copy(nickname = value, error = null) }

    fun toggleMode() = _state.update {
        it.copy(registerMode = !it.registerMode, error = null)
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = if (current.registerMode) {
                repository.register(current.email, current.password, current.nickname)
            } else {
                repository.login(current.email, current.password)
            }
            _state.update {
                result.fold(
                    onSuccess = { user -> it.copy(loading = false, done = true, error = null) },
                    onFailure = { error -> it.copy(loading = false, error = error.message ?: "操作失败") },
                )
            }
        }
    }

    fun logout() = repository.logout()

    fun reset() = _state.update { AuthUiState(registerMode = it.registerMode) }
}
