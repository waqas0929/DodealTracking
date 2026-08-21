# Dodeal Device Agent — Milestone 1 (Testable APK)

Secure communication layer between company sales phones and Dodeal CRM. This milestone delivers the
foundation only; location/heartbeat/commands/geofence/meetings/ROM integration follow.

## Milestone 4 — geofencing + meetings (this build)
- Geofences ride in the policy (`geofences: [{id, name, lat, lng, radius_meters}]`). `GeofenceEvaluator`
  is fed each location fix and emits ENTER / EXIT / DWELL transitions, reported via
  `DeviceApi.sendGeofenceEvent` (`EventsRepository`). Deterministic + testable; the native
  GeofencingClient can replace it later behind the same call site.
- Meetings: agent-initiated check-in / check-out from the status screen, stamped with the current
  fix (`CurrentLocationProvider`), sent via `DeviceApi.meetingCheckin` / `meetingCheckout`
  (`MeetingRepository`).
- Status screen now shows geofence count and a meeting check-in/out control.
- Still no Retrofit/DTO outside `data/api` — geofencing and meetings go through `DeviceApi` only.

## Milestone 3 — heartbeat + commands (previous build)
- Heartbeat: `HeartbeatWorker` (WorkManager, 15-min, network-constrained) posts device health
  (battery, network, tracking-active, agent/rom/policy versions, last sync) via
  `DeviceApi.sendHeartbeat`. Built by `HeartbeatFactory` from live state.
- Commands: `CommandPollWorker` calls `DeviceApi.getCommands`, runs each through `CommandExecutor`,
  and acks via `DeviceApi.ackCommand`. Only a FIXED command set is supported — no arbitrary/remote
  code: SYNC_POLICY / SYNC_NOW / UPDATE_SCHEDULE / UPDATE_GEOFENCES (all re-sync policy + re-arm
  tracking), START_WORK_TRACKING, STOP_WORK_TRACKING; anything else acks FAILED.
- `TelemetryScheduler` starts both loops on login / app-start / boot (when enrolled) and cancels
  them on logout. WorkManager's floor is 15 min; a low-latency path (FCM push) can be added behind
  `TelemetryRepository` later without touching it.
- All telemetry goes through `DeviceApi` only — no Retrofit/DTO in the telemetry or work layers.

## Milestone 2 — location engine (previous build)
- Foreground `LocationService` reads the phone GPS via the fused provider.
- Interval is policy-driven: `movingIntervalSeconds` when moving, `stationaryIntervalSeconds` when
  stationary (movement detected from speed/displacement). Accuracy maps to fused `Priority`.
- `WorkingHoursGate` enforces `workingDays` + start/end + timezone — outside hours, no GPS is read.
- Each fix -> `LocationPoint` -> `LocationRepository` -> encrypted offline queue -> batch upload via
  `DeviceApi.uploadLocationBatch` (-> `LocationPayloadMapper` -> Dodeal wire model). Dedup by
  `eventId`; idempotent batches; nothing lost offline, nothing uploaded twice.
- Offline queue: `FileLocationQueue`, AES-256/GCM via a non-exportable AndroidKeyStore key
  (`PayloadCipher`). Swap for Room/SQLCipher behind `LocationQueue` without touching callers.
- `LocationFlushWorker` (WorkManager, network-constrained) drains the queue if the service is gone.
- UI: Start/Stop tracking with runtime permission request; status shows tracking state + pending
  uploads. Boot receiver re-arms tracking and reschedules the flush worker.

Note: on the standard APK, Android 12+ forbids starting a location foreground service from the
background, so tracking resumes from a foreground context (the Start button / a sync). The
`PrivilegedRomStrategy` path is where boot/background auto-start becomes available on the ROM.

## What's in milestone 1
1. Project skeleton (Gradle + version catalog, Compose)
2. Centralized environment/config layer (`config/`)
3. API abstraction + placeholder adapter (`data/api/`)
4. Device registration (`domain/repository/`)
5. Secure token storage (`data/storage/`, EncryptedSharedPreferences)
6. Policy fetch + local cache (`data/storage/FilePolicyStore`)
7. "Dodeal Device Services" status screen (`ui/`)
8. `ProcessLifecycleStrategy` (Standard APK now, Privileged ROM later)

## The layering (enforced)
```
UI / ViewModel
   -> DeviceRepository (domain)
      -> DeviceApi (interface, domain models only)
         -> DodealApiAdapter (the ONLY class that knows endpoints + JSON)
            -> Retrofit / OkHttp
```
No module outside `data/api/adapter` and `data/http` references Retrofit, OkHttp, or a DTO.

Location decoupling:
```
LocationPoint (internal) -> LocationPayloadMapper -> DodealLocationRequest (wire)
```

## Swapping in the real Dodeal API
Everything you touch is inside `data/api/`:
- `dto/` — replace placeholder JSON schemas with the real ones (`@SerialName`).
- `mapper/` — adjust the domain<->DTO field mapping if names differ.
- `adapter/DodealDeviceService.kt` — correct endpoint paths if they change.
Domain models, repository, storage, UI and the location engine remain untouched.

## Config / environments
`config/EnvironmentConfig.kt` holds STAGING/PRODUCTION hosts (placeholders — replace).
Selected at build time via `BuildConfig.AGENT_ENVIRONMENT` (debug=STAGING, release=PRODUCTION).
Base URL, API version, timeouts, logging level, certificate config and feature flags all live in
`ApiConfig` — nothing network-related is hard-coded elsewhere.

## Auth (abstract by design) — per-agent login
Each agent enrolls with `company_id` + `username` + `password` on the login screen. These are
exchanged ONCE at `POST /api/v1/auth/login` for a per-agent bearer token (+ optional refresh token);
the password is never stored. Every later request carries that token, and the server scopes all
data (location, policy, commands) to that agent — so two agents on two devices stay fully isolated.

Flow: `LoginScreen` → `DeviceRepository.login()` → `DeviceApi.login()` → adapter → `/auth/login`.
Token rotation: `DeviceRepository` calls `refreshToken()` automatically when the access token nears
expiry (`REFRESH_SKEW`), using the stored refresh token — no re-login. On 401 the session is cleared
and the UI returns to the login screen.

`AuthProvider` is the seam. Today: `BearerTokenAuthProvider`. Extension points already present:
- signed requests + nonce/timestamp replay protection — `RequestSigner` (currently `NoOp`)
- mTLS / certificate pinning — `CertificateConfig` + `HttpClientFactory`

Auth endpoints (placeholder — map to the real ones in `DodealDeviceService`):
`POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`.

## Build
Open in Android Studio (Koala+), let it sync, run the `app` config on a device/emulator (minSdk 26).
`Register & Sync Policy` exercises registration + policy against the configured host. With the
placeholder host it will report a network failure and fall back to cached policy — that's expected
until the real host is set.

> Note: version numbers in `gradle/libs.versions.toml` are a coherent recent set but were not
> compiled in this environment — sync in Android Studio and bump any that your installed SDK
> resolves differently.

## Security notes
- Tokens: AES-256 EncryptedSharedPreferences; `Authorization` redacted from logs.
- HTTPS only; pinning/mTLS hooks in place.
- No arbitrary remote command execution; commands are a fixed enum (later milestone).


## Enrollment is permanent
The device enrolls once via login so it identifies itself and binds to the API. After that it never logs out on its own: there is no logout button, and a 401 keeps the enrollment and silently refreshes the token instead of returning to the login screen.

## Milestone 5 — privileged ROM integration (this build)
The binary self-detects its context: `PrivilegeDetector` (`FLAG_SYSTEM`) drives `AppContainer` to
pick `PrivilegedRomStrategy` on the system-app build and `StandardApkStrategy` otherwise — nothing
else changes. In ROM mode `canAutoStartTracking()` is true, so location + telemetry come up silently
at boot and on process start (no Android 12+ background-start limit); `DeviceInfoProvider` reads the
real serial and a `ro.dodeal.rom.version` property. The status screen shows the active Runtime Mode.
See `docs/ROM_INTEGRATION.md` for baking it into the Moto G57 image.
