package com.dodeal.deviceagent.data.auth

import com.dodeal.deviceagent.diagnostics.AgentLog
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Applies auth + (optional) signing to every request. A 401 is NOT treated as a logout: the device
 * is company-owned and enrolls once, so we keep the credentials and let the proactive token refresh
 * recover the session. The app never bounces the agent back to the login screen on its own.
 */
class AuthInterceptor(
    private val authProvider: AuthProvider,
    private val requestSigner: RequestSigner = RequestSigner.NoOp,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        authProvider.applyTo(builder)      // bearer today; rotation-aware later
        requestSigner.sign(builder, original) // no-op today; HMAC/nonce/timestamp later

        val response = chain.proceed(builder.build())
        if (response.code == 401) {
            // Do not clear the session; the next refresh attempt will try to rotate the token.
            AgentLog.w(AgentLog.Category.AUTH, "401 received; keeping enrollment, will refresh token")
        }
        return response
    }
}
