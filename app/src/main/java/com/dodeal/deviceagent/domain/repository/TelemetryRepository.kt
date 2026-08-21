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
    suspend fun sendHeartbeat(): Boolean
    suspend fun pollAndExecuteCommands(): Int
}

class TelemetryRepositoryImpl(
    private val deviceApi: DeviceApi,
    private val deviceRepository: DeviceRepository,
    private val heartbeatFactory: HeartbeatFactory,
    private val commandExecutor: CommandExecutor,
) : TelemetryRepository {

    override suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        val heartbeat = heartbeatFactory.build() ?: return@withContext false
        when (deviceApi.sendHeartbeat(heartbeat)) {
            is ApiResult.Success -> {
                AgentLog.i(AgentLog.Category.API, "heartbeat sent")
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
