package com.dodeal.deviceagent.data.api

/** Transport-neutral result. No Retrofit/OkHttp type ever leaks above the adapter. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    /** Connectivity problem — safe to retry / fall back to cache / enqueue offline. */
    data class Network(val cause: Throwable) : ApiError
    /** Non-2xx that isn't auth-related. */
    data class Http(val code: Int, val message: String?) : ApiError
    /** 401 — credentials missing/expired/revoked. */
    data object Unauthorized : ApiError
    /** Anything else (serialization, unexpected). */
    data class Unexpected(val cause: Throwable) : ApiError
}
