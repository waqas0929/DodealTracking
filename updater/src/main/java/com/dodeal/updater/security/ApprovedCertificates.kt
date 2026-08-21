package com.dodeal.updater.security

/**
 * Strict package -> approved production certificate (SHA-256) allowlist.
 *
 * Each Dodeal app has its OWN permanent production signing identity. An update for a given package
 * is accepted ONLY if its signing certificate SHA-256 matches the entry pinned here for that exact
 * package. There is no global/shared certificate.
 *
 * These are PUBLIC certificate fingerprints only — never private keys. Fill each value from:
 *   scripts/print-signing-info.sh <that-app-keystore.jks> <that-app-alias>
 * (lowercase hex, no colons). Leave a value blank ONLY during early bring-up: while blank, the
 * verifier falls back to requiring the update cert to equal the currently-installed app's cert,
 * which still blocks foreign APKs but does not pin the identity. Pin every package for production.
 */
object ApprovedCertificates {

    val APPROVED_PACKAGES: Map<String, String> = mapOf(
        "com.dodeal.deviceagent" to "",  // <TRACKING_CERT_SHA256>
        "com.dodeal.callrecorder" to "", // <ACR_CERT_SHA256>
        "com.phonemaskapp" to "",        // <DIALER_CERT_SHA256>
    )

    /** True if this package is managed by the updater at all. */
    fun isManaged(packageName: String): Boolean = APPROVED_PACKAGES.containsKey(packageName)

    /** The pinned cert SHA-256 for a package (lowercase, no colons), or null/blank if not pinned. */
    fun expectedCert(packageName: String): String? = APPROVED_PACKAGES[packageName]?.takeIf { it.isNotBlank() }
}
