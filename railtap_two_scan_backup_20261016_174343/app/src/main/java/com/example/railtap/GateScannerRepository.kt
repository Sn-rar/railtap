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

@Serializable
private data class GateStationDb(
    @SerialName("station_id") val stationId: Int,
    @SerialName("line_id") val lineId: Int,
    val name: String,
    val position: Int
)

@Serializable
private data class GateLineDb(
    @SerialName("line_id") val lineId: Int,
    val code: String
)

data class GateStation(val stationId: Int, val label: String, val lineId: Int, val position: Int)

class GateScannerRepository {
    private val client = SupabaseClient.client

    suspend fun loadStations(): List<GateStation> {
        check(client.auth.currentUserOrNull() != null) { "Sign in first" }
        val stations = client.postgrest.from("stations").select().decodeList<GateStationDb>()
        val lines = client.postgrest.from("lines").select().decodeList<GateLineDb>()
            .associateBy { it.lineId }
        return stations.sortedWith(compareBy<GateStationDb> { it.lineId }.thenBy { it.position })
            .map { station ->
                val line = lines[station.lineId]?.code ?: "LRT${station.lineId}"
                GateStation(station.stationId, "${line.replace("LRT", "LRT-")} • ${station.name}",
                    station.lineId, station.position)
            }
    }

    /** Only the Supabase SECURITY DEFINER function may update the ticket. */
    suspend fun scan(qrToken: String, mode: String, stationId: Int): GateScanResult {
        check(client.auth.currentUserOrNull() != null) { "Sign in with an authorized scanner account" }
        return client.postgrest.rpc("redeem_ticket_at_gate", buildJsonObject {
            put("p_token", qrToken)
            put("p_mode", mode)
            put("p_station_id", stationId)
        }).decodeSingle<GateScanResult>()
    }
}
