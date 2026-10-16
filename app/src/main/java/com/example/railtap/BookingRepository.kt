package com.example.railtap

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal

// ---------- Models ----------

@Serializable
data class LineDto(
    @SerialName("line_id") val lineId: Int,
    val code: String,
    val name: String
)

@Serializable
data class Station(
    @SerialName("station_id") val stationId: Int,
    @SerialName("line_id") val lineId: Int,
    val name: String,
    val position: Int
)

@Serializable
data class DiscountDto(
    val type: String,
    val percent: Int
)

@Serializable
private data class FareDto(val fare: Double)

@Serializable
data class BookingResult(
    @SerialName("out_transaction_id") val transactionId: Int,
    @SerialName("out_reference_no") val referenceNo: String,
    @SerialName("out_total_amount") val totalAmount: Double
)

// ---------- Repository ----------

class BookingRepository(
    private val client: io.github.jan.supabase.SupabaseClient = SupabaseClient.client
) {

    /** All stations of a line, ordered from first to last stop. */
    suspend fun getStations(lineCode: String): List<Station> {
        val line = client.postgrest.from("lines")
            .select { filter { eq("code", lineCode) } }
            .decodeSingle<LineDto>()

        return client.postgrest.from("stations")
            .select { filter { eq("line_id", line.lineId) } }
            .decodeList<Station>()
            .sortedBy { it.position }
    }

    /** Discount type -> percent */
    suspend fun getDiscounts(): Map<String, Int> =
        client.postgrest.from("discounts")
            .select()
            .decodeList<DiscountDto>()
            .associate { it.type to it.percent }

    /** Base fare for a route. Checks A->B first, then B->A. Null if not found. */
    suspend fun getBaseFare(origin: Station, destination: Station): BigDecimal? {
        suspend fun lookup(from: Int, to: Int): FareDto? =
            client.postgrest.from("fare_matrix")
                .select {
                    filter {
                        eq("line_id", origin.lineId)
                        eq("origin_id", from)
                        eq("destination_id", to)
                    }
                    limit(1)
                }
                .decodeList<FareDto>()
                .firstOrNull()

        val row = lookup(origin.stationId, destination.stationId)
            ?: lookup(destination.stationId, origin.stationId)
        return row?.let { BigDecimal.valueOf(it.fare) }
    }

    /** Creates transaction + tickets + payment (all pending) in one server call. */
    suspend fun createBooking(
        lineCode: String,
        origin: Station,
        destination: Station,
        regular: Int,
        student: Int,
        senior: Int
    ): BookingResult =
        client.postgrest.rpc(
            "create_booking",
            buildJsonObject {
                put("p_line_code", lineCode)
                put("p_origin_id", origin.stationId)
                put("p_destination_id", destination.stationId)
                put("p_regular", regular)
                put("p_student", student)
                put("p_senior", senior)
            }
        ).decodeSingle<BookingResult>()

    /** Mark booking as paid after paypal succeeds (also starts the 24h ticket timer) */
    suspend fun markBookingPaid(transactionId: Int, paypalOrderId: String) {
        client.postgrest.rpc(
            "mark_booking_paid",
            buildJsonObject {
                put("p_transaction_id", transactionId)
                put("p_paypal_order_id", paypalOrderId)
            }
        )
    }
}