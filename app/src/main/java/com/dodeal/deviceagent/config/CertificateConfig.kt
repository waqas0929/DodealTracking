package com.dodeal.deviceagent.config

/**
 * Certificate / TLS policy. Empty pins == rely on system trust store (fine for the test APK).
 * The fields below are the extension points for pinning and future mutual-TLS; wiring lives
 * in HttpClientFactory so no business module is aware of TLS specifics.
 */
data class CertificateConfig(
    val pinnedSha256: List<String> = emptyList(),
    val enableCertificatePinning: Boolean = false,
    val useMutualTls: Boolean = false, // reserved: client-cert (mTLS) support
)
