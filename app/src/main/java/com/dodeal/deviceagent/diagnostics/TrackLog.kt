package com.dodeal.deviceagent.diagnostics

import android.util.Log

/**
 * Dedicated boot/tracking event log under a single grep-able tag (DODEAL_TRACKING). Exceptions are
 * always logged with full stack traces — never swallowed silently.
 */
object TrackLog {
    const val TAG = "DODEAL_TRACKING"
    fun i(event: String) = Log.i(TAG, event)
    fun w(event: String, t: Throwable? = null) = Log.w(TAG, event, t)
    fun e(event: String, t: Throwable? = null) = Log.e(TAG, event, t)
}
