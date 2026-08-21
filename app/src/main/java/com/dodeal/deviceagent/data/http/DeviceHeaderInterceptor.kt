package com.dodeal.deviceagent.data.http

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the X-Device-Id header (the stable device UID) to every tenant request, as required by the
 * DoDeal device API so fixes, heartbeats and commands are tied to this device. Does not overwrite an
 * X-Device-Id already set by a caller.
 */
class DeviceHeaderInterceptor(private val deviceUid: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER) != null) return chain.proceed(request)
        val withId = request.newBuilder().header(HEADER, deviceUid()).build()
        return chain.proceed(withId)
    }

    private companion object { const val HEADER = "X-Device-Id" }
}
