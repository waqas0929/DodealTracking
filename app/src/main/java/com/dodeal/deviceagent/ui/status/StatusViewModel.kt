package com.dodeal.deviceagent.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dodeal.deviceagent.data.tenant.TenantStore
import com.dodeal.deviceagent.device.DeviceInfoProvider
import com.dodeal.deviceagent.domain.repository.DeviceRepository
import com.dodeal.deviceagent.domain.repository.LocationRepository
import com.dodeal.deviceagent.domain.repository.PolicyOutcome
import com.dodeal.deviceagent.location.LocationController
import com.dodeal.deviceagent.location.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

class StatusViewModel(
    private val repository: DeviceRepository,
    private val locationController: LocationController,
    private val locationRepository: LocationRepository,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val tenantStore: TenantStore,
    private val runtimeMode: String,
    private val sessionUsername: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    init {
        refresh()
        // The screen used to be a snapshot taken once, so a device that connected and synced a
        // second later still read "Last Sync: Never" — which looks exactly like a broken app and is
        // why "Sync now" felt compulsory. It now follows reality on its own.
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                refresh()
            }
        }
    }

    /** Fetch the policy in the background on first open; no tap required. */
    fun syncInBackground() {
        viewModelScope.launch {
            repository.syncPolicy()
            locationController.onPolicyChanged()
            refresh()
        }
    }

    fun refresh() {
        val identity = deviceInfoProvider.identity()
        val creds = repository.credentials()
        val policy = repository.cachedPolicy()
        val decision = locationController.decision()
        val tracking = decision.isActive
        _state.update {
            it.copy(
                deviceId = creds?.deviceId ?: identity.deviceId,
                assignedUser = sessionUsername ?: tenantStore.username() ?: "—",
                registrationStatus = if (repository.isRegistered()) "Logged in" else "Not logged in",
                policyVersion = policy?.policyVersion ?: "—",
                lastSync = repository.lastPolicySync()?.let(::formatInstant) ?: "Never",
                // Derived, not remembered: a sync inside the freshness window means the server was
                // reachable. The field used to say "Unknown" until somebody pressed Sync now, even
                // when the device had just synced by itself.
                serverConnection = repository.lastPolicySync()
                    ?.let { if (it.isAfter(java.time.Instant.now().minusSeconds(CONNECTED_WITHIN_SECONDS))) "Connected" else "Last seen ${formatInstant(it)}" }
                    ?: "Not connected yet",
                agentVersion = identity.agentVersion,
                romVersion = identity.romVersion,
                runtimeMode = runtimeMode,
                trackingActive = tracking,
                pendingUploads = locationRepository.pendingCount(),
                geofenceCount = policy?.geofences?.size ?: 0,
                // One decision, shared with the service. The screen used to print "Enabled by
                // policy" for a handset that was not tracking at all, which answered nothing.
                trackingStatus = when (decision.state) {
                    TrackingState.ACTIVE -> "Active"
                    TrackingState.OUTSIDE_WORKING_HOURS -> "Paused — outside working hours"
                    TrackingState.PERMISSION_MISSING -> "Paused — permission needed"
                    TrackingState.DISABLED_BY_POLICY -> "Turned off by policy"
                    TrackingState.WAITING_FOR_POLICY -> "Waiting for policy"
                    TrackingState.POLICY_STALE -> "Paused — policy out of date"
                    TrackingState.NOT_ENROLLED -> "Not signed in"
                },
                trackingReason = decision.detail,
            )
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val outcome = repository.syncPolicy()
            val (text, connected) = when (outcome) {
                is PolicyOutcome.Synced -> "Policy v${outcome.policy.policyVersion} synced" to true
                is PolicyOutcome.ServedFromCache -> "Using cached policy (${outcome.reason})" to false
                is PolicyOutcome.Unavailable -> "Policy unavailable: ${outcome.reason}" to false
            }
            locationController.sync()
            _state.update {
                it.copy(loading = false, serverConnection = if (connected) "Connected" else "Unreachable")
            }
            refresh()
            _state.update { it.copy(message = text) }
        }
    }

    fun startTracking() { locationController.start(); refresh() }
    fun stopTracking() { locationController.stop(); refresh() }


    private companion object {
        /** Often enough to feel live, rare enough to cost nothing: this only reads local state. */
        const val REFRESH_INTERVAL_MS = 3_000L

        /** A policy fetched within this window means the server is reachable right now. */
        const val CONNECTED_WITHIN_SECONDS = 15L * 60L

        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
    }

    /**
     * Local wall-clock, not a raw UTC instant. The screen showed
     * "2026-08-22T05:21:26.711Z" to an agent standing in Karachi at 10:21.
     */
    private fun formatInstant(instant: java.time.Instant): String =
        TIME_FORMAT.format(instant.atZone(java.time.ZoneId.systemDefault()))

    class Factory(
        private val repository: DeviceRepository,
        private val locationController: LocationController,
        private val locationRepository: LocationRepository,
            private val deviceInfoProvider: DeviceInfoProvider,
        private val tenantStore: TenantStore,
        private val runtimeMode: String,
        private val sessionUsername: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatusViewModel(
                repository, locationController, locationRepository,
                deviceInfoProvider, tenantStore, runtimeMode, sessionUsername,
            ) as T
    }
}
