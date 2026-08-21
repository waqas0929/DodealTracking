# Dodeal production signing (per-app identities)

## Rule
Each Dodeal package has its OWN permanent production signing key — NOT one shared key:

| Package                 | App                 | Keystore alias  |
|-------------------------|---------------------|-----------------|
| com.dodeal.deviceagent  | Dodeal Tracking     | dodeal-tracking |
| com.dodeal.updater      | Dodeal Updater      | dodeal-updater  |
| com.dodeal.callrecorder | Dodeal Call Recorder| dodeal-acr      |
| com.phonemaskapp        | Dodeal Dialer       | dodeal-dialer   |

Every future update of a package must be signed with the SAME key as that package's factory/base
APK, or Android refuses the in-place update and Dodeal Updater rejects it. Keys are never shared
between packages and never regenerated.

## Why the earlier APK was "CN=Android Debug"
That SHA-256 (1711f2e8...) is Android's shared debug key, used only for the **debug** build type
(Run button / `assembleDebug`). Production requires building the **release** variant with the
package's own keystore. The Gradle release config now ALWAYS uses the production signing config and
fails fast if it is missing — it never silently falls back to debug.

## One-time: create each app's permanent key
    scripts/generate-dodeal-keystore.sh dodeal-tracking ~/.dodeal-signing
    scripts/generate-dodeal-keystore.sh dodeal-updater  ~/.dodeal-signing
    # later: dodeal-acr, dodeal-dialer
RSA 4096, SHA256withRSA, 10000 days. Real DN (NOT "Android Debug"). Back each up securely.

## Wire the build (keystore.properties, gitignored)
    cp keystore.properties.template keystore.properties
Fill the `tracking.*` and `updater.*` blocks (storeFile paths OUTSIDE the repo, passwords, aliases).
`:app` reads `tracking.*`; `:updater` reads `updater.*`. CI alternative: env vars
DODEAL_TRACKING_STORE_FILE/…/KEY_PASSWORD and DODEAL_UPDATER_STORE_FILE/…/KEY_PASSWORD.

## Build + verify (one command)
    scripts/build-release.sh
Builds both release APKs, runs `apksigner verify --print-certs` on each, fails if any DN is Android
Debug, and prints each app's cert SHA-256.

Manual:
    ./gradlew :app:assembleRelease
    apksigner verify --verbose --print-certs app/build/outputs/apk/release/DodealTracking-release.apk
Acceptance: Signer #1 DN must NOT contain `CN=Android Debug`.

## Pin certificates in Dodeal Updater (per package)
`updater/.../security/ApprovedCertificates.kt`:
    APPROVED_PACKAGES = mapOf(
        "com.dodeal.deviceagent"  to "<TRACKING_CERT_SHA256>",
        "com.dodeal.callrecorder" to "<ACR_CERT_SHA256>",
        "com.phonemaskapp"        to "<DIALER_CERT_SHA256>",
    )
Values are PUBLIC certificate fingerprints only (lowercase hex, no colons) from
`scripts/print-signing-info.sh <keystore> <alias>`. NEVER embed private keys. The Updater reads the
APK's package, looks up that package's pinned cert, and installs only if the APK's signing cert
matches; anything else logs `SIGNATURE_MISMATCH` and is rejected. The Updater's OWN key is not
pinned there (it is the installer, not a target).

## Restart after update (no shared signature permission)
Because keys are per-app, there is no cross-app signature permission. After an in-place update
Android delivers `ACTION_MY_PACKAGE_REPLACED` to the updated app itself; Tracking's boot receiver
handles it and re-arms the foreground service. No co-signing required.

## Keys stay out of everything
Private keystores must never be in Git, the APK, source, CI logs, or (where possible) the firmware
image. Only public certificate fingerprints are embedded (in ApprovedCertificates).

## Manifest guarantee
Dodeal Tracking's production manifest contains NO `android:persistent="true"`, so the /product base
APK stays updateable by a newer /data/app package update.
