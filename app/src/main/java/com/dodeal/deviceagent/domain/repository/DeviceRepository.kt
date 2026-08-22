package com.dodeal.deviceagent.domain.repository

import com.dodeal.deviceagent.domain.model.DeviceCredentials
import com.dodeal.deviceagent.domain.model.DevicePolicy
import java.time.Instant

/** Domain-layer entry point for the UI and (later) the background workers. Speaks only domain terms. */
interface DeviceRepository {
    /** Enroll this device as a specific agent using their company id, username and password. */
    suspend fun login(companyId: String, username: String, password: String): LoginOutcome

    /** Drop the current agent's session (clears tokens); the UI returns to the login screen. */

    suspend fun ensureRegistered(): RegistrationOutcome
    suspend fun syncPolicy(): PolicyOutcome

    fun credentials(): DeviceCredentials?
    fun cachedPolicy(): DevicePolicy?
    fun lastPolicySync(): Instant?
    fun isRegistered(): Boolean
}

sealed interface LoginOutcome {
    data class Success(val deviceId: String) : LoginOutcome
    data class InvalidCredentials(val reason: String) : LoginOutcome
    data class Failed(val reason: String) : LoginOutcome
}

sealed interface RegistrationOutcome {
    data class Registered(val deviceId: String) : RegistrationOutcome
    data object AlreadyRegistered : RegistrationOutcome
    data class Failed(val reason: String) : RegistrationOutcome
}

sealed interface PolicyOutcome {
    data class Synced(val policy: DevicePolicy) : PolicyOutcome
    data class ServedFromCache(val policy: DevicePolicy, val reason: String) : PolicyOutcome
    data class Unavailable(val reason: String) : PolicyOutcome
}
