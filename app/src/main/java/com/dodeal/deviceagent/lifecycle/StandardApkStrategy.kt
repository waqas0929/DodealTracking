package com.dodeal.deviceagent.lifecycle

import android.content.Context
import android.os.PowerManager
import com.dodeal.deviceagent.diagnostics.AgentLog

/**
 * Behaviour for the plain testable APK. Works within normal Android constraints:
 *  - relies on user-granted permissions
 *  - runs location as a foreground service, started from a foreground context
 *  - cannot silently auto-start the location FGS from the background on Android 12+
 */
class StandardApkStrategy : ProcessLifecycleStrategy {
    override val mode = AgentMode.STANDARD_APK

    override fun onAgentStart(context: Context) {
        AgentLog.i(AgentLog.Category.LIFECYCLE, "Agent started in STANDARD_APK mode")
    }

    override fun requestPersistentExecution(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val exempt = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        if (!exempt) {
            // Non-intrusive: the exemption should be granted once from the UI/settings. On OEM builds
            // (incl. Motorola) this is what keeps the service alive under aggressive Doze.
            AgentLog.i(AgentLog.Category.LIFECYCLE, "Battery-optimisation exemption recommended")
        }
    }

    override fun canRunPrivileged(): Boolean = false
    override fun canAutoStartTracking(): Boolean = false
}
