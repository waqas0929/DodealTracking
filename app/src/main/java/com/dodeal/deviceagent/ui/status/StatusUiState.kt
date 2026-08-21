package com.dodeal.deviceagent.ui.status

/** Everything the "Dodeal Device Services" status page displays. */
data class StatusUiState(
    val loading: Boolean = false,
    val deviceId: String = "—",
    val assignedUser: String = "—",
    val registrationStatus: String = "Not logged in",
    val serverConnection: String = "Unknown",
    val trackingStatus: String = "Idle",
    val trackingActive: Boolean = false,
    val pendingUploads: Int = 0,
    val geofenceCount: Int = 0,
    val policyVersion: String = "—",
    val lastSync: String = "Never",
    val agentVersion: String = "—",
    val romVersion: String = "—",
    val runtimeMode: String = "—",
    val message: String? = null,
)
