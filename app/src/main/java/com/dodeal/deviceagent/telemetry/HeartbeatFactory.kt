package com.dodeal.deviceagent.telemetry

import android.content.Context
import android.os.PowerManager
import com.dodeal.deviceagent.BuildConfig
import com.dodeal.deviceagent.device.DeviceInfoProvider
import com.dodeal.deviceagent.domain.model.Heartbeat
import com.dodeal.deviceagent.domain.repository.DeviceRepository
import com.dodeal.deviceagent.domain.repository.LocationRepository
import com.dodeal.deviceagent.location.DeviceStateProvider
import com.dodeal.deviceagent.location.LocationController
import com.dodeal.deviceagent.location.LocationPermissions
import com.dodeal.deviceagent.location.isCharging
import java.time.Instant

/** Assembles the periodic heartbeat snapshot from current device + session state (spec §15). */
class HeartbeatFactory(
    private val appContext: Context,
    private val deviceState: DeviceStateProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val deviceRepository: DeviceRepository,
    private val locationRepository: LocationRepository,
    private val locationController: LocationController,
) {
    fun build(): Heartbeat? {
        val creds = deviceRepository.credentials() ?: return null
        val identity = deviceInfoProvider.identity()
        return Heartbeat(
            deviceId = creds.deviceId,
            timestamp = Instant.now(),
            batteryPercent = deviceState.batteryLevel(),
            charging = deviceState.isCharging(),
            networkType = deviceState.networkType(),
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            romVersion = identity.romVersion,
            trackingActive = locationController.shouldTrack(),
            locationPermission = LocationPermissions.hasForeground(appContext),
            backgroundLocation = LocationPermissions.hasBackground(appContext),
            batteryExempt = isBatteryExempt(),
            offlineQueueSize = locationRepository.pendingCount(),
        )
    }

    private fun isBatteryExempt(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }
}
