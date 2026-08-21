package com.dodeal.deviceagent.lifecycle

import android.content.Context
import com.dodeal.deviceagent.diagnostics.AgentLog

/**
 * Behaviour for the privileged/system app baked into the Moto G57 custom ROM. The ROM grants
 * persistence, Doze exemption, silent auto-start and privileged device info, so the "please don't
 * kill me" workarounds become no-ops and the agent may start tracking directly at boot.
 *
 * Wired up automatically when PrivilegeDetector reports a system app; no other module changes.
 */
class PrivilegedRomStrategy : ProcessLifecycleStrategy {
    override val mode = AgentMode.PRIVILEGED_ROM

    override fun onAgentStart(context: Context) {
        AgentLog.i(AgentLog.Category.LIFECYCLE, "Agent started in PRIVILEGED_ROM mode")
    }

    override fun requestPersistentExecution(context: Context) {
        AgentLog.i(AgentLog.Category.LIFECYCLE, "Persistent execution granted by ROM")
    }

    override fun canRunPrivileged(): Boolean = true
    override fun canAutoStartTracking(): Boolean = true
}
