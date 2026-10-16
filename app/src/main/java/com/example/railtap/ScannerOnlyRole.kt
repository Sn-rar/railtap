package com.example.railtap

import android.util.Base64
import io.github.jan.supabase.auth.auth
import org.json.JSONObject

// checks if the logged in account is a gate scanner (from the jwt).
// just for routing, the real check is in redeem_ticket_at_gate() on supabase
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
