package com.dodeal.deviceagent.domain.model

import java.time.Instant

/**
 * Auth material for a single agent account. The access token is what scopes every request to this
 * agent on the server; the (optional) refresh token lets us rotate the access token without asking
 * the agent to log in again. Signing keys / mTLS identities can be added as sibling fields later
 * without changing consumers.
 */
data class DeviceCredentials(
    val deviceId: String,
    val accessToken: String,
    val tokenType: String,
    val expiresAt: Instant,
    val refreshToken: String? = null,
) {
    fun isExpired(now: Instant = Instant.now()): Boolean = !expiresAt.isAfter(now)

    /** True once we're within [skew] of expiry — refresh proactively before the token dies. */
    fun expiresWithin(skew: java.time.Duration, now: Instant = Instant.now()): Boolean =
        !expiresAt.isAfter(now.plus(skew))
}
