package com.dodeal.updater.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.dodeal.updater.UpdaterLog

/**
 * Receives the PackageInstaller commit callback. STATUS_PENDING_USER_ACTION should never occur for a
 * privileged silent install; if it does (i.e. we are not actually privileged), it is logged as a
 * failure rather than popping UI.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                UpdaterLog.i("INSTALL_SUCCESS pkg=$pkg")
                InstallOutcomeBus.publish(pkg, true, "INSTALL_SUCCEEDED", null)
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Not privileged enough for silent install — do NOT show UI.
                UpdaterLog.w("INSTALL_FAILED pkg=$pkg reason=PENDING_USER_ACTION (not privileged)")
                InstallOutcomeBus.publish(pkg, false, "INSTALL_FAILED", "pending_user_action")
            }
            else -> {
                val outcome = SilentInstaller.describe(status, msg)
                UpdaterLog.w("INSTALL_FAILED pkg=$pkg status=$status msg=$msg -> $outcome")
                InstallOutcomeBus.publish(pkg, false, outcome, msg)
            }
        }
    }

    companion object {
        const val ACTION = "com.dodeal.updater.INSTALL_RESULT"
        const val EXTRA_PACKAGE = "dodeal_package"
    }
}
