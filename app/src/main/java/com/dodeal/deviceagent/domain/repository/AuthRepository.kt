package com.dodeal.deviceagent.domain.repository

import com.dodeal.deviceagent.config.ApiConfig
import com.dodeal.deviceagent.data.api.ApiError
import com.dodeal.deviceagent.data.api.ApiResult
import com.dodeal.deviceagent.data.api.DeviceApi
import com.dodeal.deviceagent.data.api.cms.CmsClientProvider
import com.dodeal.deviceagent.data.auth.AuthProvider
import com.dodeal.deviceagent.data.tenant.TenantStore
import com.dodeal.deviceagent.diagnostics.AgentLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-tenant login (spec):
 *   1. Resolve Client ID -> tenant via the CMS API.
 *   2. If the client exists and is active, build the tenant base URL and persist it.
 *   3. Authenticate loginId + password against the tenant, store the returned credentials.
 * Authentication is never attempted unless a valid, active client is returned first.
 */
interface AuthRepository {
    suspend fun login(clientId: String, loginId: String, password: String): LoginOutcome
}

class AuthRepositoryImpl(
    private val apiConfig: ApiConfig,
    private val cmsClientProvider: CmsClientProvider,
    private val tenantStore: TenantStore,
    private val deviceApi: DeviceApi,
    private val authProvider: AuthProvider,
) : AuthRepository {

    override suspend fun login(clientId: String, loginId: String, password: String): LoginOutcome =
        withContext(Dispatchers.IO) {
            AgentLog.i(AgentLog.Category.AUTH, "resolving client")

            // Step 1: CMS client lookup.
            val client = when (val res = cmsClientProvider.getClient(clientId)) {
                is ApiResult.Success -> res.data
                is ApiResult.Failure -> return@withContext when (val e = res.error) {
                    is ApiError.Network -> LoginOutcome.Failed("No connection to the server")
                    is ApiError.Http ->
                        if (e.code == 404) LoginOutcome.InvalidCredentials("Invalid Client ID")
                        else LoginOutcome.Failed("Client lookup failed (${e.code})")
                    else -> LoginOutcome.Failed("Client lookup failed")
                }
            }

            // Validation: only proceed for an existing, active client.
            if (!client.isActive) {
                return@withContext LoginOutcome.InvalidCredentials("Client is not active")
            }
            if (client.clientDomain.isBlank()) {
                return@withContext LoginOutcome.Failed("Client has no domain configured")
            }

            // Step 2: build + persist the tenant base URL.
            val baseUrl = "${apiConfig.tenantScheme}://${client.clientDomain.trim().trimEnd('/')}/api/"
            tenantStore.save(baseUrl = baseUrl, clientId = client.clientId, clientName = client.clientName)
            AgentLog.i(AgentLog.Category.AUTH, "tenant resolved")

            // Step 3: authenticate against the tenant.
            when (val res = deviceApi.tenantLogin(loginId.trim(), password)) {
                is ApiResult.Success -> {
                    authProvider.onRegistered(res.data)
                    // Remembered so "Assigned User" survives a restart; without it the status
                    // screen fell back to the numeric device id.
                    tenantStore.saveUsername(loginId.trim())
                    AgentLog.i(AgentLog.Category.AUTH, "login success")
                    LoginOutcome.Success(res.data.deviceId)
                }
                is ApiResult.Failure -> when (val e = res.error) {
                    is ApiError.Unauthorized -> LoginOutcome.InvalidCredentials("Wrong Login ID or password")
                    is ApiError.Http ->
                        if (e.code == 401 || e.code == 403) LoginOutcome.InvalidCredentials("Wrong Login ID or password")
                        else LoginOutcome.Failed("Login failed (${e.code})")
                    is ApiError.Network -> LoginOutcome.Failed("No connection to the server")
                    else -> LoginOutcome.Failed("Login failed")
                }
            }
        }
}
