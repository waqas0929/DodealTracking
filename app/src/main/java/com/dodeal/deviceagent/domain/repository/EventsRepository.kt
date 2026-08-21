package com.dodeal.deviceagent.domain.repository

import com.dodeal.deviceagent.data.api.ApiResult
import com.dodeal.deviceagent.data.api.DeviceApi
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.domain.model.DeviceEvent
import com.dodeal.deviceagent.domain.model.GeofenceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fire-and-log reporting for geofence transitions and generic device events, via DeviceApi only. */
interface EventsRepository {
    suspend fun reportGeofence(event: GeofenceEvent): Boolean
    suspend fun reportEvent(event: DeviceEvent): Boolean
}

class EventsRepositoryImpl(
    private val deviceApi: DeviceApi,
) : EventsRepository {

    override suspend fun reportGeofence(event: GeofenceEvent): Boolean = withContext(Dispatchers.IO) {
        when (deviceApi.sendGeofenceEvent(event)) {
            is ApiResult.Success -> {
                AgentLog.i(AgentLog.Category.LOCATION, "geofence ${event.transition} ${event.geofenceId}")
                true
            }
            is ApiResult.Failure -> {
                AgentLog.w(AgentLog.Category.LOCATION, "geofence report failed for ${event.geofenceId}")
                false
            }
        }
    }

    override suspend fun reportEvent(event: DeviceEvent): Boolean = withContext(Dispatchers.IO) {
        deviceApi.sendEvent(event) is ApiResult.Success
    }
}
