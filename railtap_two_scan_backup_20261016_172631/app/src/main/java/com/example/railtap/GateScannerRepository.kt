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

/**
 * The Android scanner does NOT update tickets directly. It can only request
 * a one-time redemption from an authorized Supabase function.
 *
 * Reuses the SAME client/session as the RailTap sign-in screen.
 */
class GateScannerRepository {
    private val client = SupabaseClient.client

    suspend fun redeem(qrToken: String): GateScanResult {
        check(client.auth.currentUserOrNull() != null) {
            "Sign in with a staff-authorized RailTap account first."
        }
        return client.postgrest.rpc(
            "redeem_ticket_at_gate",
            buildJsonObject { put("p_token", qrToken) }
        ).decodeSingle<GateScanResult>()
    }
}
