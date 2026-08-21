package com.dodeal.deviceagent.domain.model

import kotlinx.serialization.Serializable

/** A circular geofence pushed down in the policy (customer site, office, no-go zone, etc.). */
@Serializable
data class GeofenceArea(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
)
