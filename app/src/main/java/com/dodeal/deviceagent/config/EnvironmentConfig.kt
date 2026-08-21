package com.dodeal.deviceagent.config

import java.time.Duration

/**
 * Produces the ApiConfig for a given environment. These are PLACEHOLDER hosts — swap in the real
 * Dodeal hosts here (or drive them from a build flavor / remote bootstrap) without touching any
 * other module.
 */
object EnvironmentConfig {

    fun forEnvironment(environment: Environment): ApiConfig = when (environment) {
        Environment.STAGING -> ApiConfig(
            environment = Environment.STAGING,
            // Real DoDeal servers (the app talks to these directly over the internet).
            host = "https://cmsapi.dodealcrm.com",
            apiVersion = "v1",
            cmsBaseUrl = "https://cmsapi.dodealcrm.com/",
            tenantScheme = "https",
            connectTimeout = Duration.ofSeconds(15),
            readTimeout = Duration.ofSeconds(30),
            writeTimeout = Duration.ofSeconds(30),
            loggingLevel = LoggingLevel.BODY,
            certificate = CertificateConfig(enableCertificatePinning = false),
            featureFlags = FeatureFlags(verboseDiagnostics = true),
        )
        Environment.PRODUCTION -> ApiConfig(
            environment = Environment.PRODUCTION,
            host = "https://cmsapi.dodealcrm.com",
            apiVersion = "v1",
            // Real DoDeal CMS host + https tenants.
            cmsBaseUrl = "https://cmsapi.dodealcrm.com/",
            tenantScheme = "https",
            connectTimeout = Duration.ofSeconds(15),
            readTimeout = Duration.ofSeconds(30),
            writeTimeout = Duration.ofSeconds(30),
            loggingLevel = LoggingLevel.BASIC,
            certificate = CertificateConfig(enableCertificatePinning = false),
            featureFlags = FeatureFlags(),
        )
    }

    /**
     * Resolve the build's environment name.
     *
     * Falls back to PRODUCTION, not STAGING. The old default failed the wrong way: STAGING sets
     * LoggingLevel.BODY, so an empty or misspelled AGENT_ENVIRONMENT would have written full
     * request and response bodies — bearer token on every call, password on login — into logcat on
     * whatever build hit it. An unknown environment must land on the quiet, strict one.
     */
    fun fromName(name: String?): Environment =
        Environment.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: Environment.PRODUCTION
}
