package com.dodeal.deviceagent.domain.repository

import com.dodeal.deviceagent.data.api.ApiResult
import com.dodeal.deviceagent.data.api.DeviceApi
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.telemetry.CommandExecutor
import com.dodeal.deviceagent.telemetry.HeartbeatFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the two server-facing telemetry loops: heartbeat (device -> server health) and command poll
 * (server -> device control). Both go through DeviceApi only; command execution is delegated to the
 * fixed-command CommandExecutor, and every fetched command is acked.
 */
interface TelemetryRepository {
    /**
     * Check in, then ACT on what the server said: re-sync the policy if it moved, and fetch
     * commands only when there are some. Returns false only when the beat itself failed.
     */
    suspend fun sendHeartbeat(): Boolean
    suspend fun pollAndExecuteCommands(): Int
}

class TelemetryRepositoryImpl(
    private val deviceApi: DeviceApi,
    private val deviceRepository: DeviceRepository,
    private val heartbeatFactory: HeartbeatFactory,
    private val commandExecutor: CommandExecutor,
    /**
     * Invoked after a policy the server told us had changed was re-fetched, so the tracking session
     * and the background cadence are rebuilt on it. A callback rather than a dependency: this layer
     * must not know about Android's WorkManager or a foreground service.
     */
    private val onPolicyChanged: (() -> Unit)? = null,
) : TelemetryRepository {

    override suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        val heartbeat = heartbeatFactory.build() ?: return@withContext false

        when (val result = deviceApi.sendHeartbeat(heartbeat)) {
            is ApiResult.Success -> {
                val ack = result.data
                AgentLog.i(
                    AgentLog.Category.API,
                    "heartbeat sent (policyChanged=${ack.policyChanged} pending=${ack.hasPendingCommands})",
                )

                // The whole point of the answer: the device reacts instead of waiting for a person
                // to press Sync, or for the cached policy to rot into staleness.
                if (ack.policyChanged) {
                    deviceRepository.syncPolicy()
                    onPolicyChanged?.invoke()
                }
                if (ack.hasPendingCommands) {
                    pollAndExecuteCommands()
                }
                true
            }
            is ApiResult.Failure -> false
        }
    }

    override suspend fun pollAndExecuteCommands(): Int = withContext(Dispatchers.IO) {
        val deviceId = deviceRepository.credentials()?.deviceId ?: return@withContext 0
        if (deviceRepository.cachedPolicy()?.deviceCommandsEnabled == false) return@withContext 0
        val commands = when (val result = deviceApi.getCommands(deviceId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return@withContext 0
        }
        if (commands.isEmpty()) return@withContext 0

        var executed = 0
        for (command in commands) {
            val ack = commandExecutor.execute(command)
            when (deviceApi.ackCommand(deviceId, ack)) {
                is ApiResult.Success -> executed++
                is ApiResult.Failure ->
                    AgentLog.w(AgentLog.Category.COMMAND, "ack failed for ${command.commandId}")
            }
        }
        AgentLog.i(AgentLog.Category.COMMAND, "processed $executed command(s)")
        executed
    }
}
