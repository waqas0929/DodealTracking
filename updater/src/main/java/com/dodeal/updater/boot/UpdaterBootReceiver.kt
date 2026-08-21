package com.dodeal.updater.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dodeal.updater.UpdaterLog
import com.dodeal.updater.work.UpdateWorker

/**
 * Starts the periodic update checker after boot / self-update. It only SCHEDULES the WorkManager
 * check (which recovers state safely) — it never installs directly on boot, so there is no update
 * boot loop (spec §21).
 */
class UpdaterBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UpdaterLog.i("BOOT_RECEIVED action=${intent.action}")
        UpdateWorker.schedule(context.applicationContext)
    }
}
