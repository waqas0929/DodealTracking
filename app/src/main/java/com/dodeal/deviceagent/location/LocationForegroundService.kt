package com.dodeal.deviceagent.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dodeal.deviceagent.DodealAgentApp
import com.dodeal.deviceagent.R
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.diagnostics.TrackLog
import com.dodeal.deviceagent.domain.model.DevicePolicy
import com.dodeal.deviceagent.location.geofence.GeofenceEvaluator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persistent, boot-independent foreground service that owns continuous location tracking.
 *
 * Acquisition is dual-path:
 *  - ONLINE  (validated internet): FusedLocationProviderClient, high accuracy.
 *  - OFFLINE (no validated internet): raw LocationManager.GPS_PROVIDER (native GNSS). Fused is not
 *    used offline because it deregisters the GPS request ~30s after network loss; the native request
 *    stays active with no such timeout (cold-fix window up to several minutes).
 *
 * The foreground-service lifecycle (startForeground first, START_STICKY, boot start) is unchanged.
 */
class LocationForegroundService : Service() {

    private enum class Mode { NONE, FUSED, NATIVE_GPS }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var connectivity: ConnectivityManager
    private lateinit var pointFactory: LocationPointFactory
    private val geofenceEvaluator = GeofenceEvaluator()

    private val app get() = application as DodealAgentApp
    private val handler = Handler(Looper.getMainLooper())

    private var mode = Mode.NONE
    private var currentIntervalSec = -1
    private var foregroundStarted = false
    // Service-level one-time setup (foreground + gate loop + first policy bootstrap). This being
    // true must NEVER, by itself, be taken to mean the location engine is running.
    private var serviceInitialized = false
    // True ONLY while an actual requestLocationUpdates() call is live. Decoupled from the service
    // lifecycle so a re-check can (re)start the engine even after the service is initialized.
    @Volatile private var locationRequestActive = false

    @Volatile private var online = true
    @Volatile private var locationRequestedAt = 0L
    @Volatile private var lastFixAt = 0L
    @Volatile private var nativeStartedAt = 0L
    @Volatile private var nativeFirstFix = false

    // --- Fused (online) callback ---
    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onNewLocation(it, "fused") }
        }
    }

    // --- Native GPS (offline) listener ---
    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!nativeFirstFix) {
                nativeFirstFix = true
                TrackLog.i("NATIVE_GPS_LOCATION_RECEIVED lat=${location.latitude} lon=${location.longitude} accuracy=${if (location.hasAccuracy()) location.accuracy else "?"}")
            }
            onNewLocation(location, "gps")
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("legacy") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    // --- Network monitor: validated internet available or not ---
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = setOnline(true)
        override fun onLost(network: Network) = setOnline(false)
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            setOnline(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
    }

    private val gateTick = object : Runnable {
        override fun run() {
            runCatching { evaluateGate() }.onFailure { TrackLog.e("GATE_EVAL_FAILED", it) }
            handler.postDelayed(this, GATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        pointFactory = LocationPointFactory(DeviceStateProvider(this))
        online = hasValidatedInternet()
        registerNetworkMonitor()
        TrackLog.i("LOCATION_FGS_CREATED")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1) Go foreground FIRST, always, before any heavy work.
        ensureForeground()
        TrackLog.i("LOCATION_SERVICE_STARTED startRequested=true")

        // 2) One-time SERVICE setup (foreground gate loop + first policy bootstrap). Duplicate start
        //    requests (MainActivity + App + boot + login all call TrackingServiceStarter) skip only
        //    this one-time setup — they must NOT skip the tracking-engine check below.
        if (!serviceInitialized) {
            serviceInitialized = true
            scope.launch { runCatching { bootstrap() }.onFailure { TrackLog.e("STARTUP_FAILED", it) } }
            handler.removeCallbacks(gateTick)
            handler.postDelayed(gateTick, GATE_INTERVAL_MS)
        } else {
            // Only the duplicate SERVICE setup is skipped — never the engine check.
            TrackLog.i("DUPLICATE_START_IGNORED service setup already done")
        }

        // 3) ALWAYS (re)check the tracking engine on every start/resume. If a previous start failed
        //    to establish a location request (e.g. permission was missing and is now granted, or
        //    policy just enabled tracking), this is what retries it.
        handler.post { runCatching { evaluateGate() }.onFailure { TrackLog.e("GATE_EVAL_FAILED", it) } }

        return START_STICKY
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        try {
            val channelId = ensureChannel()
            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.tracking_notification_title))
                .setContentText(getString(R.string.tracking_notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setNumber(0)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                .setShowWhen(false)
                .build()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            foregroundStarted = true
            TrackLog.i("LOCATION_FGS_FOREGROUND")
        } catch (t: Throwable) {
            TrackLog.e("LOCATION_FGS_FOREGROUND_FAILED", t)
        }
    }

    private suspend fun bootstrap() {
        if (!app.container.deviceRepository.isRegistered()) {
            TrackLog.i("NOT_ENROLLED_STOPPING")
            stopSelfSafely("not_enrolled")
            return
        }
        val cached = app.container.deviceRepository.cachedPolicy()
        if (cached == null || cached.isStale()) {
            runCatching { app.container.deviceRepository.syncPolicy() }
                .onFailure { TrackLog.e("POLICY_SYNC_FAILED", it) }
            app.container.deviceRepository.cachedPolicy()?.let {
                TrackLog.i("POLICY_LOADED v=${it.policyVersion} tracking=${it.trackingEnabled}")
            }
        } else {
            TrackLog.i("POLICY_LOADED v=${cached.policyVersion} tracking=${cached.trackingEnabled}")
        }
        // All acquisition changes happen on the main thread so start/stop can never race.
        handler.post { runCatching { evaluateGate() }.onFailure { TrackLog.e("GATE_EVAL_FAILED", it) } }
    }

    private fun policy(): DevicePolicy? = app.container.deviceRepository.cachedPolicy()

    private fun shouldTrackNow(policy: DevicePolicy?): Boolean {
        if (!app.container.deviceRepository.isRegistered()) return false
        if (policy == null) return true // bootstrap until CRM policy arrives
        if (!policy.trackingEnabled) return false
        if (policy.isHardStale()) return false
        return WorkingHoursGate.isWithinWorkingHours(policy)
    }

    private fun evaluateGate() {
        val policy = policy()
        if (shouldTrackNow(policy)) {
            startTrackingIfNeeded(policy)
        } else {
            if (mode != Mode.NONE) TrackLog.i("TRACKING_DISABLED_BY_POLICY")
            AgentHealthState.sessionState.value =
                if (policy?.trackingEnabled == false) LocationSessionState.OUTSIDE_WORKING_HOURS
                else LocationSessionState.WAITING_FOR_POLICY
            stopAllUpdates()
            scope.launch { runCatching { app.container.locationRepository.flush() } }
        }
    }

    // --- Tracking-engine lifecycle ----------------------------------------------------------

    /**
     * Idempotent tracking-engine check. Always verifies the REAL state (locationRequestActive)
     * rather than assuming the engine is running because the service was initialized. Safe to call
     * on every start/resume/policy refresh; it will (re)start the location request if it isn't live.
     */
    private fun startTrackingIfNeeded(policy: DevicePolicy?) {
        TrackLog.i("TRACKING_ENGINE_CHECK active=$locationRequestActive mode=$mode")
        if (locationRequestActive && mode != Mode.NONE) {
            TrackLog.i("TRACKING_ENGINE_ALREADY_ACTIVE")
            // Still pass through acquisition so an interval change or online<->offline switch is
            // applied; startFused/startNativeGps dedupe when nothing changed.
            startAcquisition(policy)
            return
        }
        TrackLog.i("TRACKING_ENGINE_STARTING")
        startAcquisition(policy)
    }

    // --- Acquisition mode selection ---------------------------------------------------------

    private fun startAcquisition(policy: DevicePolicy?) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val background = LocationPermissions.hasBackground(this)
        TrackLog.i("LOCATION_PERMISSION_FINE=$fine")
        TrackLog.i("LOCATION_PERMISSION_BACKGROUND=$background")
        if (!fine) {
            // Do NOT mark the engine active — a later start/resume/policy refresh (after the
            // permission is granted) will retry via startTrackingIfNeeded().
            locationRequestActive = false
            TrackLog.w("LOCATION_PERMISSION_MISSING")
            TrackLog.w("TRACKING_ENGINE_START_FAILED reason=permission_missing")
            AgentHealthState.sessionState.value = LocationSessionState.ERROR
            AgentHealthState.onError("location permission missing")
            return
        }
        if (online) startFused(policy) else startNativeGps(policy)
    }

    // --- Online: fused --------------------------------------------------------------------

    private fun startFused(policy: DevicePolicy?) {
        val intervalSec = policy?.effectiveIntervalSeconds ?: DEFAULT_INTERVAL_SEC
        if (mode == Mode.FUSED && intervalSec == currentIntervalSec) {
            locationRequestActive = true // already live with the right settings
            AgentHealthState.sessionState.value = LocationSessionState.TRACKING
            return
        }
        stopNativeGps()
        AgentHealthState.sessionState.value = LocationSessionState.STARTING
        val highAccuracy = policy?.highAccuracyEnabled ?: true
        val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val minDistance = (policy?.minimumDistanceMeters ?: 0).toFloat()

        val request = LocationRequest.Builder(priority, intervalSec * 1000L)
            .setMinUpdateIntervalMillis(intervalSec * 1000L / 2)
            .setMinUpdateDistanceMeters(minDistance)
            .build()

        try {
            TrackLog.i("LOCATION_REQUEST_ATTEMPT provider=fused interval=${intervalSec}s highAccuracy=$highAccuracy")
            fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            mode = Mode.FUSED
            currentIntervalSec = intervalSec
            locationRequestedAt = System.currentTimeMillis()
            locationRequestActive = true // set ONLY after requestLocationUpdates() succeeded
            AgentHealthState.sessionState.value = LocationSessionState.TRACKING
            TrackLog.i("LOCATION_REQUEST_SUCCESS")
            TrackLog.i("LOCATION_REQUEST_STARTED provider=fused interval=${intervalSec}s")
            handler.removeCallbacks(fixWatchdog)
            handler.postDelayed(fixWatchdog, FIX_WATCHDOG_MS)
        } catch (e: SecurityException) {
            mode = Mode.NONE
            locationRequestActive = false
            AgentHealthState.sessionState.value = LocationSessionState.ERROR
            AgentHealthState.onError("SecurityException: ${e.message}")
            TrackLog.e("LOCATION_REQUEST_SECURITY_EXCEPTION", e)
            TrackLog.w("TRACKING_ENGINE_START_FAILED reason=security_exception")
        } catch (e: Exception) {
            mode = Mode.NONE
            locationRequestActive = false
            AgentHealthState.sessionState.value = LocationSessionState.ERROR
            AgentHealthState.onError(e.message)
            TrackLog.e("LOCATION_REQUEST_FAILURE", e)
            TrackLog.w("TRACKING_ENGINE_START_FAILED reason=exception")
        }
    }

    private fun stopFused() {
        if (mode != Mode.FUSED) return
        runCatching { fusedClient.removeLocationUpdates(fusedCallback) }
            .onFailure { TrackLog.e("REMOVE_UPDATES_FAILED", it) }
        locationRequestActive = false
        handler.removeCallbacks(fixWatchdog)
    }

    // --- Offline: native GNSS via LocationManager -----------------------------------------

    private fun startNativeGps(policy: DevicePolicy?) {
        TrackLog.i("NETWORK_AVAILABLE=false")
        TrackLog.i("OFFLINE_GPS_MODE_ENABLED")
        val gpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        TrackLog.i("GPS_PROVIDER_ENABLED=$gpsEnabled")

        val intervalSec = policy?.effectiveIntervalSeconds ?: DEFAULT_INTERVAL_SEC
        if (mode == Mode.NATIVE_GPS && intervalSec == currentIntervalSec) {
            locationRequestActive = true // already live with the right settings
            AgentHealthState.sessionState.value = LocationSessionState.OFFLINE_TRACKING
            return
        }
        stopFused()
        AgentHealthState.sessionState.value = LocationSessionState.STARTING
        val minDistance = (policy?.minimumDistanceMeters ?: 0).toFloat()

        try {
            TrackLog.i("LOCATION_REQUEST_ATTEMPT provider=gps interval=${intervalSec}s")
            // Raw GNSS request — NOT routed through fused. No 30s network-assisted timeout.
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                intervalSec * 1000L,   // minTime (ms)
                minDistance,           // minDistance (m)
                gpsListener,
                Looper.getMainLooper(),
            )
            mode = Mode.NATIVE_GPS
            currentIntervalSec = intervalSec
            locationRequestedAt = System.currentTimeMillis()
            nativeStartedAt = System.currentTimeMillis()
            nativeFirstFix = false
            locationRequestActive = true // set ONLY after requestLocationUpdates() succeeded
            AgentHealthState.sessionState.value = LocationSessionState.OFFLINE_TRACKING
            TrackLog.i("LOCATION_REQUEST_SUCCESS")
            TrackLog.i("NATIVE_GPS_REQUEST_STARTED interval=${intervalSec}s")
            TrackLog.i("NATIVE_GPS_FIRST_FIX_WAITING")
            // Cold-fix watchdog: log (do NOT stop) if no first fix within the cold-fix window.
            handler.removeCallbacks(nativeColdFixWatchdog)
            handler.postDelayed(nativeColdFixWatchdog, NATIVE_COLD_FIX_MS)
        } catch (e: SecurityException) {
            mode = Mode.NONE
            locationRequestActive = false
            AgentHealthState.sessionState.value = LocationSessionState.ERROR
            AgentHealthState.onError("SecurityException: ${e.message}")
            TrackLog.e("LOCATION_REQUEST_SECURITY_EXCEPTION", e)
            TrackLog.w("TRACKING_ENGINE_START_FAILED reason=security_exception")
        } catch (e: Exception) {
            mode = Mode.NONE
            locationRequestActive = false
            AgentHealthState.sessionState.value = LocationSessionState.ERROR
            AgentHealthState.onError(e.message)
            TrackLog.e("LOCATION_REQUEST_FAILURE", e)
            TrackLog.w("TRACKING_ENGINE_START_FAILED reason=exception")
        }
    }

    private fun stopNativeGps() {
        if (mode != Mode.NATIVE_GPS) return
        runCatching { locationManager.removeUpdates(gpsListener) }
            .onFailure { TrackLog.e("REMOVE_UPDATES_FAILED", it) }
        locationRequestActive = false
        handler.removeCallbacks(nativeColdFixWatchdog)
    }

    private fun stopAllUpdates() {
        stopFused()
        stopNativeGps()
        mode = Mode.NONE
        currentIntervalSec = -1
        locationRequestActive = false
    }

    // --- Watchdogs ------------------------------------------------------------------------

    private val fixWatchdog = Runnable {
        val gotFix = lastFixAt > locationRequestedAt
        if (mode == Mode.FUSED && !gotFix) {
            TrackLog.w("LOCATION_CAPABILITY_SUSPECTED_RESTRICTED no fix ${FIX_WATCHDOG_MS}ms after request (while-in-use FGS restriction?)")
        }
    }

    private val nativeColdFixWatchdog = Runnable {
        if (mode == Mode.NATIVE_GPS && !nativeFirstFix) {
            // Keep the request active — cold GNSS can take minutes. This is diagnostic only.
            TrackLog.w("NATIVE_GPS_TIMEOUT no fix ${NATIVE_COLD_FIX_MS}ms after request (still listening)")
        }
    }

    // --- Network state --------------------------------------------------------------------

    private fun setOnline(value: Boolean) {
        if (online == value) return
        online = value
        TrackLog.i("NETWORK_AVAILABLE=$value")
        // Switch acquisition path if we're currently supposed to be tracking.
        handler.post { runCatching { evaluateGate() }.onFailure { TrackLog.e("GATE_EVAL_FAILED", it) } }
    }

    private fun hasValidatedInternet(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerNetworkMonitor() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        runCatching { connectivity.registerNetworkCallback(request, networkCallback) }
            .onFailure { TrackLog.e("NETWORK_CALLBACK_REGISTER_FAILED", it) }
    }

    // --- Location handling ----------------------------------------------------------------

    private fun onNewLocation(loc: Location, provider: String) {
        try {
            val creds = app.container.deviceRepository.credentials() ?: return
            lastFixAt = System.currentTimeMillis()
            AgentHealthState.onLocation(if (loc.hasAccuracy()) loc.accuracy else null)
            TrackLog.i("LOCATION_RECEIVED provider=$provider acc=${if (loc.hasAccuracy()) loc.accuracy else "?"}")

            val point = pointFactory.create(loc, creds.deviceId)
            if (!point.isValid()) {
                TrackLog.w("LOCATION_INVALID_DROPPED")
                return
            }
            if (online) TrackLog.i("LOCATION_UPLOAD_QUEUED event=${point.eventId}")
            else TrackLog.i("LOCATION_UPLOAD_QUEUED_OFFLINE event=${point.eventId}")

            scope.launch {
                try {
                    app.container.locationRepository.record(point)
                    if (online) TrackLog.i("LOCATION_UPLOAD_SUCCESS event=${point.eventId}")
                } catch (t: Throwable) {
                    TrackLog.e("LOCATION_UPLOAD_FAILED event=${point.eventId}", t)
                }
            }

            val policy = policy()
            if (policy != null && policy.geofencingEnabled) {
                geofenceEvaluator.updateAreas(policy.geofences)
                val events = geofenceEvaluator.onLocation(loc, creds.deviceId)
                if (events.isNotEmpty()) {
                    scope.launch { events.forEach { runCatching { app.container.eventsRepository.reportGeofence(it) } } }
                }
            }
        } catch (t: Throwable) {
            TrackLog.e("LOCATION_HANDLING_FAILED", t)
        }
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.tracking_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = getString(R.string.tracking_channel_desc)
                        setShowBadge(false)
                    },
                )
            }
        }
        return CHANNEL_ID
    }

    private fun stopSelfSafely(reason: String) {
        // Requirement: never stopSelf() once a GPS/location session is active.
        if (mode != Mode.NONE) {
            TrackLog.w("STOP_SUPPRESSED reason=$reason (tracking active)")
            return
        }
        TrackLog.i("SERVICE_STOP_REQUESTED reason=$reason")
        runCatching {
            stopAllUpdates()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        TrackLog.i("SERVICE_DESTROYED")
        AgentLog.i(AgentLog.Category.TRACKING, "location service destroyed")
        handler.removeCallbacks(gateTick)
        handler.removeCallbacks(fixWatchdog)
        handler.removeCallbacks(nativeColdFixWatchdog)
        stopAllUpdates()
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        AgentHealthState.sessionState.value = LocationSessionState.STOPPED
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "dodeal_location_tracking"
        private const val NOTIFICATION_ID = 4711
        private const val GATE_INTERVAL_MS = 60_000L
        private const val DEFAULT_INTERVAL_SEC = 60
        private const val FIX_WATCHDOG_MS = 45_000L
        private const val NATIVE_COLD_FIX_MS = 5 * 60_000L // 5 min cold-fix window

        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
                TrackLog.i("STARTING_LOCATION_FGS")
            } catch (t: Throwable) {
                TrackLog.e("STARTING_LOCATION_FGS_FAILED", t)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, LocationForegroundService::class.java)) }
        }
    }
}
