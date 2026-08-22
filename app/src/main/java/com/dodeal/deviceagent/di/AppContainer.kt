package com.dodeal.deviceagent.di

import android.content.Context
import com.dodeal.deviceagent.BuildConfig
import com.dodeal.deviceagent.config.ApiConfig
import com.dodeal.deviceagent.config.EnrollmentConfig
import com.dodeal.deviceagent.config.EnvironmentConfig
import com.dodeal.deviceagent.data.api.DeviceApi
import com.dodeal.deviceagent.data.api.adapter.DodealApiAdapter
import com.dodeal.deviceagent.data.api.adapter.DodealDeviceService
import com.dodeal.deviceagent.data.api.cms.CmsClientProvider
import com.dodeal.deviceagent.data.api.cms.CmsService
import com.dodeal.deviceagent.data.auth.AuthInterceptor
import com.dodeal.deviceagent.data.auth.AuthProvider
import com.dodeal.deviceagent.data.auth.BearerTokenAuthProvider
import com.dodeal.deviceagent.data.http.DeviceHeaderInterceptor
import com.dodeal.deviceagent.device.DeviceUidProvider
import com.dodeal.deviceagent.data.http.HttpClientFactory
import com.dodeal.deviceagent.data.http.TenantHostInterceptor
import com.dodeal.deviceagent.data.tenant.PrefsTenantStore
import com.dodeal.deviceagent.data.tenant.TenantStore
import com.dodeal.deviceagent.data.queue.FileLocationQueue
import com.dodeal.deviceagent.data.queue.LocationQueue
import com.dodeal.deviceagent.data.storage.EncryptedTokenStorage
import com.dodeal.deviceagent.data.storage.FilePolicyStore
import com.dodeal.deviceagent.data.storage.PolicyStore
import com.dodeal.deviceagent.data.storage.SecureTokenStorage
import com.dodeal.deviceagent.device.DeviceInfoProvider
import com.dodeal.deviceagent.device.PrivilegeDetector
import com.dodeal.deviceagent.domain.repository.AuthRepository
import com.dodeal.deviceagent.domain.repository.AuthRepositoryImpl
import com.dodeal.deviceagent.domain.repository.DeviceRepository
import com.dodeal.deviceagent.domain.repository.DeviceRepositoryImpl
import com.dodeal.deviceagent.domain.repository.EventsRepository
import com.dodeal.deviceagent.domain.repository.EventsRepositoryImpl
import com.dodeal.deviceagent.domain.repository.LocationRepository
import com.dodeal.deviceagent.domain.repository.LocationRepositoryImpl
import com.dodeal.deviceagent.domain.repository.MeetingRepository
import com.dodeal.deviceagent.domain.repository.MeetingRepositoryImpl
import com.dodeal.deviceagent.domain.repository.TelemetryRepository
import com.dodeal.deviceagent.domain.repository.TelemetryRepositoryImpl
import com.dodeal.deviceagent.health.DeviceHealthProvider
import com.dodeal.deviceagent.lifecycle.PrivilegedRomStrategy
import com.dodeal.deviceagent.lifecycle.ProcessLifecycleStrategy
import com.dodeal.deviceagent.lifecycle.StandardApkStrategy
import com.dodeal.deviceagent.location.CurrentLocationProvider
import com.dodeal.deviceagent.location.DeviceStateProvider
import com.dodeal.deviceagent.location.LocationController
import com.dodeal.deviceagent.telemetry.CommandExecutor
import com.dodeal.deviceagent.telemetry.HeartbeatFactory

/**
 * Manual dependency graph. Kept deliberately simple (no annotation-processing framework) so the
 * wiring is fully visible and easy to audit for a system app. Everything is assembled from the
 * central ApiConfig; swapping the backend or the lifecycle mode happens here in one place.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    // --- Config -----------------------------------------------------------------------------
    val apiConfig: ApiConfig =
        EnvironmentConfig.forEnvironment(EnvironmentConfig.fromName(BuildConfig.AGENT_ENVIRONMENT))

    val enrollmentConfig: EnrollmentConfig = EnrollmentConfig.TEST_DEFAULT

    // --- Lifecycle strategy: auto-selected from runtime privilege (system app => ROM strategy) ---
    private val privilegeDetector = PrivilegeDetector(appContext)

    val lifecycleStrategy: ProcessLifecycleStrategy =
        if (privilegeDetector.isSystemApp()) PrivilegedRomStrategy() else StandardApkStrategy()

    // --- Storage + Auth ---------------------------------------------------------------------
    private val tokenStorage: SecureTokenStorage = EncryptedTokenStorage(appContext)
    private val policyStore: PolicyStore = FilePolicyStore(appContext)
    private val authProvider: AuthProvider = BearerTokenAuthProvider(tokenStorage)
    // Public: the status screen reads the signed-in login id from here, so the name shown as
    // "Assigned User" survives a restart.
    val tenantStore: TenantStore = PrefsTenantStore(appContext)

    // --- Network stack (confined here) ------------------------------------------------------
    val deviceUidProvider: DeviceUidProvider by lazy { DeviceUidProvider(appContext) }

    private val deviceApi: DeviceApi by lazy {
        // Base is a placeholder; TenantHostInterceptor rewrites each request to the tenant resolved
        // at login (https://{client_domain}/api/). Paths in DodealDeviceService are relative to /api/.
        val client = HttpClientFactory.okHttpClient(
            config = apiConfig,
            authInterceptor = AuthInterceptor(authProvider),
            extraInterceptors = listOf(
                TenantHostInterceptor(tenantStore),
                DeviceHeaderInterceptor { deviceUidProvider.uid() },
            ),
        )
        val retrofit = HttpClientFactory.retrofit(TENANT_PLACEHOLDER_BASE_URL, client)
        DodealApiAdapter(retrofit.create(DodealDeviceService::class.java))
    }

    // --- CMS client lookup (fixed host) -----------------------------------------------------
    private val cmsClientProvider: CmsClientProvider by lazy {
        val client = HttpClientFactory.plainOkHttpClient(apiConfig)
        val retrofit = HttpClientFactory.retrofit(apiConfig.cmsBaseUrl, client)
        CmsClientProvider(retrofit.create(CmsService::class.java))
    }

    // --- Multi-tenant auth ------------------------------------------------------------------
    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            apiConfig = apiConfig,
            cmsClientProvider = cmsClientProvider,
            tenantStore = tenantStore,
            deviceApi = deviceApi,
            authProvider = authProvider,
        )
    }

    val deviceInfoProvider = DeviceInfoProvider(appContext)

    // --- Domain -----------------------------------------------------------------------------
    val deviceRepository: DeviceRepository by lazy {
        DeviceRepositoryImpl(
            deviceApi = deviceApi,
            authProvider = authProvider,
            policyStore = policyStore,
            deviceInfoProvider = deviceInfoProvider,
            enrollmentConfig = enrollmentConfig,
        )
    }

    // --- Shared providers -------------------------------------------------------------------
    val deviceStateProvider = DeviceStateProvider(appContext)
    val currentLocationProvider = CurrentLocationProvider(appContext)

    // --- Location engine --------------------------------------------------------------------
    private val locationQueue: LocationQueue = FileLocationQueue(appContext)

    val locationRepository: LocationRepository by lazy {
        LocationRepositoryImpl(deviceApi = deviceApi, queue = locationQueue)
    }

    val locationController: LocationController by lazy {
        LocationController(appContext = appContext, deviceRepository = deviceRepository)
    }

    // --- Geofencing + meetings (milestone 4) ------------------------------------------------
    val eventsRepository: EventsRepository by lazy { EventsRepositoryImpl(deviceApi = deviceApi) }

    val meetingRepository: MeetingRepository by lazy {
        MeetingRepositoryImpl(
            deviceApi = deviceApi,
            deviceRepository = deviceRepository,
            currentLocation = currentLocationProvider,
        )
    }

    // --- Telemetry: heartbeat + commands (milestone 3) --------------------------------------
    val telemetryRepository: TelemetryRepository by lazy {
        TelemetryRepositoryImpl(
            deviceApi = deviceApi,
            deviceRepository = deviceRepository,
            heartbeatFactory = HeartbeatFactory(
                appContext = appContext,
                deviceState = deviceStateProvider,
                deviceInfoProvider = deviceInfoProvider,
                deviceRepository = deviceRepository,
                locationRepository = locationRepository,
                locationController = locationController,
            ),
            commandExecutor = CommandExecutor(
                deviceRepository = deviceRepository,
                locationController = locationController,
                locationRepository = locationRepository,
                currentLocation = currentLocationProvider,
                deviceState = deviceStateProvider,
            ),
            // A policy the server said had changed has just been re-fetched: rebuild the tracking
            // session and the background cadence on it, without the user pressing anything.
            onPolicyChanged = { locationController.onPolicyChanged() },
        )
    }

    // --- Diagnostics / health (spec §5, §44) ------------------------------------------------
    val deviceHealthProvider: DeviceHealthProvider by lazy {
        DeviceHealthProvider(
            appContext = appContext,
            privilegeDetector = privilegeDetector,
            deviceRepository = deviceRepository,
            locationRepository = locationRepository,
            locationController = locationController,
            deviceInfoProvider = deviceInfoProvider,
        )
    }

    private companion object {
        // Real host is applied per-request by TenantHostInterceptor; this only needs the /api/ path.
        const val TENANT_PLACEHOLDER_BASE_URL = "https://tenant.invalid/api/"
    }
}
