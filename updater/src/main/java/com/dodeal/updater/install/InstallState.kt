package com.dodeal.updater.install

/** Per-package update state machine (spec §15). */
enum class InstallState {
    AVAILABLE, DOWNLOADING, DOWNLOADED, VERIFYING, READY_TO_INSTALL,
    INSTALLING, INSTALLED, FAILED, RETRY_WAITING
}
