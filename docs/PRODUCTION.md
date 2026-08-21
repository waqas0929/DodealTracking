# Dodeal Tracking — Production & ROM Integration

App name: **Dodeal Tracking**  ·  Package: `com.dodeal.deviceagent` (unchanged)
Release artifact: **DodealTracking-release.apk**  ·  ROM path: `/product/priv-app/DodealTracking/DodealTracking.apk`

---

## 1. AndroidManifest review

Declared permissions (see `app/src/main/AndroidManifest.xml`):

Network & lifecycle: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
Location: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`.
Privileged (ROM-granted): `READ_PRIVILEGED_PHONE_STATE`.

Components: `MainActivity` (launcher), `LocationService` (`foregroundServiceType="location"`, not exported), `BootCompletedReceiver` (`directBootAware="true"`, handles `BOOT_COMPLETED` + `LOCKED_BOOT_COMPLETED`).
No Accessibility service. No root. No self-granting of permissions.

## 2. Privileged permissions required from the ROM (priv-app allowlist)

Add `/product/etc/permissions/privapp-permissions-com.dodeal.deviceagent.xml`:

```xml
<permissions>
  <privapp-permissions package="com.dodeal.deviceagent">
    <permission name="android.permission.READ_PRIVILEGED_PHONE_STATE"/>
    <permission name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
    <permission name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
  </privapp-permissions>
</permissions>
```

## 3. Runtime / default permissions to pre-grant from the ROM

Grant these at build time (default-permissions / DefaultPermissionGrantPolicy) so no runtime prompt appears:

- `ACCESS_FINE_LOCATION`  (fixed=true)
- `ACCESS_COARSE_LOCATION` (fixed=true)
- `ACCESS_BACKGROUND_LOCATION` (fixed=true)  ← needs explicit default grant; not granted automatically even for system apps
- `POST_NOTIFICATIONS` (fixed=true)

## 4. Battery whitelist

Add the package to the ROM power-save allowlist so Doze can't kill tracking:

- Package: `com.dodeal.deviceagent`
- e.g. `frameworks` `config_powerSaveWhitelist`, or a `sysconfig` `<allow-in-power-save package="com.dodeal.deviceagent"/>`.

The app only *detects and reports* the exemption (`PowerManager.isIgnoringBatteryOptimizations`); it never forces it.

## 5. ROM install path

```
/product/priv-app/DodealTracking/DodealTracking.apk
```

Optional firmware property read by the app and sent in the heartbeat:

```
ro.dodeal.rom.version=<build-id>
```

## 6. API endpoints (base URL from a single config: EnvironmentConfig)

```
POST /api/device/register
POST /api/device/auth/login
POST /api/device/auth/refresh
GET  /api/device/policy
POST /api/device/location
POST /api/device/location/batch
POST /api/device/heartbeat
GET  /api/device/commands
POST /api/device/commands/{command_id}/ack
POST /api/device/geofence/event
POST /api/device/meeting/check-in
POST /api/device/meeting/check-out
```

The device is identified by its bearer token, so `policy` / `commands` don't carry the id in the path.

## 7. Example backend policy JSON

```json
{
  "policy_version": "14",
  "tracking_enabled": true,
  "timezone": "Asia/Dubai",
  "working_hours": {
    "monday":    { "enabled": true, "start": "09:00", "end": "18:00" },
    "tuesday":   { "enabled": true, "start": "09:00", "end": "18:00" }
  },
  "location_interval_seconds": 60,
  "minimum_distance_meters": 20,
  "high_accuracy_enabled": true,
  "heartbeat_interval_seconds": 300,
  "batch_upload_interval_seconds": 900,
  "offline_queue_limit": 10000,
  "geofencing_enabled": true,
  "meeting_checkin_enabled": true,
  "device_commands_enabled": true,
  "geofences": [
    { "geofence_id": "gf_1", "name": "HQ", "latitude": 25.2048, "longitude": 55.2708, "radius_meters": 100, "enabled": true }
  ]
}
```

Safety: `location_interval_seconds` is clamped to 10–3600 on-device (a 0 can never create a runaway loop). A cached policy older than 24h is reported STALE; older than 7 days is treated as tracking-off.

## 8. Example location payload

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "device_id": "dv_test_0001",
  "latitude": 25.2048,
  "longitude": 55.2708,
  "accuracy": 8.2,
  "altitude": 12.5,
  "speed": 3.2,
  "bearing": 180,
  "timestamp": "2026-08-21T11:30:00Z",
  "battery_percent": 82,
  "charging": false,
  "network_type": "CELLULAR",
  "location_provider": "fused",
  "mock_location": false
}
```

Batch: `{ "device_id": "...", "events": [ { ... }, { ... } ] }`. Events are removed from the local queue only after the backend confirms receipt; dedup is by `event_id`.

## 9. Example heartbeat payload

```json
{
  "device_id": "dv_test_0001",
  "timestamp": "2026-08-21T11:35:00Z",
  "battery_percent": 82,
  "charging": false,
  "network_type": "CELLULAR",
  "app_version": "1.0.0",
  "app_version_code": 1,
  "rom_version": "unknown",
  "tracking_active": true,
  "location_permission": true,
  "background_location": true,
  "battery_exempt": true,
  "offline_queue_size": 0
}
```

## 10. Building the signed release APK

This project is the complete Android Studio source. Build the signed release on your machine (a signed APK can't be produced without your keystore):

1. Create a keystore (once):
   `keytool -genkeypair -v -keystore dodeal-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dodeal`
2. Add `keystore.properties` at the project root (do NOT commit):
   ```
   storeFile=dodeal-release.jks
   storePassword=****
   keyAlias=dodeal
   keyPassword=****
   ```
3. Build:
   `./gradlew assembleRelease`
   Output: `app/build/outputs/apk/release/DodealTracking-release.apk`

Release build: `debuggable=false`, PRODUCTION https base URL, no cleartext (debug-only), no test credentials, Authorization header redacted from logs, production log level carries no bodies.

## 11. Acceptance test checklist (spec §50)

- [ ] Reboot device; do NOT open the app → boot init runs (`Dodeal.Boot: boot initialized`).
- [ ] Policy syncs automatically after boot.
- [ ] Inside working hours, tracking starts (`Dodeal.Tracking: tracking started`); location events reach the backend.
- [ ] Disconnect internet → events are encrypted and queued (offline queue grows).
- [ ] Reconnect → queued events upload as a batch; server shows no duplicates (dedup by `event_id`).
- [ ] Outside working hours → active tracking stops (`Dodeal.Tracking: tracking stopped - outside working hours`).
- [ ] Background location keeps working when policy permits.
- [ ] Battery optimization does not kill tracking on the ROM (Diagnostics: Battery Optimization Exempt = YES).
- [ ] Diagnostics screen shows all permissions GRANTED, Privileged App = YES, session state = TRACKING.
- [ ] No Accessibility, no root, no manual permission prompt after ROM permission config.
- [ ] App survives reboot and process restarts.
