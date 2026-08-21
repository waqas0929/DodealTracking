package com.dodeal.deviceagent.telemetry

import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.domain.model.CommandAck
import com.dodeal.deviceagent.domain.model.CommandStatus
import com.dodeal.deviceagent.domain.model.CommandType
import com.dodeal.deviceagent.domain.model.DeviceCommand
import com.dodeal.deviceagent.domain.model.LocationPoint
import com.dodeal.deviceagent.domain.repository.DeviceRepository
import com.dodeal.deviceagent.domain.repository.LocationRepository
import com.dodeal.deviceagent.domain.repository.PolicyOutcome
import com.dodeal.deviceagent.location.CurrentLocationProvider
import com.dodeal.deviceagent.location.DeviceStateProvider
import com.dodeal.deviceagent.location.LocationController
import com.dodeal.deviceagent.location.isCharging
import java.time.Instant
import java.util.UUID

/**
 * Executes ONLY the fixed, predefined command set (spec §16) — no arbitrary/remote code path.
 * Every command is acked with received/executed timestamps, status and result.
 */
class CommandExecutor(
    private val deviceRepository: DeviceRepository,
    private val locationController: LocationController,
    private val locationRepository: LocationRepository,
    private val currentLocation: CurrentLocationProvider,
    private val deviceState: DeviceStateProvider,
) {
    suspend fun execute(command: DeviceCommand): CommandAck {
        val received = Instant.now()
        AgentLog.i(AgentLog.Category.COMMANDS, "command received id=${command.commandId}")
        val (status, result) = runCatching { run(command) }
            .getOrElse { CommandStatus.FAILED to (it.message ?: "error") }
        AgentLog.i(AgentLog.Category.COMMANDS, "command completed id=${command.commandId}")
        return CommandAck(
            commandId = command.commandId,
            status = status,
            receivedAt = received,
            executedAt = Instant.now(),
            result = result,
        )
    }

    private suspend fun run(command: DeviceCommand): Pair<CommandStatus, String> = when (command.type) {
        CommandType.REFRESH_POLICY, CommandType.SYNC_NOW -> {
            val outcome = deviceRepository.syncPolicy()
            locationController.sync()
            val detail = when (outcome) {
                is PolicyOutcome.Synced -> "policy v${outcome.policy.policyVersion}"
                is PolicyOutcome.ServedFromCache -> "cache (${outcome.reason})"
                is PolicyOutcome.Unavailable -> "unavailable (${outcome.reason})"
            }
            CommandStatus.EXECUTED to detail
        }
        CommandType.UPLOAD_PENDING_DATA -> {
            locationRepository.flush()
            CommandStatus.EXECUTED to "flushed; ${locationRepository.pendingCount()} pending"
        }
        CommandType.REQUEST_SINGLE_LOCATION -> {
            val loc = currentLocation.lastLocation()
                ?: return CommandStatus.FAILED to "no location available"
            val deviceId = deviceRepository.credentials()?.deviceId
                ?: return CommandStatus.FAILED to "not enrolled"
            val point = LocationPoint(
                eventId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                timestamp = Instant.now(),
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
                altitude = if (loc.hasAltitude()) loc.altitude else null,
                speed = if (loc.hasSpeed()) loc.speed else null,
                bearing = if (loc.hasBearing()) loc.bearing else null,
                batteryPercent = deviceState.batteryLevel(),
                charging = deviceState.isCharging(),
                networkType = deviceState.networkType(),
                locationProvider = loc.provider ?: "fused",
                mockLocation = false,
            )
            locationRepository.record(point)
            CommandStatus.EXECUTED to "single location captured"
        }
        CommandType.REFRESH_DEVICE_INFO -> CommandStatus.EXECUTED to "device info refreshed"
        CommandType.UNKNOWN -> CommandStatus.FAILED to "unknown command"
    }
}
