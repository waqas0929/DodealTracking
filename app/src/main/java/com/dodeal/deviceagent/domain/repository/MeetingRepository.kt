package com.dodeal.deviceagent.domain.repository

import com.dodeal.deviceagent.data.api.ApiResult
import com.dodeal.deviceagent.data.api.DeviceApi
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.domain.model.MeetingCheckin
import com.dodeal.deviceagent.domain.model.MeetingCheckout
import com.dodeal.deviceagent.location.CurrentLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/** Agent-initiated meeting check-in / check-out, stamped with the current location. */
interface MeetingRepository {
    suspend fun checkIn(meetingId: String): MeetingOutcome
    suspend fun checkOut(meetingId: String): MeetingOutcome
}

sealed interface MeetingOutcome {
    data object Success : MeetingOutcome
    data class Failed(val reason: String) : MeetingOutcome
}

class MeetingRepositoryImpl(
    private val deviceApi: DeviceApi,
    private val deviceRepository: DeviceRepository,
    private val currentLocation: CurrentLocationProvider,
) : MeetingRepository {

    override suspend fun checkIn(meetingId: String): MeetingOutcome = withContext(Dispatchers.IO) {
        val deviceId = deviceRepository.credentials()?.deviceId
            ?: return@withContext MeetingOutcome.Failed("Not logged in")
        val loc = currentLocation.lastLocation()
        val request = MeetingCheckin(
            eventId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            meetingId = meetingId.trim(),
            timestamp = Instant.now(),
            latitude = loc?.latitude ?: 0.0,
            longitude = loc?.longitude ?: 0.0,
            accuracy = loc?.let { if (it.hasAccuracy()) it.accuracy else null },
        )
        when (deviceApi.meetingCheckin(request)) {
            is ApiResult.Success -> { AgentLog.i(AgentLog.Category.API, "meeting check-in $meetingId"); MeetingOutcome.Success }
            is ApiResult.Failure -> MeetingOutcome.Failed("Couldn't check in")
        }
    }

    override suspend fun checkOut(meetingId: String): MeetingOutcome = withContext(Dispatchers.IO) {
        val deviceId = deviceRepository.credentials()?.deviceId
            ?: return@withContext MeetingOutcome.Failed("Not logged in")
        val loc = currentLocation.lastLocation()
        val request = MeetingCheckout(
            eventId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            meetingId = meetingId.trim(),
            timestamp = Instant.now(),
            latitude = loc?.latitude ?: 0.0,
            longitude = loc?.longitude ?: 0.0,
            accuracy = loc?.let { if (it.hasAccuracy()) it.accuracy else null },
        )
        when (deviceApi.meetingCheckout(request)) {
            is ApiResult.Success -> { AgentLog.i(AgentLog.Category.API, "meeting check-out $meetingId"); MeetingOutcome.Success }
            is ApiResult.Failure -> MeetingOutcome.Failed("Couldn't check out")
        }
    }
}
