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

    /*
     * There is deliberately NO way to clear credentials.
     *
     * This is a company-owned tracking handset: the agent carrying it must not be able to sign
     * out, and nothing in the app may drop the session on its behalf. A 401 is logged and the
     * enrolment is kept (see AuthInterceptor) — dropping it would land the phone on a login screen
     * in someone's pocket, where nobody would ever sign in again and tracking would simply stop
     * with no trace of why.
     *
     * Re-enrolment is a deliberate act: clear the app's storage from Android's App info screen.
     */
}
