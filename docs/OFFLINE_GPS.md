# Offline GPS acquisition

## Problem
`FusedLocationProviderClient` is network-assisted. When internet drops it deregisters the GPS request
~30s later (`ProviderRequest[OFF]`), so no fixes arrive offline.

## Fix (only the acquisition layer changed)
The foreground service now has two acquisition paths, switched by a `ConnectivityManager` monitor
that tracks **validated internet** (`NET_CAPABILITY_VALIDATED`):

- **ONLINE** → `FusedLocationProviderClient`, high accuracy (unchanged).
- **OFFLINE** → raw `LocationManager.requestLocationUpdates(GPS_PROVIDER, …)` with a `LocationListener`.
  This is native GNSS, not routed through `gms[fused_location_provider]`, and has **no 30s timeout**.
  The cold-fix window is kept open up to 5 minutes; `NATIVE_GPS_TIMEOUT` is logged (diagnostic only)
  if the first fix is slow, and the request keeps listening.

When connectivity returns, the monitor switches back to fused and the offline-queued points upload
with their **original event IDs** (the encrypted queue is unchanged; dedup by `event_id`).

The FGS lifecycle, boot receiver, permissions and ROM integration were not touched.

## Logs (tag DODEAL_TRACKING)
`NETWORK_AVAILABLE=false` · `OFFLINE_GPS_MODE_ENABLED` · `GPS_PROVIDER_ENABLED=true` ·
`NATIVE_GPS_REQUEST_STARTED` · `NATIVE_GPS_FIRST_FIX_WAITING` ·
`NATIVE_GPS_LOCATION_RECEIVED lat=… lon=… accuracy=…` · `NATIVE_GPS_TIMEOUT` ·
`LOCATION_UPLOAD_QUEUED_OFFLINE` · and provider on every fix: `LOCATION_RECEIVED provider=gps|fused`.

## Offline acceptance test
1. Disable Wi-Fi and mobile data (`ping 8.8.8.8` → unreachable).
2. Walk outside, phone only (no USB), wait up to 5 min.
3. Reconnect USB without enabling internet, `adb logcat -s DODEAL_TRACKING`:
   `NETWORK_AVAILABLE=false → OFFLINE_GPS_MODE_ENABLED → NATIVE_GPS_REQUEST_STARTED →
    NATIVE_GPS_LOCATION_RECEIVED → LOCATION_UPLOAD_QUEUED_OFFLINE`.
4. Restore internet → the same queued event IDs upload successfully (POST /api/addLocation).
