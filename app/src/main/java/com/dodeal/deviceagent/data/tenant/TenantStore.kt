package com.dodeal.deviceagent.data.tenant

import android.content.Context

/**
 * Persists the tenant base URL resolved from the Client ID at login (spec: multi-tenant auth). The
 * device enrolls to ONE tenant permanently, so this is stored and reused across restarts. The domain
 * isn't secret, so plain (device-protected) prefs are fine and safe during direct boot.
 */
interface TenantStore {
    fun save(baseUrl: String, clientId: String, clientName: String)
    fun baseUrl(): String?
    fun clientId(): String?
    fun clientName(): String?
    fun clear()
}

class PrefsTenantStore(context: Context) : TenantStore {
    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences("dodeal_tenant", Context.MODE_PRIVATE)

    override fun save(baseUrl: String, clientId: String, clientName: String) {
        prefs.edit()
            .putString(KEY_BASE, baseUrl)
            .putString(KEY_ID, clientId)
            .putString(KEY_NAME, clientName)
            .apply()
    }

    override fun baseUrl(): String? = prefs.getString(KEY_BASE, null)
    override fun clientId(): String? = prefs.getString(KEY_ID, null)
    override fun clientName(): String? = prefs.getString(KEY_NAME, null)
    override fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_BASE = "base_url"
        const val KEY_ID = "client_id"
        const val KEY_NAME = "client_name"
    }
}
