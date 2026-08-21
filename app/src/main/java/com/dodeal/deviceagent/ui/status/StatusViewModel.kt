package com.dodeal.deviceagent.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dodeal.deviceagent.device.DeviceInfoProvider
import com.dodeal.deviceagent.domain.repository.DeviceRepository
import com.dodeal.deviceagent.domain.repository.LocationRepository
import com.dodeal.deviceagent.domain.repository.PolicyOutcome
import com.dodeal.deviceagent.location.LocationController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

class StatusViewModel(
    private val repository: DeviceRepository,
    private val locationController: LocationController,
    private val locationRepository: LocationRepository,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val runtimeMode: String,
    private val sessionUsername: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val identity = deviceInfoProvider.identity()
        val creds = repository.credentials()
        val policy = repository.cachedPolicy()
        val tracking = locationController.shouldTrack()
        _state.update {
            it.copy(
                deviceId = creds?.deviceId ?: identity.deviceId,
                assignedUser = sessionUsername ?: creds?.deviceId ?: "—",
                registrationStatus = if (repository.isRegistered()) "Logged in" else "Not logged in",
                policyVersion = policy?.policyVersion ?: "—",
                lastSync = repository.lastPolicySync()?.let(::formatInstant) ?: "Never",
                agentVersion = identity.agentVersion,
                romVersion = identity.romVersion,
                runtimeMode = runtimeMode,
                trackingActive = tracking,
                pendingUploads = locationRepository.pendingCount(),
                geofenceCount = policy?.geofences?.size ?: 0,
                trackingStatus = when {
                    tracking -> "Active (within working hours)"
                    policy?.trackingEnabled == true -> "Enabled by policy"
                    else -> "Disabled"
                },
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


    private fun formatInstant(instant: java.time.Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant)

    class Factory(
        private val repository: DeviceRepository,
        private val locationController: LocationController,
        private val locationRepository: LocationRepository,
            private val deviceInfoProvider: DeviceInfoProvider,
        private val runtimeMode: String,
        private val sessionUsername: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatusViewModel(
                repository, locationController, locationRepository,
                deviceInfoProvider, runtimeMode, sessionUsername,
            ) as T
    }
}
