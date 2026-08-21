package com.dodeal.deviceagent.data.http

import com.dodeal.deviceagent.config.ApiConfig
import com.dodeal.deviceagent.config.LoggingLevel
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds the OkHttp + Retrofit stacks. Timeouts, logging and TLS come from ApiConfig. Two stacks
 * exist: the fixed CMS client (client lookup) and the tenant/device client (auth + dynamic host).
 */
object HttpClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Device/tenant client: auth + any extra interceptors (e.g. the tenant host rewriter). */
    fun okHttpClient(
        config: ApiConfig,
        authInterceptor: Interceptor,
        extraInterceptors: List<Interceptor> = emptyList(),
    ): OkHttpClient {
        val builder = baseBuilder(config).addInterceptor(authInterceptor)
        extraInterceptors.forEach(builder::addInterceptor)
        return builder.build()
    }

    /** Plain client with no auth (used for the CMS client lookup). */
    fun plainOkHttpClient(config: ApiConfig): OkHttpClient = baseBuilder(config).build()

    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun baseBuilder(config: ApiConfig): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout)
            .readTimeout(config.readTimeout)
            .writeTimeout(config.writeTimeout)

        if (config.loggingLevel != LoggingLevel.NONE) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = when (config.loggingLevel) {
                        LoggingLevel.BASIC -> HttpLoggingInterceptor.Level.BASIC
                        LoggingLevel.HEADERS -> HttpLoggingInterceptor.Level.HEADERS
                        LoggingLevel.BODY -> HttpLoggingInterceptor.Level.BODY
                        LoggingLevel.NONE -> HttpLoggingInterceptor.Level.NONE
                    }
                    redactHeader("Authorization") // tokens never reach logs
                },
            )
        }
        return builder
    }
}
