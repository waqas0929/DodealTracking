package com.dodeal.deviceagent.data.api

import com.dodeal.deviceagent.data.api.dto.DodealCommandListResponse
import com.dodeal.deviceagent.data.api.dto.DodealPolicyResponse
import com.dodeal.deviceagent.data.http.ApiJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire contract, pinned against RESPONSES CAPTURED FROM THE REAL SERVER.
 *
 * Both payloads below were taken from a live tenant, not written from the spec — the spec and the
 * server had drifted, and the app was built against the spec. Parsing here uses ApiJson.instance,
 * the same parser Retrofit uses, so a test passing means the app really can read what the server
 * really sends.
 */
class ServerContractTest {

    /** GET /api/device/policy — captured from findestates.dodealcrm.com. */
    private val policyJson = """
        {
          "success": true,
          "tracking_enabled": true,
          "timezone": "Asia/Dubai",
          "working_hours": {
            "monday":    {"enabled": true,  "start": "10:00", "end": "19:00"},
            "tuesday":   {"enabled": true,  "start": "10:00", "end": "19:00"},
            "wednesday": {"enabled": true,  "start": "10:00", "end": "19:00"},
            "thursday":  {"enabled": true,  "start": "10:00", "end": "19:00"},
            "friday":    {"enabled": true,  "start": "10:00", "end": "19:00"},
            "saturday":  {"enabled": true,  "start": "10:00", "end": "19:00"},
            "sunday":    {"enabled": false, "start": "00:00", "end": "00:00"}
          },
          "location_interval_seconds": 60,
          "high_accuracy_enabled": true,
          "distance_filter_meters": 25,
          "offline_queue_limit": 500,
          "heartbeat_interval_seconds": 300,
          "policy_version": "833db1a6d05c9813",
          "device_id": 1,
          "device_uid": "field-phone-01",
          "user_id": 1,
          "server_time": "2026-08-22T01:03:42+04:00",
          "server_timezone": "Asia/Dubai",
          "server_time_unix": "1787335422"
        }
    """.trimIndent()

    /** GET /api/device/commands — the shape DeviceCommandController actually returns. */
    private val commandsJson = """
        {
          "success": true,
          "device_id": 1,
          "commands": [
            {
              "command_id": 7,
              "command": "SYNC_NOW",
              "payload": null,
              "issued_at": "2026-08-22T01:02:30+04:00",
              "expires_at": "2026-08-22T02:02:30+04:00"
            }
          ],
          "server_time": "2026-08-22T01:03:42+04:00"
        }
    """.trimIndent()

    @Test
    fun `policy from the server is understood`() {
        val policy = ApiJson.instance.decodeFromString(DodealPolicyResponse.serializer(), policyJson)

        assertTrue(policy.trackingEnabled)
        assertEquals("Asia/Dubai", policy.timezone)
        assertEquals(60, policy.locationIntervalSeconds)
        assertEquals(300, policy.heartbeatIntervalSeconds)
        assertEquals("833db1a6d05c9813", policy.policyVersion)
        assertEquals(7, policy.workingHours.size)
        assertEquals("10:00", policy.workingHours.getValue("monday").start)
        assertTrue(policy.workingHours.getValue("sunday").enabled.not())
    }

    @Test
    fun `the distance filter the CRM configured is the one the device uses`() {
        val policy = ApiJson.instance.decodeFromString(DodealPolicyResponse.serializer(), policyJson)

        // The server's field is distance_filter_meters. The app read minimum_distance_meters, so
        // this silently stayed at its own default of 20 and the CRM's setting did nothing.
        assertEquals(25, policy.minimumDistanceMeters)
    }

    @Test
    fun `a queued command from the server is understood`() {
        val response = ApiJson.instance
            .decodeFromString(DodealCommandListResponse.serializer(), commandsJson)

        assertEquals(1, response.commands.size)
        val command = response.commands.first()

        // The id arrives as a NUMBER; the DTO types it String.
        assertEquals("7", command.commandId)
        // The server's field is `command`, not `type`.
        assertEquals("SYNC_NOW", command.type)
        assertNotNull(command.issuedAt)
    }

    @Test
    fun `an unknown extra field never breaks a response`() {
        val withExtras = commandsJson.replace(
            "\"success\": true,",
            "\"success\": true, \"a_field_added_next_year\": {\"nested\": 1},"
        )

        val response = ApiJson.instance
            .decodeFromString(DodealCommandListResponse.serializer(), withExtras)

        assertEquals(1, response.commands.size)
    }
}
