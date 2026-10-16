package com.example.railtap

import android.util.Base64
import io.github.jan.supabase.auth.auth
import org.json.JSONObject

/**
 * UI routing only. Reads the server-issued app_metadata role from the logged-in JWT.
 * This is NOT a substitute for server authorization; redemption is protected by
 * public.redeem_ticket_at_gate() in Supabase, which checks auth.jwt().
 */
object ScannerOnlyRole {
    fun isScanner(): Boolean {
        val jwt = SupabaseClient.client.auth.currentAccessTokenOrNull() ?: return false
        return try {
            val payload = jwt.split('.')[1]
            val decoded = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            JSONObject(decoded)
                .optJSONObject("app_metadata")
                ?.optBoolean("railtap_gate_scanner", false) == true
        } catch (_: Exception) {
            false
        }
    }
}
