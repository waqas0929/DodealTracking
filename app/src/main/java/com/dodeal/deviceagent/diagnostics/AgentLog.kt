package com.dodeal.deviceagent.diagnostics

import android.util.Log

/**
 * Diagnostic logger with the Dodeal.* tag scheme (spec §29). Callers must never pass secrets;
 * tokens and full payloads are never logged.
 */
object AgentLog {
    enum class Category(val tag: String) {
        TRACKING("Dodeal.Tracking"),
        LOCATION("Dodeal.Location"),
        POLICY("Dodeal.Policy"),
        UPLOAD("Dodeal.Upload"),
        BOOT("Dodeal.Boot"),
        SECURITY("Dodeal.Security"),
        COMMANDS("Dodeal.Commands"),
        API("Dodeal.Upload"),
        AUTH("Dodeal.Security"),
        OFFLINE_QUEUE("Dodeal.Upload"),
        LIFECYCLE("Dodeal.Tracking"),
        COMMAND("Dodeal.Commands"),
    }

    fun i(category: Category, message: String) = Log.i(category.tag, message)
    fun w(category: Category, message: String, t: Throwable? = null) = Log.w(category.tag, message, t)
    fun e(category: Category, message: String, t: Throwable? = null) = Log.e(category.tag, message, t)
}
