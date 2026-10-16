package com.example.railtap

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class GateScanResult(
    @SerialName("scan_result") val scanResult: String,
    @SerialName("ticket_id") val ticketId: Int? = null,
    @SerialName("scanned_at") val scannedAt: String? = null,
    val detail: String? = null
)

class GateScannerRepository {
    private val client = SupabaseClient.client

    // supabase does the checks + update in one go
    suspend fun scan(qrToken: String): GateScanResult {
        check(client.auth.currentUserOrNull() != null) { "Sign in with the scanner account" }
        return client.postgrest.rpc("auto_scan_ticket_at_gate", buildJsonObject {
            put("p_token", qrToken)
        }).decodeSingle<GateScanResult>()
    }
}
