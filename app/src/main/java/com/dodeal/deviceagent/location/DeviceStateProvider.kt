package com.dodeal.deviceagent.location

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.dodeal.deviceagent.domain.model.ChargingStatus
import com.dodeal.deviceagent.domain.model.NetworkType

/** Snapshots battery + connectivity to attach to each LocationPoint. */
class DeviceStateProvider(private val context: Context) {

    fun batteryLevel(): Int? {
        val intent = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100 / scale) else null
    }

    fun chargingStatus(): ChargingStatus {
        val intent = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ChargingStatus.UNKNOWN
        return when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargingStatus.CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargingStatus.FULL
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargingStatus.DISCHARGING
            else -> ChargingStatus.UNKNOWN
        }
    }

    fun networkType(): NetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkType.UNKNOWN
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }
    }
}

// --- production helpers (spec §10, §15) ---
fun DeviceStateProvider.isCharging(): Boolean {
    val s = chargingStatus()
    return s == com.dodeal.deviceagent.domain.model.ChargingStatus.CHARGING ||
        s == com.dodeal.deviceagent.domain.model.ChargingStatus.FULL
}
