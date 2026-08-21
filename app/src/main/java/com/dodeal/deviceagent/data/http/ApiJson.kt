package com.dodeal.deviceagent.data.http

import kotlinx.serialization.json.Json

/**
 * The ONE JSON parser the app talks to the server with.
 *
 * It used to be a private field inside HttpClientFactory, which meant a test could only ever check
 * a DTO against a parser it had configured itself — so a response that Retrofit would reject could
 * still pass a green test. Sharing the instance makes the wire contract testable for real.
 *
 *  - ignoreUnknownKeys: the server may add fields (server_time, device_uid, expires_at …) without
 *    breaking an older build.
 *  - explicitNulls = false: we never send nulls for values we simply do not have.
 *  - isLenient: the CRM returns numeric ids (command_id, lead_id, user_id) unquoted. Without this a
 *    number arriving where a String is declared throws and the WHOLE response fails to parse —
 *    which is exactly how command polling died silently.
 */
object ApiJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }
}
