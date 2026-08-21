package com.dodeal.deviceagent.config

/**
 * Minimal bootstrap identity supplied at enrollment time. On a provisioned company device these
 * come from the enrollment step (QR / ROM provisioning / MDM). For the test APK they are
 * placeholders so registration can be exercised end to end.
 */
data class EnrollmentConfig(
    val agencyId: String = "AGENCY_PLACEHOLDER",
    val salesUserId: String = "SALES_USER_PLACEHOLDER",
) {
    companion object {
        val TEST_DEFAULT = EnrollmentConfig()
    }
}
