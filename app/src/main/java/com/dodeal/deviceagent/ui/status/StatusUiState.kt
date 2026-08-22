package com.dodeal.deviceagent.ui.status

/** Everything the "Dodeal Device Services" status page displays. */
data class StatusUiState(
    val loading: Boolean = false,
    val deviceId: String = "—",
    val assignedUser: String = "—",
    val registrationStatus: String = "Not logged in",
    val serverConnection: String = "Unknown",
    val trackingStatus: String = "Idle",
    /** Why it is in that state, in words an operator can act on. */
    val trackingReason: String = "",
    val trackingActive: Boolean = false,
    val pendingUploads: Int = 0,
    val geofenceCount: Int = 0,
    val policyVersion: String = "—",
    val lastSync: String = "Never",
    val agentVersion: String = "—",
    val romVersion: String = "—",
    val runtimeMode: String = "—",
    val message: String? = null,
) {
    /**
     * True when the pause needs a person: a missing permission, a policy nobody can reach, a phone
     * that is not signed in. An expected pause — outside working hours — is not attention-worthy,
     * and colouring it red is how a screen teaches people to ignore red.
     */
    val needsAttention: Boolean
        get() = !trackingActive &&
            (trackingStatus.contains("permission", ignoreCase = true) ||
                trackingStatus.contains("out of date", ignoreCase = true) ||
                trackingStatus.contains("not signed in", ignoreCase = true))
}
