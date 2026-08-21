package com.dodeal.deviceagent.data.api.adapter

import com.dodeal.deviceagent.data.api.ApiError
import com.dodeal.deviceagent.data.api.ApiResult
import retrofit2.Response
import java.io.IOException

/**
 * Executes a Retrofit call and normalises it into ApiResult. This is where every HTTP/OkHttp/
 * serialization concern is trapped and translated — nothing transport-specific escapes upward.
 */
internal suspend fun <D, R> safeApiCall(
    call: suspend () -> Response<D>,
    transform: (D?) -> R,
): ApiResult<R> = try {
    val response = call()
    when {
        response.isSuccessful -> ApiResult.Success(transform(response.body()))
        response.code() == 401 -> ApiResult.Failure(ApiError.Unauthorized)
        else -> ApiResult.Failure(ApiError.Http(response.code(), response.message()))
    }
} catch (io: IOException) {
    ApiResult.Failure(ApiError.Network(io))
} catch (t: Throwable) {
    ApiResult.Failure(ApiError.Unexpected(t))
}
