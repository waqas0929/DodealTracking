package com.dodeal.deviceagent.ui.login

data class LoginUiState(
    val clientId: String = "",
    val loginId: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedInUsername: String? = null, // non-null => navigate to status
)
