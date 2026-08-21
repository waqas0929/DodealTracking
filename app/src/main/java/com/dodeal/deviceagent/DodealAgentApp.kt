package com.dodeal.deviceagent

import android.app.Application
import com.dodeal.deviceagent.di.AppContainer
import com.dodeal.deviceagent.work.LocationFlushWorker
import com.dodeal.deviceagent.work.TelemetryScheduler

class DodealAgentApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.lifecycleStrategy.onAgentStart(this)
        container.lifecycleStrategy.requestPersistentExecution(this)

        // Upload/retry loops (safe in any mode).
        LocationFlushWorker.schedule(this)

        // NOTE: we do NOT start the foreground service from here. Application.onCreate is a background
        // context, and on Android 14+ a normal app starting a location FGS from the background can be
        // rejected (and the half-started service killed ~seconds later). Tracking is started from a
        // foreground/allowed context instead: MainActivity.onCreate and BootCompletedReceiver.
        if (container.deviceRepository.isRegistered()) {
            TelemetryScheduler.schedule(this)
        }
    }
}
