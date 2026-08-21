package com.dodeal.deviceagent.location.geofence

import android.location.Location
import com.dodeal.deviceagent.domain.model.GeofenceArea
import com.dodeal.deviceagent.domain.model.GeofenceEvent
import com.dodeal.deviceagent.domain.model.GeofenceTransition
import java.time.Instant
import java.util.UUID

/**
 * Stream-based geofence detector: fed each location fix, it tracks inside/outside state per area and
 * emits ENTER / EXIT / DWELL transitions. Deterministic and testable; the native GeofencingClient
 * can be swapped in later for OS-level efficiency without changing callers.
 */
class GeofenceEvaluator(dwellMillis: Long = DEFAULT_DWELL_MS) {

    private val dwell = dwellMillis
    private var areas: List<GeofenceArea> = emptyList()
    private val inside = HashMap<String, Boolean>()
    private val enteredAt = HashMap<String, Long>()
    private val dwellFired = HashSet<String>()

    fun updateAreas(newAreas: List<GeofenceArea>) {
        areas = newAreas
        val ids = newAreas.map { it.id }.toHashSet()
        inside.keys.retainAll(ids)
        enteredAt.keys.retainAll(ids)
        dwellFired.retainAll(ids)
    }

    /** Returns any transitions triggered by this fix. */
    fun onLocation(location: Location, deviceId: String, now: Long = System.currentTimeMillis()): List<GeofenceEvent> {
        if (areas.isEmpty()) return emptyList()
        val events = ArrayList<GeofenceEvent>()

        for (area in areas) {
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, area.latitude, area.longitude, results)
            val isInside = results[0] <= area.radiusMeters
            val wasInside = inside[area.id] ?: false

            when {
                isInside && !wasInside -> {
                    enteredAt[area.id] = now
                    dwellFired.remove(area.id)
                    events += event(area, deviceId, GeofenceTransition.ENTER, location)
                }
                !isInside && wasInside -> {
                    enteredAt.remove(area.id)
                    dwellFired.remove(area.id)
                    events += event(area, deviceId, GeofenceTransition.EXIT, location)
                }
                isInside && wasInside -> {
                    val since = enteredAt[area.id]
                    if (since != null && now - since >= dwell && area.id !in dwellFired) {
                        dwellFired += area.id
                        events += event(area, deviceId, GeofenceTransition.DWELL, location)
                    }
                }
            }
            inside[area.id] = isInside
        }
        return events
    }

    private fun event(area: GeofenceArea, deviceId: String, t: GeofenceTransition, loc: Location) =
        GeofenceEvent(
            eventId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            geofenceId = area.id,
            transition = t,
            timestamp = Instant.now(),
            latitude = loc.latitude,
            longitude = loc.longitude,
        )

    private companion object {
        const val DEFAULT_DWELL_MS = 5 * 60_000L
    }
}
