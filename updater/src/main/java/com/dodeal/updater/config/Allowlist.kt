package com.dodeal.updater.config

/** The ONLY packages Dodeal Updater may ever install. Anything else is rejected. No generic path. */
object Allowlist {
    val PACKAGES = setOf(
        "com.dodeal.deviceagent",   // Dodeal Tracking
        "com.dodeal.callrecorder",  // Dodeal Call Recorder
        "com.phonemaskapp",         // Dodeal Dialer
    )
    fun isAllowed(packageName: String): Boolean = packageName in PACKAGES
}
