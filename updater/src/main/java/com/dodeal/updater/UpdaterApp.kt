package com.dodeal.updater

import android.app.Application
import com.dodeal.updater.work.UpdateWorker

class UpdaterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Boot-independent: whenever the updater process starts, ensure the periodic check exists.
        UpdateWorker.schedule(this)
    }
}
