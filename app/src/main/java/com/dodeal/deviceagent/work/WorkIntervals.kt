package com.dodeal.deviceagent.work

/**
 * How a policy interval in SECONDS becomes a WorkManager period in MINUTES.
 *
 * Deliberately pure — no Android imports — so the arithmetic is unit-testable without a device.
 * The floor is passed in rather than written here, because the only correct value is WorkManager's
 * own PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS and duplicating it invites the two to drift.
 *
 * The rounding rule matters: a period is rounded UP, never down. Rounding 300s (5 min) down to zero
 * or up to the floor is the difference between "the CRM asked for five minutes and got fifteen" —
 * which is honest, because Android will not schedule faster — and a period the OS silently rejects.
 */
object WorkIntervals {

    /**
     * @param seconds       what the policy asked for; null or <= 0 falls back
     * @param fallbackSeconds value used when the policy has nothing to say
     * @param floorMinutes  the platform minimum (WorkManager: 15)
     */
    fun minutesFor(seconds: Int?, fallbackSeconds: Int, floorMinutes: Long): Long {
        val wanted = seconds?.takeIf { it > 0 } ?: fallbackSeconds
        val minutes = (wanted + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE   // round up
        return maxOf(minutes.toLong(), floorMinutes)
    }

    private const val SECONDS_PER_MINUTE = 60
}
