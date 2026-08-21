package com.dodeal.updater.net

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration

object HttpFactory {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofMinutes(5))   // large APK downloads
        .writeTimeout(Duration.ofSeconds(30))
        .build()

    fun api(baseUrl: String, client: OkHttpClient): UpdateApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(UpdateApi::class.java)
}
