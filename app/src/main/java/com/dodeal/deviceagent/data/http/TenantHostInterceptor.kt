package com.dodeal.deviceagent.data.http

import com.dodeal.deviceagent.data.tenant.TenantStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Redirects every device-API request from the placeholder base to the tenant base URL resolved at
 * login (scheme + host + port). Path stays the same (both share the /api/ prefix), so no endpoint
 * path is duplicated. If no tenant is stored yet, the request proceeds unchanged (pre-login only).
 */
class TenantHostInterceptor(private val tenantStore: TenantStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val tenant = tenantStore.baseUrl()?.toHttpUrlOrNull() ?: return chain.proceed(original)
        val rewritten = original.url.newBuilder()
            .scheme(tenant.scheme)
            .host(tenant.host)
            .port(tenant.port)
            .build()
        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
