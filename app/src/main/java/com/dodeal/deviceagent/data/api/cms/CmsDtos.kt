package com.dodeal.deviceagent.data.api.cms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** GET https://cmsapi.dodealcrm.com/api/crmclients/{client_id} */
@Serializable
data class CmsClientResponse(
    // client_id can arrive as a number (e.g. 354) OR a string; accept either safely.
    @SerialName("client_id") val clientId: JsonElement? = null,
    @SerialName("client_name") val clientName: String = "",
    @SerialName("client_domain") val clientDomain: String = "",
    @SerialName("package") val packageName: String? = null,
    @SerialName("status") val status: String = "",
) {
    fun clientIdString(): String = (clientId as? JsonPrimitive)?.contentOrNull ?: ""
}
