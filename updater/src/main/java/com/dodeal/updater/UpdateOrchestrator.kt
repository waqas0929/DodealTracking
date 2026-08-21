package com.dodeal.updater

import android.content.Context
import android.content.pm.PackageManager
import com.dodeal.updater.config.Allowlist
import com.dodeal.updater.config.UpdaterConfig
import com.dodeal.updater.data.AppUpdate
import com.dodeal.updater.data.UpdateResultRequest
import com.dodeal.updater.install.BatteryGuard
import com.dodeal.updater.install.InstallOutcomeBus
import com.dodeal.updater.install.InstallState
import com.dodeal.updater.install.SilentInstaller
import com.dodeal.updater.net.Downloader
import com.dodeal.updater.net.HttpFactory
import com.dodeal.updater.net.UpdateApi
import com.dodeal.updater.security.PackageVerifier
import com.dodeal.updater.security.VerifyResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * The end-to-end update flow (spec §5): check -> compare -> (battery/schedule gate) -> download ->
 * verify -> silent install -> confirm installed version -> restart target -> report result.
 * One install per package at a time; existing working version is always kept on failure.
 */
class UpdateOrchestrator(private val context: Context) {

    private val config = UpdaterConfig(context)
    private val client = HttpFactory.client()
    private val api: UpdateApi = HttpFactory.api(config.baseUrl(), client)
    private val downloader = Downloader(client)
    private val verifier = PackageVerifier(context)
    private val installer = SilentInstaller(context)
    private val battery = BatteryGuard(context)

    private val inFlight = ConcurrentHashMap.newKeySet<String>() // no duplicate installs per package

    suspend fun runCheck() {
        UpdaterLog.i("UPDATE_CHECK_STARTED channel=${config.channel}")
        val resp = runCatching {
            api.updatePolicy(
                deviceId = null,
                serial = safeSerial(),
                firmware = android.os.Build.DISPLAY,
                currentVersions = installedVersionsCsv(),
            )
        }.getOrElse { UpdaterLog.e("UPDATE_CHECK_FAILED", it); return }

        if (resp == null || !resp.isSuccessful) {
            UpdaterLog.w("UPDATE_CHECK_HTTP_${resp?.code()}")
            return
        }
        val policy = resp.body()
        policy?.pollMinutes?.let { config.pollMinutes = it }
        val apps = policy?.apps.orEmpty().filter { Allowlist.isAllowed(it.packageName) }
        if (apps.isEmpty()) { UpdaterLog.i("NO_UPDATE_AVAILABLE"); return }

        for (app in apps) process(app)
    }

    private suspend fun process(app: AppUpdate) {
        if (!inFlight.add(app.packageName)) { UpdaterLog.w("SKIP already in-flight ${app.packageName}"); return }
        val downloadStarted = Instant.now()
        var installStarted: Instant? = null
        val installedBefore = installedVersionCode(app.packageName)
        try {
            // Version rule (spec §7)
            if (installedBefore != null && app.versionCode <= installedBefore && !app.allowRollback) {
                UpdaterLog.i("NO_UPDATE_AVAILABLE ${app.packageName} (installed=$installedBefore server=${app.versionCode})")
                return
            }
            UpdaterLog.i("UPDATE_AVAILABLE ${app.packageName} -> ${app.versionCode}")
            state(app, InstallState.AVAILABLE)

            // Scheduled install window (spec §23) — download now, defer install if scheduled in future.
            val now = System.currentTimeMillis()
            val scheduledFuture = app.installAfterEpochMs?.let { it > now } ?: false

            // Download
            state(app, InstallState.DOWNLOADING)
            val dir = context.filesDir
            val part = downloader.tempFile(dir, app.packageName, app.versionCode)
            val finalApk = downloader.finalFile(dir, app.packageName, app.versionCode)
            if (!finalApk.exists()) {
                if (!downloader.download(app.downloadUrl, part)) { fail(app, installedBefore, "DOWNLOAD_FAILED", downloadStarted, null); return }
                part.renameTo(finalApk)
            }
            state(app, InstallState.DOWNLOADED)

            // Verify (spec §8)
            state(app, InstallState.VERIFYING)
            when (val v = verifier.verify(finalApk, app.packageName, app.versionCode, app.sha256, app.sizeBytes, app.allowRollback)) {
                is VerifyResult.Rejected -> { finalApk.delete(); fail(app, installedBefore, v.code, downloadStarted, null); return }
                VerifyResult.Ok -> Unit
            }

            if (scheduledFuture) {
                UpdaterLog.i("INSTALL_DEFERRED ${app.packageName} until=${app.installAfterEpochMs}")
                state(app, InstallState.READY_TO_INSTALL)
                return // a later worker run (after the timestamp) will install
            }

            // Battery gate (spec §14)
            if (!battery.canInstall(app.minBatteryPercent)) {
                UpdaterLog.i("INSTALL_WAITING battery=${battery.percent()}% < ${app.minBatteryPercent}% not charging")
                state(app, InstallState.RETRY_WAITING)
                return
            }

            // Install (spec §10)
            state(app, InstallState.READY_TO_INSTALL)
            installStarted = Instant.now()
            state(app, InstallState.INSTALLING)
            val sessionId = installer.install(finalApk, app.packageName)
            if (sessionId == null) { fail(app, installedBefore, "INSTALL_FAILED", downloadStarted, installStarted); return }

            // Await commit callback
            val outcome = withTimeoutOrNull(INSTALL_TIMEOUT_MS) {
                InstallOutcomeBus.flow.first { it.packageName == app.packageName }
            }
            if (outcome == null || !outcome.success) {
                fail(app, installedBefore, outcome?.code ?: "INSTALL_FAILED", downloadStarted, installStarted)
                return
            }

            // Confirm installed version (spec §18)
            val installedAfter = installedVersionCode(app.packageName)
            if (installedAfter == null || installedAfter < app.versionCode) {
                fail(app, installedBefore, "INSTALL_FAILED", downloadStarted, installStarted); return
            }
            state(app, InstallState.INSTALLED)
            UpdaterLog.i("INSTALL_SUCCESS ${app.packageName} v=$installedAfter")
            finalApk.delete()

            // Restart the target app/service (spec §18) — Tracking has a co-signed restart receiver.
            restartTarget(app.packageName)

            report(app, installedBefore ?: 0, installedAfter, "SUCCESS", null, null, downloadStarted, installStarted)
        } catch (t: Throwable) {
            UpdaterLog.e("UPDATE_FLOW_FAILED ${app.packageName}", t)
            fail(app, installedBefore, "INSTALL_FAILED", downloadStarted, installStarted)
        } finally {
            inFlight.remove(app.packageName)
        }
    }

    private fun restartTarget(packageName: String) {
        // Each Dodeal app is signed with its OWN key, so we do NOT use a cross-app signature
        // permission. After an in-place update Android delivers ACTION_MY_PACKAGE_REPLACED to the
        // updated app itself, which restarts its own service (Tracking's boot receiver handles it).
        UpdaterLog.i("APP_RESTART_REQUESTED $packageName (via MY_PACKAGE_REPLACED)")
    }

    private suspend fun fail(app: AppUpdate, oldV: Long?, code: String, dl: Instant, inst: Instant?) {
        state(app, InstallState.FAILED)
        UpdaterLog.w("INSTALL_FAILED ${app.packageName} code=$code (keeping installed version)")
        report(app, oldV ?: 0, oldV ?: 0, "FAILED", code, code, dl, inst)
    }

    private suspend fun report(
        app: AppUpdate, oldV: Long, newV: Long, status: String,
        errorCode: String?, errorMsg: String?, dl: Instant, inst: Instant?,
    ) {
        val req = UpdateResultRequest(
            packageName = app.packageName,
            oldVersionCode = oldV,
            newVersionCode = newV,
            status = status,
            errorCode = errorCode,
            errorMessage = errorMsg,
            downloadStartedAt = dl.toString(),
            installStartedAt = inst?.toString(),
            completedAt = Instant.now().toString(),
        )
        runCatching { api.reportResult(req) }
            .onSuccess { UpdaterLog.i("UPDATE_RESULT_REPORTED ${app.packageName} $status") }
            .onFailure { UpdaterLog.w("UPDATE_RESULT_REPORT_FAILED ${app.packageName}", it) }
    }

    private fun state(app: AppUpdate, s: InstallState) = UpdaterLog.i("STATE ${app.packageName} -> $s")

    private fun installedVersionCode(pkg: String): Long? = try {
        val pi = context.packageManager.getPackageInfo(pkg, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (_: PackageManager.NameNotFoundException) { null }

    private fun installedVersionsCsv(): String =
        Allowlist.PACKAGES.mapNotNull { p -> installedVersionCode(p)?.let { "$p:$it" } }.joinToString(",")

    private fun safeSerial(): String? =
        runCatching { android.os.Build.getSerial() }.getOrNull()?.takeIf { it != android.os.Build.UNKNOWN }

    private companion object { const val INSTALL_TIMEOUT_MS = 120_000L }
}
