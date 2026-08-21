package com.dodeal.deviceagent.work

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The heartbeat and command-poll cadence were hard-coded at 15 minutes, so the policy field the
 * server sends — heartbeat_interval_seconds — did nothing at all. These pin the conversion, floor
 * included: the CRM may ask for anything, but Android will not run periodic work faster than 15
 * minutes and the app must not pretend otherwise.
 */
class WorkIntervalsTest {

    private val floor = 15L

    @Test
    fun `a policy interval longer than the floor is honoured`() {
        assertEquals(30L, WorkIntervals.minutesFor(seconds = 1800, fallbackSeconds = 900, floorMinutes = floor))
        assertEquals(60L, WorkIntervals.minutesFor(seconds = 3600, fallbackSeconds = 900, floorMinutes = floor))
    }

    @Test
    fun `anything below the platform floor becomes the floor`() {
        // The server's default is 300s; WorkManager cannot run periodic work that often.
        assertEquals(floor, WorkIntervals.minutesFor(seconds = 300, fallbackSeconds = 900, floorMinutes = floor))
        assertEquals(floor, WorkIntervals.minutesFor(seconds = 1, fallbackSeconds = 900, floorMinutes = floor))
    }

    @Test
    fun `a period is rounded up so it is never shorter than asked`() {
        // 20.5 minutes -> 21, not 20.
        assertEquals(21L, WorkIntervals.minutesFor(seconds = 1230, fallbackSeconds = 900, floorMinutes = floor))
    }

    @Test
    fun `a missing or nonsense interval falls back rather than scheduling madness`() {
        assertEquals(20L, WorkIntervals.minutesFor(seconds = null, fallbackSeconds = 1200, floorMinutes = floor))
        assertEquals(20L, WorkIntervals.minutesFor(seconds = 0, fallbackSeconds = 1200, floorMinutes = floor))
        assertEquals(20L, WorkIntervals.minutesFor(seconds = -5, fallbackSeconds = 1200, floorMinutes = floor))
    }
}
