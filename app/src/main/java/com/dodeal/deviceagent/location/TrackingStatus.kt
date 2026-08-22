package com.dodeal.deviceagent.location

import com.dodeal.deviceagent.domain.model.DevicePolicy
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/** Why tracking is, or is not, running right now. */
enum class TrackingState {
    ACTIVE,
    NOT_ENROLLED,
    PERMISSION_MISSING,
    WAITING_FOR_POLICY,
    POLICY_STALE,
    DISABLED_BY_POLICY,
    OUTSIDE_WORKING_HOURS,
}

/** The state plus a sentence a person can act on. */
data class TrackingDecision(val state: TrackingState, val detail: String) {
    val isActive: Boolean get() = state == TrackingState.ACTIVE

    /**
     * "Not yet", as opposed to "no".
     *
     * The service is what fetches the first policy, so stopping it because no policy has arrived
     * cancels the very request that would have produced one — a startup deadlock that showed up on
     * a real handset as POLICY_SYNC_FAILED (Job was cancelled) immediately after SERVICE_DESTROYED.
     * The service must stay alive through this state; the gate still keeps GPS switched off.
     */
    val isAwaitingPolicy: Boolean get() = state == TrackingState.WAITING_FOR_POLICY

    /** Should the location service be running at all (even if it is not reading GPS)? */
    val serviceShouldRun: Boolean get() = isActive || isAwaitingPolicy
}

/**
 * The ONE place that answers "should this handset be tracking, and if not, why not".
 *
 * It existed in three places before, and they disagreed. The service decided whether to acquire,
 * then mapped the refusal to a session state with the test inverted — a handset paused because it
 * was outside working hours reported WAITING_FOR_POLICY — while the status screen printed a third
 * answer of its own ("Enabled by policy") that said nothing about what was actually happening.
 *
 * The reason matters more than the flag: "not tracking" is the one thing an operator will be asked
 * about, and "Disabled" is not an answer.
 *
 * Pure and injectable-clock, so every branch is unit-testable without a device.
 */
object TrackingStatus {

    private val DAY_KEYS = mapOf(
        DayOfWeek.MONDAY to "monday",
        DayOfWeek.TUESDAY to "tuesday",
        DayOfWeek.WEDNESDAY to "wednesday",
        DayOfWeek.THURSDAY to "thursday",
        DayOfWeek.FRIDAY to "friday",
        DayOfWeek.SATURDAY to "saturday",
        DayOfWeek.SUNDAY to "sunday",
    )

    fun decide(
        registered: Boolean,
        hasLocationPermission: Boolean,
        policy: DevicePolicy?,
        now: ZonedDateTime? = null,
    ): TrackingDecision {
        if (!registered) {
            return TrackingDecision(TrackingState.NOT_ENROLLED, "Sign in to start tracking")
        }
        if (!hasLocationPermission) {
            return TrackingDecision(
                TrackingState.PERMISSION_MISSING,
                "Location permission is not granted",
            )
        }
        if (policy == null) {
            return TrackingDecision(TrackingState.WAITING_FOR_POLICY, "Waiting for the first policy")
        }
        if (policy.isHardStale()) {
            return TrackingDecision(
                TrackingState.POLICY_STALE,
                "Policy is too old to trust — check the connection",
            )
        }
        if (!policy.trackingEnabled) {
            return TrackingDecision(TrackingState.DISABLED_BY_POLICY, "Turned off by your company policy")
        }

        val clock = now ?: nowIn(policy.timeZone)

        return if (WorkingHoursGate.isWithinWorkingHours(policy, clock)) {
            TrackingDecision(TrackingState.ACTIVE, todayWindow(policy, clock))
        } else {
            TrackingDecision(TrackingState.OUTSIDE_WORKING_HOURS, todayWindow(policy, clock))
        }
    }

    /**
     * Today's window as the device sees it, e.g. "Today 10:00–19:00 (Asia/Dubai)".
     *
     * The timezone is named on purpose: the working day is judged in the POLICY's zone, and a
     * handset in Karachi reading a Dubai policy is an hour out — which is exactly the confusion
     * this line exists to end.
     */
    fun todayWindow(policy: DevicePolicy, now: ZonedDateTime? = null): String {
        val clock = now ?: nowIn(policy.timeZone)
        val key = DAY_KEYS[clock.dayOfWeek] ?: return "No window for today (${policy.timeZone})"
        val window = policy.workingHours[key]

        return when {
            window == null -> "No window for today (${policy.timeZone})"
            !window.enabled -> "Today is a non-working day (${policy.timeZone})"
            else -> "Today ${window.start}–${window.end} (${policy.timeZone})"
        }
    }

    private fun nowIn(zone: String): ZonedDateTime =
        ZonedDateTime.now(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()))
}
