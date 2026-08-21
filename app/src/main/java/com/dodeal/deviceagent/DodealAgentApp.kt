package com.dodeal.deviceagent

import android.app.Application
import com.dodeal.deviceagent.di.AppContainer
import com.dodeal.deviceagent.work.AgentScheduler

class DodealAgentApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.lifecycleStrategy.onAgentStart(this)
        container.lifecycleStrategy.requestPersistentExecution(this)

        // Every periodic job — flush, heartbeat, command poll — is scheduled from the policy
        // in force, in one place. Safe in any mode; the cached policy may be null on first run.
        AgentScheduler.apply(this, container.deviceRepository.cachedPolicy())

        // NOTE: we do NOT start the foreground service from here. Application.onCreate is a background
        // context, and on Android 14+ a normal app starting a location FGS from the background can be
        // rejected (and the half-started service killed ~seconds later). Tracking is started from a
        // foreground/allowed context instead: MainActivity.onCreate and BootCompletedReceiver.
        // Telemetry only matters once enrolled; AgentScheduler above already covers the flush.
        if (!container.deviceRepository.isRegistered()) {
            AgentScheduler.cancelTelemetry(this)
        }
    }
}
