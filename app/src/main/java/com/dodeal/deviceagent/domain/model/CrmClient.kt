package com.dodeal.deviceagent.domain.model

/** Result of the CMS client lookup (Step 1 of multi-tenant login). */
data class CrmClient(
    val clientId: String,
    val clientName: String,
    val clientDomain: String,
    val status: String,
) {
    val isActive: Boolean get() = status.equals("active", ignoreCase = true)
}
