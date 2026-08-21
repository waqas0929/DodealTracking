package com.dodeal.deviceagent.data.api.cms

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Retrofit definition for the fixed CMS host. Base URL comes from ApiConfig.cmsBaseUrl. */
interface CmsService {
    @GET("api/crmclients/{client_id}")
    suspend fun getClient(@Path("client_id") clientId: String): Response<CmsClientResponse>
}
