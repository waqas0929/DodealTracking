# Boot persistence fix

## Root cause
`BootCompletedReceiver` fired but only scheduled WorkManager / called a policy-gated sync, so no
foreground service was left running. With no active component the OS killed the process as *empty*
~12s later.

## What changed (no ROM changes)
- `BootCompletedReceiver` now **directly** starts the persistent FGS via
  `ContextCompat.startForegroundService(...)` on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` (and logs
  `LOCKED_BOOT`, deferring until unlock). It does **not** gate on a cached policy.
- Service renamed to `LocationForegroundService`. `onStartCommand` calls `startForeground()`
  **immediately**, before any auth/policy/DB/network work, then returns `START_STICKY`.
- Policy load, location requests, geofencing and uploads happen **after** foreground promotion, on a
  background coroutine. If no policy exists yet, it tracks with safe defaults (bootstrap) until the
  CRM policy arrives; once a policy is loaded it is authoritative (can disable / enforce hours).
- The service stays alive while enrolled — UI closed, screen off, idle, Recents removal, reboot.
- `DodealAgentApp.onCreate` also ensures the FGS when the enrolled process starts (boot-independent).
- MainActivity no longer *owns* startup; login just triggers `LocationForegroundService.start`.
- WorkManager is used only for upload/retry — never to keep tracking alive.
- All events logged under tag `DODEAL_TRACKING`; exceptions logged with full stack traces (never
  swallowed).

## Verify (matches the acceptance test)
```
adb shell pidof com.dodeal.deviceagent                       # A–D: returns a PID
adb shell dumpsys activity services com.dodeal.deviceagent   # E: LocationForegroundService present
adb shell dumpsys location | grep -Ei "deviceagent|Dodeal"   # F: active location request
adb logcat -s DODEAL_TRACKING                                # trace the whole startup chain
```
Expected log chain after reboot (no app opened):
`BOOT_RECEIVED → STARTING_LOCATION_FGS → LOCATION_FGS_CREATED → LOCATION_FGS_FOREGROUND →
POLICY_LOADED → LOCATION_REQUEST_STARTED → LOCATION_RECEIVED → LOCATION_UPLOAD_QUEUED`.

Note: an *active location request* (test F) requires tracking to be allowed — either a policy with
`tracking_enabled` within working hours, or the pre-policy bootstrap. If the policy API returns
tracking disabled, you'll see `TRACKING_DISABLED_BY_POLICY` and no location request (by design).
