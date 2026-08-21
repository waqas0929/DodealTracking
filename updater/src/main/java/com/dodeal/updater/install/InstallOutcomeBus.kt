package com.dodeal.updater.install

import kotlinx.coroutines.flow.MutableSharedFlow

/** Bridges the InstallResultReceiver callback back to the awaiting orchestrator coroutine. */
data class InstallOutcome(val packageName: String?, val success: Boolean, val code: String, val message: String?)

object InstallOutcomeBus {
    val flow = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 8)
    fun publish(pkg: String?, success: Boolean, code: String, message: String?) {
        flow.tryEmit(InstallOutcome(pkg, success, code, message))
    }
}
