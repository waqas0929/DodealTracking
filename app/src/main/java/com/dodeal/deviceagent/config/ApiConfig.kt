package com.dodeal.deviceagent.config

import java.time.Duration

/**
 * Single source of truth for everything network-related. No URL, timeout or API version is
 * hard-coded anywhere else in the app — everything routes through here.
 */
data class ApiConfig(
    val environment: Environment,
    val host: String,          // legacy single-host base (kept for fallback / non-tenant calls)
    val apiVersion: String,    // e.g. "v1"
    val cmsBaseUrl: String,    // fixed CMS host for client lookup, e.g. https://cmsapi.dodealcrm.com/
    val tenantScheme: String,  // scheme used to build the per-client tenant base URL (http/https)
    val connectTimeout: Duration,
    val readTimeout: Duration,
    val writeTimeout: Duration,
    val loggingLevel: LoggingLevel,
    val certificate: CertificateConfig,
    val featureFlags: FeatureFlags,
) {
    /** Retrofit base URL = host root. Endpoint paths (api/device/...) live in DodealDeviceService. */
    val versionedBaseUrl: String
        get() = host.trimEnd('/') + "/"
}
