package com.dodeal.deviceagent.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dodeal.deviceagent.domain.model.DeviceCredentials
import java.time.Instant

/**
 * Token storage backed by AES-256 EncryptedSharedPreferences (Keystore master key). Secrets are
 * never written in plain text and never logged.
 */
class EncryptedTokenStorage(context: Context) : SecureTokenStorage {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun save(credentials: DeviceCredentials) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, credentials.deviceId)
            .putString(KEY_TOKEN, credentials.accessToken)
            .putString(KEY_TOKEN_TYPE, credentials.tokenType)
            .putString(KEY_EXPIRES_AT, credentials.expiresAt.toString())
            .putString(KEY_REFRESH_TOKEN, credentials.refreshToken)
            .apply()
    }

    override fun load(): DeviceCredentials? {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val type = prefs.getString(KEY_TOKEN_TYPE, "Bearer") ?: "Bearer"
        val expires = prefs.getString(KEY_EXPIRES_AT, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
        return runCatching {
            DeviceCredentials(deviceId, token, type, Instant.parse(expires), refresh)
        }.getOrNull()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "dodeal_secure_credentials"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_TOKEN_TYPE = "token_type"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
