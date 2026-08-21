package com.dodeal.deviceagent.contract

import com.dodeal.deviceagent.data.api.dto.DodealBatchLocationsRequest
import com.dodeal.deviceagent.data.api.dto.DodealCommandAckRequest
import com.dodeal.deviceagent.data.api.dto.DodealHeartbeatRequest
import com.dodeal.deviceagent.data.api.dto.DodealLocationItem
import com.dodeal.deviceagent.data.http.ApiJson
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Writes the EXACT JSON this app puts on the wire to build/contract/, so it can be replayed against
 * a real server by scripts/verify-against-server.sh.
 *
 * A unit test can only prove the app is self-consistent. Replaying its real bytes against the real
 * Laravel endpoint is what proves the two systems agree — which is precisely what nobody had done,
 * and why command polling was dead for weeks without a single error anywhere.
 */
class PayloadDumpTest {

    private val outputDir = File("build/contract").apply { mkdirs() }

    private fun dump(name: String, json: String) {
        File(outputDir, name).writeText(json)
        assertTrue(json.isNotBlank())
    }

    @Test
    fun `dump heartbeat`() {
        dump(
            "heartbeat.json",
            ApiJson.instance.encodeToString(
                DodealHeartbeatRequest.serializer(),
                DodealHeartbeatRequest(
                    deviceId = "contract-check-device",
                    timestamp = "2026-08-22T11:35:00Z",
                    batteryPercent = 82,
                    charging = false,
                    networkType = "CELLULAR",
                    appVersion = "1.0.0",
                    appVersionCode = 1,
                    romVersion = "test-rom",
                    trackingActive = true,
                    locationPermission = true,
                    backgroundLocation = true,
                    batteryExempt = false,
                    offlineQueueSize = 0,
                ),
            ),
        )
    }

    @Test
    fun `dump a failed command ack`() {
        dump(
            "ack-failed.json",
            ApiJson.instance.encodeToString(
                DodealCommandAckRequest.serializer(),
                DodealCommandAckRequest(
                    commandId = "REPLACED_BY_SCRIPT",
                    success = false,
                    status = "FAILED",
                    receivedAt = "2026-08-22T11:35:00Z",
                    executedAt = "2026-08-22T11:35:01Z",
                    result = "contract check: deliberate failure",
                ),
            ),
        )
    }

    @Test
    fun `dump a mixed batch upload`() {
        dump(
            "locations-batch.json",
            ApiJson.instance.encodeToString(
                DodealBatchLocationsRequest.serializer(),
                DodealBatchLocationsRequest(
                    locations = listOf(
                        // Deliberately uneven: the first point carries no accuracy and the second
                        // does. A batch shaped like this is what a handset sends after a dead spot.
                        DodealLocationItem(
                            latitude = 25.2048,
                            longitude = 55.2708,
                            recordedAt = "2026-08-22T11:40:00Z",
                        ),
                        DodealLocationItem(
                            latitude = 25.2100,
                            longitude = 55.2750,
                            recordedAt = "2026-08-22T11:45:00Z",
                            accuracy = 15f,
                            batteryPercent = 80,
                            provider = "gps",
                        ),
                    ),
                ),
            ),
        )
    }
}
