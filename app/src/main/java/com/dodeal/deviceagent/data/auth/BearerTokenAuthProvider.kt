package com.dodeal.deviceagent.data.auth

import com.dodeal.deviceagent.data.storage.SecureTokenStorage
import com.dodeal.deviceagent.domain.model.DeviceCredentials
import okhttp3.Request

/**
 * Bearer-token implementation. Credentials are persisted via SecureTokenStorage (Keystore-backed).
 * Loading is lazy and crash-safe so early process start (e.g. direct boot, before unlock) can never
 * throw from credential-encrypted storage.
 */
class BearerTokenAuthProvider(
    private val storage: SecureTokenStorage,
) : AuthProvider {

    @Volatile private var cached: DeviceCredentials? = null
    @Volatile private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        cached = runCatching { storage.load() }.getOrNull()
        loaded = true
    }

    override fun currentCredentials(): DeviceCredentials? {
        ensureLoaded()
        return cached
    }

    override fun isAuthenticated(): Boolean = currentCredentials()?.let { !it.isExpired() } ?: false

    override fun applyTo(builder: Request.Builder) {
        val c = currentCredentials() ?: return
        builder.header("Authorization", "${c.tokenType} ${c.accessToken}")
    }

    override fun onRegistered(credentials: DeviceCredentials) {
        cached = credentials
        loaded = true
        runCatching { storage.save(credentials) }
    }

    override fun onUnauthorized() {
        cached = null
        loaded = true
        runCatching { storage.clear() }
    }
}
