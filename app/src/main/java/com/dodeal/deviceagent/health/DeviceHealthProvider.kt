package com.dodeal.deviceagent.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.dodeal.deviceagent.BuildConfig
import com.dodeal.deviceagent.device.DeviceInfoProvider
import com.dodeal.deviceagent.device.PrivilegeDetector
import com.dodeal.deviceagent.domain.repository.DeviceRepository
import com.dodeal.deviceagent.domain.repository.LocationRepository
import com.dodeal.deviceagent.location.AgentHealthState
import com.dodeal.deviceagent.location.LocationController
import com.dodeal.deviceagent.location.LocationPermissions

/** Assembles the DeviceHealth snapshot from permissions, power, network, policy and runtime state. */
class DeviceHealthProvider(
    private val appContext: Context,
    private val privilegeDetector: PrivilegeDetector,
    private val deviceRepository: DeviceRepository,
    private val locationRepository: LocationRepository,
    private val locationController: LocationController,
    private val deviceInfoProvider: DeviceInfoProvider,
) {
    fun snapshot(): DeviceHealth {
        val policy = deviceRepository.cachedPolicy()
        val network = isNetworkConnected()
        return DeviceHealth(
            privileged = privilegeDetector.isSystemApp(),
            fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            coarseLocation = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            backgroundLocation = LocationPermissions.hasBackground(appContext),
            privilegedPhoneState = grantedPrivilegedPhoneState(),
            batteryExempt = isBatteryExempt(),
            foregroundServiceReady = LocationPermissions.hasForeground(appContext),
            bootReceiverReady = true,
            networkConnected = network,
            backendConnected = network && deviceRepository.lastPolicySync() != null,
            policySynced = policy != null,
            policyStale = policy?.isStale() ?: true,
            trackingActive = locationController.shouldTrack(),
            sessionState = AgentHealthState.sessionState.value,
            offlineQueueSize = locationRepository.pendingCount(),
            lastLocationAt = AgentHealthState.lastLocationAt,
            lastLocationAccuracy = AgentHealthState.lastLocationAccuracy,
            lastUploadAt = AgentHealthState.lastUploadAt,
            lastError = AgentHealthState.lastError,
            romVersion = deviceInfoProvider.identity().romVersion,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(appContext, p) == PackageManager.PERMISSION_GRANTED

    private fun grantedPrivilegedPhoneState(): Boolean =
        runCatching { granted("android.permission.READ_PRIVILEGED_PHONE_STATE") }.getOrDefault(false)

    private fun isBatteryExempt(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    private fun isNetworkConnected(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
