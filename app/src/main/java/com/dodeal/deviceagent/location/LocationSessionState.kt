package com.dodeal.deviceagent.location

/** Explicit tracking state machine, surfaced in diagnostics. */
enum class LocationSessionState {
    STOPPED, WAITING_FOR_POLICY, OUTSIDE_WORKING_HOURS, STARTING, TRACKING, OFFLINE_TRACKING, ERROR
}
