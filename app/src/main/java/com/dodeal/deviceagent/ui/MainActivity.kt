package com.dodeal.deviceagent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dodeal.deviceagent.DodealAgentApp
import com.dodeal.deviceagent.location.TrackingServiceStarter
import com.dodeal.deviceagent.ui.diagnostics.DiagnosticsScreen
import com.dodeal.deviceagent.ui.login.LoginScreen
import com.dodeal.deviceagent.ui.login.LoginViewModel
import com.dodeal.deviceagent.ui.status.StatusScreen
import com.dodeal.deviceagent.ui.status.StatusViewModel
import com.dodeal.deviceagent.ui.theme.DodealTheme
import com.dodeal.deviceagent.work.AgentScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DodealAgentApp).container
        val repository = container.deviceRepository

        // Normal-APK path: opening the app is a foreground context, so starting the FGS here is
        // permitted and the service gains location capability. No privileged/system status required.
        if (repository.isRegistered()) {
            TrackingServiceStarter.start(this)
        }

        setContent {
            DodealTheme {
                // Enrollment is one-time and persistent; the device opens on the status screen and
                // never returns to login on its own.
                var loggedIn by remember { mutableStateOf(repository.isRegistered()) }
                var sessionUsername by remember { mutableStateOf<String?>(null) }
                var showDiagnostics by remember { mutableStateOf(false) }

                when {
                    !loggedIn -> {
                        val vm: LoginViewModel = viewModel(factory = LoginViewModel.Factory(container.authRepository))
                        LoginScreen(
                            viewModel = vm,
                            onLoggedIn = { username ->
                                AgentScheduler.apply(this@MainActivity, container.deviceRepository.cachedPolicy())
                                TrackingServiceStarter.start(this@MainActivity)
                                sessionUsername = username
                                loggedIn = true
                            },
                        )
                    }
                    showDiagnostics -> {
                        DiagnosticsScreen(
                            healthProvider = container.deviceHealthProvider,
                            onBack = { showDiagnostics = false },
                        )
                    }
                    else -> {
                        val vm: StatusViewModel = viewModel(
                            key = "status-${sessionUsername ?: "restored"}",
                            factory = StatusViewModel.Factory(
                                repository = repository,
                                locationController = container.locationController,
                                locationRepository = container.locationRepository,
                                deviceInfoProvider = container.deviceInfoProvider,
                                runtimeMode = container.lifecycleStrategy.mode.name,
                                sessionUsername = sessionUsername,
                            ),
                        )
                        StatusScreen(viewModel = vm, onOpenDiagnostics = { showDiagnostics = true })
                    }
                }
            }
        }
    }
}
