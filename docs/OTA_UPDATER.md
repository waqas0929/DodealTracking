# Dodeal remote OTA architecture

## Components
- **Dodeal Tracking** (`com.dodeal.deviceagent`) — the tracking app. `android:persistent` REMOVED so it
  stays updateable. Base version ships in `/product/priv-app`; newer versions land in `/data/app`.
- **Dodeal Updater** (`com.dodeal.updater`) — separate privileged system app whose ONLY job is secure,
  silent, allowlisted APK deployment. Independent of Tracking, so it can repair a broken Tracking.

## Update flow (Updater)
check (`GET /api/device/update-policy`) → compare versionCode (server > installed, unless
`allow_rollback`) → battery/schedule gate → download (`.part`, resumable, app-private) → verify
(allowlist + package + SHA-256 + signing cert + versionCode + size) → silent install
(PackageInstaller Session API) → confirm installed version → restart target (co-signed broadcast to
Tracking) → report (`POST /api/device/update-result`). Tag: `DODEAL_UPDATER`.

## Security (never bypassed)
- Allowlist: only `com.dodeal.deviceagent`, `com.dodeal.callrecorder`, `com.phonemaskapp`.
- Rejects on any of: package mismatch, unapproved/mismatched signing cert, SHA-256 mismatch, bad
  versionCode, oversize. Logs `UPDATE_SECURITY_CHECK_FAILED`, `PACKAGE_MISMATCH`, `SIGNATURE_MISMATCH`,
  `HASH_MISMATCH`. No generic install path, no remote shell, no arbitrary code.
- Set `PackageVerifier.APPROVED_CERT_SHA256` to the production cert SHA-256 once the key exists. Until
  then it falls back to requiring the update cert == the installed app's cert (still blocks foreign APKs).

## Privileges (ROM)
Silent install needs the Updater to be a priv-app with `android.permission.INSTALL_PACKAGES` on the
firmware privapp allowlist. A normal `/data/app` install cannot install silently — this is by design.
Provide `privapp-permissions-com.dodeal.updater.xml` granting INSTALL_PACKAGES to the Updater.

## Signing (mandatory)
All production releases of each package use ONE approved key per package, reused for the firmware base
and every remote update (Android requires identical signing identity to update in place). Never use a
debug/random key for production. The Updater rejects any update whose cert doesn't match.

## Base + data-app update, factory fallback
Base APK in `/product/priv-app` stays as factory fallback; the Updater installs newer versions to
`/data/app`, which Android makes the active version. Normal updates never remove the `/product` base.

## Data preservation (§18–19)
In-place update only. Never clears Tracking's auth/session, device registration, policy, offline
location queue, or preferences. After a Tracking update the Updater broadcasts
`com.dodeal.permission.RESTART_TRACKING` (signature-protected) → Tracking re-arms its foreground
service per policy.

## System-update test (§34)
1. Flash base v1 to `/product/priv-app/DodealTracking/`.
2. `adb shell pm path com.dodeal.deviceagent` → `/product/priv-app/DodealTracking/DodealTracking.apk`.
3. Deploy v2 via Updater (server update-policy returns v2 metadata).
4. `pm path` now shows a `/data/app/.../base.apk` (active), `/product` base remains.
5. Verify preserved: login, registration, offline queue, permissions; tracking service restarts; no
   confirmation shown.

## CRM / device-management contract (§22)
- `GET /api/device/update-policy` → `{ poll_minutes, apps:[{package_name, version_code, version_name,
  download_url, sha256, size_bytes, mandatory, min_battery_percent, install_after_download,
  allow_rollback, install_after}] }`. Download URLs come from here (never hardcoded).
- `POST /api/device/update-result` → `{ device_id, package_name, old_version_code, new_version_code,
  status, error_code, error_message, download_started_at, install_started_at, completed_at }`.
- UI: Device → Installed Apps (Installed vs Latest, Update Available); buttons Update Now / Schedule /
  Force Mandatory; bulk deploy across selected devices. `mandatory=true` installs automatically when
  battery/schedule conditions are met. `install_after` (epoch ms) schedules install (device timezone).
