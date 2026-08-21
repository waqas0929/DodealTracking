package com.dodeal.deviceagent.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dodeal.deviceagent.DodealAgentApp

class CommandPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DodealAgentApp
        if (!app.container.deviceRepository.isRegistered()) return Result.success()
        return runCatching { app.container.telemetryRepository.pollAndExecuteCommands() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
