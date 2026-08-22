package com.dodeal.deviceagent.location

import com.dodeal.deviceagent.domain.model.DevicePolicy
import com.dodeal.deviceagent.domain.model.WorkingWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Why tracking is or is not running — the question an operator actually asks.
 *
 * The case that matters most is the one that was wrong on a real handset: a Galaxy S23 in Karachi
 * reading a policy whose working day is 10:00–19:00 Asia/Dubai. At 10:01 Karachi it is 09:01 in
 * Dubai, so the device correctly refused to track — and then reported WAITING_FOR_POLICY, because
 * the state was only set to OUTSIDE_WORKING_HOURS when tracking was DISABLED, which is the one
 * situation in which working hours are irrelevant.
 */
class TrackingStatusTest {

    private fun policy(
        enabled: Boolean = true,
        zone: String = "Asia/Dubai",
        hours: Map<String, WorkingWindow> = weekOf("10:00", "19:00"),
        fetchedAt: Long = System.currentTimeMillis(),
    ) = DevicePolicy(
        policyVersion = "v1",
        trackingEnabled = enabled,
        timeZone = zone,
        workingHours = hours,
        locationIntervalSeconds = 60,
        minimumDistanceMeters = 0,
        highAccuracyEnabled = true,
        heartbeatIntervalSeconds = 300,
        batchUploadIntervalSeconds = 900,
        offlineQueueLimit = 500,
        geofencingEnabled = false,
        meetingCheckinEnabled = false,
        deviceCommandsEnabled = true,
        fetchedAtEpochMs = fetchedAt,
    )

    private fun weekOf(start: String, end: String, enabled: Boolean = true) =
        listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
            .associateWith { WorkingWindow(enabled = enabled, start = start, end = end) }

    /** A Saturday. 10:01 in Karachi is 09:01 in Dubai — before a 10:00 Dubai start. */
    private val karachiJustBeforeStart: ZonedDateTime =
        ZonedDateTime.of(2026, 8, 22, 10, 1, 0, 0, ZoneId.of("Asia/Karachi"))
            .withZoneSameInstant(ZoneId.of("Asia/Dubai"))

    private val insideTheDay: ZonedDateTime =
        ZonedDateTime.of(2026, 8, 22, 12, 0, 0, 0, ZoneId.of("Asia/Dubai"))

    @Test
    fun `outside working hours says exactly that, not waiting for policy`() {
        val decision = TrackingStatus.decide(
            registered = true, hasLocationPermission = true,
            policy = policy(), now = karachiJustBeforeStart,
        )

        assertEquals(TrackingState.OUTSIDE_WORKING_HOURS, decision.state)
        assertTrue("the reason must name the window and its timezone", decision.detail.contains("10:00"))
        assertTrue(decision.detail.contains("Asia/Dubai"))
    }

    @Test
    fun `inside the window it tracks`() {
        val decision = TrackingStatus.decide(
            registered = true, hasLocationPermission = true,
            policy = policy(), now = insideTheDay,
        )

        assertEquals(TrackingState.ACTIVE, decision.state)
        assertTrue(decision.isActive)
    }

    @Test
    fun `every blocking condition is reported as itself`() {
        assertEquals(
            TrackingState.NOT_ENROLLED,
            TrackingStatus.decide(false, true, policy(), insideTheDay).state,
        )
        assertEquals(
            TrackingState.PERMISSION_MISSING,
            TrackingStatus.decide(true, false, policy(), insideTheDay).state,
        )
        assertEquals(
            TrackingState.WAITING_FOR_POLICY,
            TrackingStatus.decide(true, true, null, insideTheDay).state,
        )
        assertEquals(
            TrackingState.DISABLED_BY_POLICY,
            TrackingStatus.decide(true, true, policy(enabled = false), insideTheDay).state,
        )
    }

    @Test
    fun `a policy too old to trust stops tracking rather than guessing`() {
        val ancient = policy(fetchedAt = 0L)

        assertEquals(
            TrackingState.POLICY_STALE,
            TrackingStatus.decide(true, true, ancient, insideTheDay).state,
        )
    }

    @Test
    fun `permission is checked before the policy, so the fixable thing is named first`() {
        // A handset with no permission AND no policy should be told about the permission: it is the
        // one of the two a person standing in front of the phone can do something about.
        assertEquals(
            TrackingState.PERMISSION_MISSING,
            TrackingStatus.decide(true, false, null, insideTheDay).state,
        )
    }

    @Test
    fun `a non-working day is named as one`() {
        val sundayOff = policy(
            hours = weekOf("10:00", "19:00") + ("saturday" to WorkingWindow(enabled = false)),
        )

        val decision = TrackingStatus.decide(true, true, sundayOff, insideTheDay)

        assertEquals(TrackingState.OUTSIDE_WORKING_HOURS, decision.state)
        assertTrue(decision.detail.contains("non-working day"))
    }
}
