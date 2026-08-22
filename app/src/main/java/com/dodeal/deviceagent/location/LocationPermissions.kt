package com.dodeal.deviceagent.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Central place for the runtime permissions the location engine needs. */
object LocationPermissions {

    /** Requested up-front from the UI (fine location + notifications on Android 13+). */
    fun foregroundRequest(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    /**
     * Background location, which Android REFUSES to grant in the same dialog as the foreground one
     * (10+) and sends the user to Settings for entirely (11+). So it is a second, separate request
     * made only after foreground has been granted — asking for both at once gets the whole prompt
     * denied, which is why this permission was simply never requested and always read NOT GRANTED.
     *
     * Tracking still works while the foreground service runs; without this the OS is free to cut
     * location off the moment that service is not in the foreground, which on a tracking handset is
     * the case that matters.
     */
    fun backgroundRequest(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            emptyArray()
        }

    fun hasForeground(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackground(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else true
}
