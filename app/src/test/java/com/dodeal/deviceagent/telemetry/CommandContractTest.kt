package com.dodeal.deviceagent.telemetry

import com.dodeal.deviceagent.data.api.dto.DodealCommandResponse
import com.dodeal.deviceagent.data.api.mapper.TelemetryMapper
import com.dodeal.deviceagent.data.http.ApiJson
import com.dodeal.deviceagent.domain.model.CommandAck
import com.dodeal.deviceagent.domain.model.CommandStatus
import com.dodeal.deviceagent.domain.model.CommandType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The command vocabulary and the acknowledgement, both of which disagreed with the server.
 *
 * The ack matters more than it looks: the server treats a missing `success` as TRUE, so before this
 * every failed command was recorded in the CRM as executed — the one report that must never lie.
 */
class CommandContractTest {

    @Test
    fun `every name the server can send resolves to something the app runs`() {
        // config('device_tracking.commands.allowed') on the server, verbatim.
        val serverVocabulary = listOf(
            "SYNC_NOW", "UPLOAD_PENDING", "REFRESH_POLICY", "PING", "RESTART_TRACKING",
        )

        serverVocabulary.forEach { name ->
            assertTrue(
                "$name is not understood by the agent",
                CommandType.fromWire(name) != CommandType.UNKNOWN,
            )
        }
    }

    @Test
    fun `the server's UPLOAD_PENDING is the app's UPLOAD_PENDING_DATA`() {
        // Same action, two vocabularies. Kept as an alias so a command queued under either name runs.
        assertEquals(CommandType.UPLOAD_PENDING_DATA, CommandType.fromWire("UPLOAD_PENDING"))
        assertEquals(CommandType.UPLOAD_PENDING_DATA, CommandType.fromWire("UPLOAD_PENDING_DATA"))
    }

    @Test
    fun `an unrecognised or empty command never throws`() {
        assertEquals(CommandType.UNKNOWN, CommandType.fromWire("RUN_SHELL"))
        assertEquals(CommandType.UNKNOWN, CommandType.fromWire(""))
        assertEquals(CommandType.UNKNOWN, CommandType.fromWire(null))
    }

    @Test
    fun `command names are case insensitive`() {
        assertEquals(CommandType.SYNC_NOW, CommandType.fromWire("sync_now"))
        assertEquals(CommandType.PING, CommandType.fromWire(" Ping "))
    }

    @Test
    fun `a failed command is acknowledged as failed`() {
        val json = ApiJson.instance.encodeToString(
            com.dodeal.deviceagent.data.api.dto.DodealCommandAckRequest.serializer(),
            TelemetryMapper.toDto(
                CommandAck(
                    commandId = "7",
                    status = CommandStatus.FAILED,
                    receivedAt = Instant.parse("2026-08-22T10:00:00Z"),
                    executedAt = Instant.parse("2026-08-22T10:00:01Z"),
                    result = "no permission",
                )
            )
        )

        assertTrue("the server reads `success`; it must be present", json.contains("\"success\""))
        assertTrue("a failure must not be reported as executed", json.contains("\"success\":false"))
    }

    @Test
    fun `an executed command is acknowledged as successful`() {
        val json = ApiJson.instance.encodeToString(
            com.dodeal.deviceagent.data.api.dto.DodealCommandAckRequest.serializer(),
            TelemetryMapper.toDto(
                CommandAck(
                    commandId = "7",
                    status = CommandStatus.EXECUTED,
                    receivedAt = Instant.parse("2026-08-22T10:00:00Z"),
                    executedAt = Instant.parse("2026-08-22T10:00:01Z"),
                    result = "policy v1",
                )
            )
        )

        assertTrue(json.contains("\"success\":true"))
    }

    @Test
    fun `a payload object becomes parameters and anything else is simply empty`() {
        fun parse(payload: String): DodealCommandResponse = ApiJson.instance.decodeFromString(
            DodealCommandResponse.serializer(),
            """{"command_id": 9, "command": "SYNC_NOW", "payload": $payload}""",
        )

        assertEquals(mapOf("reason" to "manual"), parse("""{"reason": "manual"}""").parameters)
        // A payload the app does not expect must never cost the command its execution.
        assertEquals(emptyMap<String, String>(), parse("null").parameters)
        assertEquals(emptyMap<String, String>(), parse("\"just a string\"").parameters)
        assertEquals(emptyMap<String, String>(), parse("[1,2,3]").parameters)
    }
}
