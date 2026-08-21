# Location access from a boot-started foreground service

## The restriction (Android 12+, tightened in 14/15/16)
When a foreground service is started while the app is in the background (e.g. from
`BOOT_COMPLETED`), Android runs the service but withholds the **while-in-use** capabilities
(location / camera / mic). You see:

`Foreground service started from background can not have location/camera/microphone access`

Key point: this is independent of `ACCESS_BACKGROUND_LOCATION` (even when granted) and it does **not**
throw `SecurityException` — `requestLocationUpdates()` succeeds but no fixes are delivered, and
`dumpsys location` shows no active request. So "service is alive" is not enough.

## App-side fix (this build)
- `android:persistent="true"` on `<application>`. This is honoured **only for a system/privileged
  app** (our `/product/priv-app` build) and is ignored for a normal APK. A persistent process runs at
  `PROCESS_STATE_PERSISTENT`, so its FGS starts are not treated as "from background" and the
  while-in-use location capability is granted. This is the primary lever for a headless, boot-started
  device.
- Service keeps `android:foregroundServiceType="location"`; all six location permissions declared.
- `requestLocationUpdates()` is wrapped with explicit logging and exception handling (never
  swallowed). New `DODEAL_TRACKING` events:
  `LOCATION_PERMISSION_FINE`, `LOCATION_PERMISSION_BACKGROUND`, `LOCATION_SERVICE_STARTED`,
  `LOCATION_REQUEST_ATTEMPT`, `LOCATION_REQUEST_SUCCESS`, `LOCATION_REQUEST_SECURITY_EXCEPTION`,
  `LOCATION_REQUEST_FAILURE`, `LOCATION_RECEIVED`.
- Watchdog: since the restriction is silent, if no fix arrives ~45s after a successful request we log
  `LOCATION_CAPABILITY_SUSPECTED_RESTRICTED` so the condition is diagnosable from logcat.

## ROM-side exemption (the guaranteed grant, if `persistent` isn't sufficient)
The while-in-use FGS restriction is ultimately enforced by the system, so on some builds the
definitive exemption is granted ROM-side (no app change needed):

- Grant the app-op **`OP_SYSTEM_EXEMPT_FROM_FGS_BG_START_RESTRICTION`** (Android 13+) to
  `com.dodeal.deviceagent` (e.g. via `appops set com.dodeal.deviceagent SYSTEM_EXEMPT_FROM_FGS_BG_START_RESTRICTION allow`,
  or the equivalent default app-ops config baked into the image), and/or
- keep the package on the power/allowlist and priv-app permission allowlist you already ship.

`android:persistent="true"` plus the priv-app status normally suffices; the app-op above is the
belt-and-braces guarantee.

## Verify after reboot (no app opened)
```
adb shell pidof com.dodeal.deviceagent                      # PID exists
adb shell dumpsys activity services com.dodeal.deviceagent  # LocationForegroundService, isForeground=true
adb shell dumpsys location | grep -Ei "deviceagent|Dodeal"  # active request present
adb logcat -s DODEAL_TRACKING
```
Expected: `LOCATION_PERMISSION_FINE=true` → `LOCATION_REQUEST_ATTEMPT` → `LOCATION_REQUEST_SUCCESS` →
`LOCATION_RECEIVED` (continuing with the screen off). If you instead see
`LOCATION_CAPABILITY_SUSPECTED_RESTRICTED`, apply the ROM-side app-op exemption above.
