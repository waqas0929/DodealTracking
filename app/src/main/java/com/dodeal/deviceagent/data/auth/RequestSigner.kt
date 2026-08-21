package com.dodeal.deviceagent.data.auth

import okhttp3.Request

/**
 * Extension point for request signing / replay protection (HMAC over method+path+body,
 * X-Timestamp, X-Nonce, etc.). Default is a no-op so the test APK runs plain-bearer. A real signer
 * is dropped in later without changing the interceptor's callers.
 */
interface RequestSigner {
    fun sign(builder: Request.Builder, original: Request)

    object NoOp : RequestSigner {
        override fun sign(builder: Request.Builder, original: Request) = Unit
    }
}
