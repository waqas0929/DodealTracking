package com.dodeal.updater.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.dodeal.updater.UpdaterLog
import java.io.File

/**
 * Silent install via the PackageInstaller Session API (spec §10). No installer UI is shown — this
 * only works when the app holds the privileged INSTALL_PACKAGES capability (priv-app on firmware).
 * The commit result is delivered to InstallResultReceiver.
 */
class SilentInstaller(private val context: Context) {

    /** Returns the sessionId, or null if the session could not be created/written. */
    fun install(apk: File, packageName: String): Int? {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
        }
        var sessionId = -1
        return try {
            sessionId = pi.createSession(params)
            UpdaterLog.i("INSTALL_SESSION_CREATED id=$sessionId pkg=$packageName")
            pi.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                UpdaterLog.i("INSTALL_STARTED id=$sessionId")
                val intent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = InstallResultReceiver.ACTION
                    putExtra(InstallResultReceiver.EXTRA_PACKAGE, packageName)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val sender = PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
                session.commit(sender)
            }
            sessionId
        } catch (t: Throwable) {
            UpdaterLog.e("INSTALL_FAILED session=$sessionId", t)
            if (sessionId >= 0) runCatching { pi.abandonSession(sessionId) }
            null
        }
    }

    companion object {
        /** Maps PackageInstaller status codes to the spec's outcomes. */
        fun describe(status: Int, legacyMessage: String?): String = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "INSTALL_SUCCEEDED"
            else -> when {
                legacyMessage?.contains("VERSION_DOWNGRADE", true) == true -> "INSTALL_FAILED_VERSION_DOWNGRADE"
                legacyMessage?.contains("UPDATE_INCOMPATIBLE", true) == true -> "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
                legacyMessage?.contains("INSUFFICIENT_STORAGE", true) == true -> "INSTALL_FAILED_INSUFFICIENT_STORAGE"
                else -> "INSTALL_FAILED"
            }
        }
    }
}
