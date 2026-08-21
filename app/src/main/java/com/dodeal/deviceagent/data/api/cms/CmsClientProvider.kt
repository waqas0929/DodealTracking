package com.dodeal.deviceagent.data.api.cms

import com.dodeal.deviceagent.data.api.ApiError
import com.dodeal.deviceagent.data.api.ApiResult
import com.dodeal.deviceagent.domain.model.CrmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Resolves a Client ID to its CRM tenant via the CMS API. Transport types never leak out. */
class CmsClientProvider(private val service: CmsService) {

    suspend fun getClient(clientId: String): ApiResult<CrmClient> = withContext(Dispatchers.IO) {
        try {
            val response = service.getClient(clientId.trim())
            if (response.isSuccessful) {
                val body = response.body()
                if (body == null || body.clientDomain.isBlank()) {
                    ApiResult.Failure(ApiError.Http(404, "Client not found"))
                } else {
                    ApiResult.Success(
                        CrmClient(
                            clientId = body.clientIdString().ifBlank { clientId.trim() },
                            clientName = body.clientName,
                            clientDomain = body.clientDomain,
                            status = body.status,
                        ),
                    )
                }
            } else {
                ApiResult.Failure(ApiError.Http(response.code(), response.message()))
            }
        } catch (e: IOException) {
            ApiResult.Failure(ApiError.Network(e))
        } catch (e: Exception) {
            ApiResult.Failure(ApiError.Unexpected(e))
        }
    }
}
