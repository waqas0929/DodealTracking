package com.dodeal.updater

import android.util.Log

/** Single grep-able tag for the whole update pipeline. Exceptions are logged, never swallowed. */
object UpdaterLog {
    const val TAG = "DODEAL_UPDATER"
    fun i(event: String) = Log.i(TAG, event)
    fun w(event: String, t: Throwable? = null) = Log.w(TAG, event, t)
    fun e(event: String, t: Throwable? = null) = Log.e(TAG, event, t)
}
