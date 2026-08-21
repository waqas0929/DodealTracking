package com.dodeal.deviceagent.location

import com.dodeal.deviceagent.domain.model.DevicePolicy
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Decides whether tracking is allowed right now, using the policy's per-day working windows and its
 * timezone (spec §9, §36). Outside authorized hours the engine reads no GPS at all.
 */
object WorkingHoursGate {

    private val DAY_KEYS = mapOf(
        DayOfWeek.MONDAY to "monday",
        DayOfWeek.TUESDAY to "tuesday",
        DayOfWeek.WEDNESDAY to "wednesday",
        DayOfWeek.THURSDAY to "thursday",
        DayOfWeek.FRIDAY to "friday",
        DayOfWeek.SATURDAY to "saturday",
        DayOfWeek.SUNDAY to "sunday",
    )

    fun isWithinWorkingHours(policy: DevicePolicy, now: ZonedDateTime = nowIn(policy.timeZone)): Boolean {
        if (!policy.trackingEnabled) return false

        val key = DAY_KEYS[now.dayOfWeek] ?: return false
        val window = policy.workingHours[key] ?: return false
        if (!window.enabled) return false

        val start = parse(window.start) ?: return false
        val end = parse(window.end) ?: return false
        val t = now.toLocalTime()

        return if (end.isAfter(start)) {
            !t.isBefore(start) && t.isBefore(end)
        } else {
            // Overnight window (e.g. 22:00 -> 06:00)
            !t.isBefore(start) || t.isBefore(end)
        }
    }

    private fun nowIn(zone: String): ZonedDateTime =
        ZonedDateTime.now(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()))

    private fun parse(hhmm: String): LocalTime? =
        runCatching { LocalTime.parse(hhmm) }.getOrNull()
}
