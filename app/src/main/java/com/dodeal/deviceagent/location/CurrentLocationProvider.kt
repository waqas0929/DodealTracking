package com.dodeal.deviceagent.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Fetches the most recent fix on demand (used for meeting check-in/out). Null if unavailable. */
class CurrentLocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun lastLocation(): Location? = suspendCancellableCoroutine { cont ->
        runCatching {
            client.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }.onFailure { cont.resume(null) }
    }
}
