package com.dodeal.updater.config

import android.content.Context
import com.dodeal.updater.BuildConfig

enum class Channel { DEVELOPMENT, STAGING, PRODUCTION }

/**
 * Update-service configuration. The update-policy endpoint base URL is per channel; APK download
 * URLs are NEVER hardcoded — they come from the server's update-policy response. The active channel
 * defaults from the build but can be centrally overridden and stored in prefs.
 */
class UpdaterConfig(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("dodeal_updater", Context.MODE_PRIVATE)

    var channel: Channel
        get() = runCatching { Channel.valueOf(prefs.getString(KEY_CHANNEL, defaultChannel().name)!!) }
            .getOrDefault(defaultChannel())
        set(value) { prefs.edit().putString(KEY_CHANNEL, value.name).apply() }

    fun baseUrl(): String = when (channel) {
        Channel.DEVELOPMENT -> "https://cmsapi.dodealcrm.com/"
        Channel.STAGING -> "https://cmsapi.dodealcrm.com/"
        Channel.PRODUCTION -> "https://cmsapi.dodealcrm.com/"
    }

    var pollMinutes: Int
        get() = prefs.getInt(KEY_POLL, DEFAULT_POLL_MIN).coerceIn(MIN_POLL_MIN, MAX_POLL_MIN)
        set(value) { prefs.edit().putInt(KEY_POLL, value.coerceIn(MIN_POLL_MIN, MAX_POLL_MIN)).apply() }

    private fun defaultChannel(): Channel =
        runCatching { Channel.valueOf(BuildConfig.UPDATER_CHANNEL.uppercase()) }.getOrDefault(Channel.PRODUCTION)

    private companion object {
        const val KEY_CHANNEL = "channel"
        const val KEY_POLL = "poll_minutes"
        const val DEFAULT_POLL_MIN = 360
        const val MIN_POLL_MIN = 60
        const val MAX_POLL_MIN = 1440
    }
}
