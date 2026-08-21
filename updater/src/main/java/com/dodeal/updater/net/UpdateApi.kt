package com.dodeal.updater.net

import com.dodeal.updater.data.UpdatePolicyResponse
import com.dodeal.updater.data.UpdateResultRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface UpdateApi {
    @GET("api/device/update-policy")
    suspend fun updatePolicy(
        @Query("device_id") deviceId: String?,
        @Query("serial_number") serial: String?,
        @Query("firmware_version") firmware: String?,
        @Query("current_versions") currentVersions: String?,
    ): Response<UpdatePolicyResponse>

    @POST("api/device/update-result")
    suspend fun reportResult(@Body body: UpdateResultRequest): Response<Unit>
}
