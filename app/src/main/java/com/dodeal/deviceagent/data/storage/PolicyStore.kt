package com.dodeal.deviceagent.data.storage

import com.dodeal.deviceagent.domain.model.DevicePolicy
import java.time.Instant

/** Local cache for the latest valid policy so the agent keeps working while offline. */
interface PolicyStore {
    fun save(policy: DevicePolicy)
    fun load(): DevicePolicy?
    fun lastSyncedAt(): Instant?
}
