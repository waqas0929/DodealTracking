package com.dodeal.deviceagent.lifecycle

import android.content.Context

enum class AgentMode { STANDARD_APK, PRIVILEGED_ROM }

/**
 * Abstracts HOW the agent stays alive and how much it is allowed to do. The test APK uses
 * StandardApkStrategy; the Moto G57 ROM build swaps in PrivilegedRomStrategy — the rest of the app
 * (registration, policy, and the location/heartbeat/geofence engines) never changes.
 */
interface ProcessLifecycleStrategy {
    val mode: AgentMode

    /** Called once when the agent process starts (from Application / boot). */
    fun onAgentStart(context: Context)

    /** Ask the platform to keep us running (battery-optimisation exemption, persistence, etc.). */
    fun requestPersistentExecution(context: Context)

    /** Whether privileged/system capabilities (silent boot start, real serial, no Doze) are available. */
    fun canRunPrivileged(): Boolean

    /**
     * Whether we may start the location foreground service from the background / at boot. False on a
     * standard APK (Android 12+ forbids it) — tracking then resumes from a foreground context. True
     * on the ROM build, where the system app is exempt.
     */
    fun canAutoStartTracking(): Boolean
}
