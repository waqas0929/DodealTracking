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
        // Dodeal Tracking, signed 2026-08-22 with the permanent dodeal-tracking key
        // (CN=Dodeal Technologies …, RSA 4096). Verified from the release APK itself with
        // `apksigner verify --print-certs`, not copied from a note.
        "com.dodeal.deviceagent" to "5de94c803b8a17579faa06db265ef96b2396ac31c2f9372ccd34e1a999532c99",

        // DELIBERATELY BLANK. The shipped APKs for both of these are signed
        // `CN=Android Debug` — the platform's shared debug key, which is per-machine and not an
        // identity anyone should pin. Pinning it here would bless the debug key as their
        // production identity for ever. Each needs its own permanent key first
        // (scripts/generate-dodeal-keystore.sh dodeal-acr / dodeal-dialer), a release build with
        // it, and then its fingerprint pinned here. Until then the verifier falls back to
        // "must match the currently-installed cert", which still blocks a foreign APK.
        "com.dodeal.callrecorder" to "", // needs a real dodeal-acr key
        "com.phonemaskapp" to "",        // needs a real dodeal-dialer key
    )

    /** True if this package is managed by the updater at all. */
    fun isManaged(packageName: String): Boolean = APPROVED_PACKAGES.containsKey(packageName)

    /** The pinned cert SHA-256 for a package (lowercase, no colons), or null/blank if not pinned. */
    fun expectedCert(packageName: String): String? = APPROVED_PACKAGES[packageName]?.takeIf { it.isNotBlank() }
}
