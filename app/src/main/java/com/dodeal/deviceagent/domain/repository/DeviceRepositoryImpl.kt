package com.dodeal.deviceagent.domain.repository

import com.dodeal.deviceagent.config.EnrollmentConfig
import com.dodeal.deviceagent.data.api.ApiError
import com.dodeal.deviceagent.data.api.ApiResult
import com.dodeal.deviceagent.data.api.DeviceApi
import com.dodeal.deviceagent.data.auth.AuthProvider
import com.dodeal.deviceagent.data.storage.PolicyStore
import com.dodeal.deviceagent.device.DeviceInfoProvider
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.domain.model.DeviceCredentials
import com.dodeal.deviceagent.domain.model.DevicePolicy
import com.dodeal.deviceagent.domain.model.LoginRequest
import com.dodeal.deviceagent.domain.model.RegistrationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/**
 * Orchestrates login, token refresh and policy sync. It depends ONLY on the DeviceApi abstraction,
 * the auth abstraction and the storage abstractions — never on Retrofit, DTOs or endpoint paths.
 */
class DeviceRepositoryImpl(
    private val deviceApi: DeviceApi,
    private val authProvider: AuthProvider,
    private val policyStore: PolicyStore,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val enrollmentConfig: EnrollmentConfig,
) : DeviceRepository {

    override fun credentials(): DeviceCredentials? = authProvider.currentCredentials()
    override fun cachedPolicy(): DevicePolicy? = policyStore.load()
    override fun lastPolicySync(): Instant? = policyStore.lastSyncedAt()
    override fun isRegistered(): Boolean = authProvider.currentCredentials() != null

    override suspend fun login(
        companyId: String,
        username: String,
        password: String,
    ): LoginOutcome = withContext(Dispatchers.IO) {
        val request = LoginRequest(
            companyId = companyId.trim(),
            username = username.trim(),
            password = password,
            identity = deviceInfoProvider.identity(),
        )

        when (val result = deviceApi.login(request)) {
            is ApiResult.Success -> {
                authProvider.onRegistered(result.data.credentials)
                AgentLog.i(AgentLog.Category.AUTH, "Agent logged in: ${result.data.deviceId}")
                LoginOutcome.Success(result.data.deviceId)
            }
            is ApiResult.Failure -> when (result.error) {
                is ApiError.Unauthorized ->
                    LoginOutcome.InvalidCredentials("Wrong company id, username or password")
                else -> LoginOutcome.Failed(result.error.describe())
            }
        }
    }

    override fun logout() {
        authProvider.onUnauthorized()
        AgentLog.i(AgentLog.Category.AUTH, "Agent logged out; session cleared")
    }

    /**
     * Guarantees a usable access token before an authenticated call. If the token is near expiry and
     * we hold a refresh token, rotate it silently; the agent never re-enters a password.
     */
    private suspend fun ensureValidSession(): Boolean {
        val current = authProvider.currentCredentials() ?: return false
        if (!current.expiresWithin(REFRESH_SKEW)) return true

        val refresh = current.refreshToken ?: return !current.isExpired()
        return when (val result = deviceApi.refreshToken(refresh)) {
            is ApiResult.Success -> {
                authProvider.onCredentialsRenewed(result.data)
                AgentLog.i(AgentLog.Category.AUTH, "Access token refreshed")
                true
            }
            is ApiResult.Failure -> {
                AgentLog.w(AgentLog.Category.AUTH, "Token refresh failed: ${result.error.describe()}")
                // Keep the enrollment regardless; we retry on the next cycle rather than logging out.
                !current.isExpired()
            }
        }
    }

    override suspend fun ensureRegistered(): RegistrationOutcome = withContext(Dispatchers.IO) {
        val existing = authProvider.currentCredentials()
        if (existing != null && !existing.isExpired()) {
            return@withContext RegistrationOutcome.AlreadyRegistered
        }

        val request = RegistrationRequest(
            agencyId = enrollmentConfig.agencyId,
            salesUserId = enrollmentConfig.salesUserId,
            identity = deviceInfoProvider.identity(),
        )

        when (val result = deviceApi.register(request)) {
            is ApiResult.Success -> {
                authProvider.onRegistered(result.data.credentials)
                AgentLog.i(AgentLog.Category.AUTH, "Device registered: ${result.data.deviceId}")
                RegistrationOutcome.Registered(result.data.deviceId)
            }
            is ApiResult.Failure -> {
                val reason = result.error.describe()
                AgentLog.e(AgentLog.Category.AUTH, "Registration failed: $reason")
                RegistrationOutcome.Failed(reason)
            }
        }
    }

    override suspend fun syncPolicy(): PolicyOutcome = withContext(Dispatchers.IO) {
        if (!ensureValidSession()) {
            return@withContext PolicyOutcome.Unavailable("Not logged in")
        }
        val deviceId = authProvider.currentCredentials()?.deviceId
            ?: return@withContext PolicyOutcome.Unavailable("Not logged in")

        when (val result = deviceApi.getPolicy(deviceId)) {
            is ApiResult.Success -> {
                policyStore.save(result.data)
                AgentLog.i(AgentLog.Category.POLICY, "Policy synced: v${result.data.policyVersion}")
                PolicyOutcome.Synced(result.data)
            }
            is ApiResult.Failure -> {
                val reason = result.error.describe()
                val cached = policyStore.load()
                if (cached != null) {
                    AgentLog.w(AgentLog.Category.POLICY, "Policy sync failed ($reason); serving cache v${cached.policyVersion}")
                    PolicyOutcome.ServedFromCache(cached, reason)
                } else {
                    AgentLog.e(AgentLog.Category.POLICY, "Policy sync failed ($reason); no cache")
                    PolicyOutcome.Unavailable(reason)
                }
            }
        }
    }

    private fun ApiError.describe(): String = when (this) {
        is ApiError.Network -> "network error"
        is ApiError.Http -> "http $code${message?.let { " ($it)" } ?: ""}"
        ApiError.Unauthorized -> "unauthorized"
        is ApiError.Unexpected -> "unexpected: ${cause.message}"
    }

    private companion object {
        /** Refresh the access token once it's within this window of expiry. */
        val REFRESH_SKEW: Duration = Duration.ofMinutes(2)
    }
}
