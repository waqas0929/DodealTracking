package com.dodeal.deviceagent.data.storage

import com.dodeal.deviceagent.domain.model.DeviceCredentials

/**
 * Abstraction over encrypted credential persistence. The default impl is EncryptedSharedPreferences;
 * on the privileged ROM build this can be swapped for a hardware-Keystore-backed store without
 * touching AuthProvider.
 */
interface SecureTokenStorage {
    fun save(credentials: DeviceCredentials)
    fun load(): DeviceCredentials?
    fun clear()
}
