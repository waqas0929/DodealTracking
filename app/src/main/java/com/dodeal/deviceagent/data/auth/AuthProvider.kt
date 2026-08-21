package com.dodeal.deviceagent.data.auth

import com.dodeal.deviceagent.domain.model.DeviceCredentials
import okhttp3.Request

/**
 * Abstract authentication strategy. Business logic and the repository never touch tokens directly —
 * they just call the API. Today this is a bearer token; token rotation, signed requests,
 * nonce/timestamp replay protection and mTLS are added as new implementations/decorators WITHOUT
 * changing any consumer.
 */
interface AuthProvider {
    fun currentCredentials(): DeviceCredentials?
    fun isAuthenticated(): Boolean

    /** Attach auth material to an outgoing request (e.g. Authorization header). No-op if unauthenticated. */
    fun applyTo(builder: Request.Builder)

    /** Persist credentials after a successful registration / login. */
    fun onRegistered(credentials: DeviceCredentials)

    /** Persist rotated credentials after a token refresh. Defaults to the same path as registration. */
    fun onCredentialsRenewed(credentials: DeviceCredentials) = onRegistered(credentials)

    /** Invoked when the server rejects our credentials (401): clear + flag for re-registration. */
    fun onUnauthorized()
}
