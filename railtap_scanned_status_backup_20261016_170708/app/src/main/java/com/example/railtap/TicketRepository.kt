package com.example.railtap

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** DTOs exactly follow the existing RailTap Supabase columns. */
@Serializable
private data class TicketDb(
    @SerialName("ticket_id") val ticketId: Int,
    @SerialName("transaction_id") val transactionId: Int,
    @SerialName("line_id") val lineId: Int,
    @SerialName("origin_id") val originId: Int,
    @SerialName("destination_id") val destinationId: Int,
    @SerialName("discount_id") val discountId: Int,
    val fare: Double,
    @SerialName("qr_token") val qrToken: String,
    val status: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("used_at") val usedAt: String? = null
)

@Serializable
private data class TransactionDb(
    @SerialName("transaction_id") val transactionId: Int,
    @SerialName("user_id") val userId: String,
    @SerialName("total_amount") val totalAmount: Double,
    val status: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class StationDb(
    @SerialName("station_id") val stationId: Int,
    @SerialName("line_id") val lineId: Int,
    val name: String,
    val position: Int
)

@Serializable
private data class LineDb(
    @SerialName("line_id") val lineId: Int,
    val code: String,
    val name: String,
    val color: String? = null
)

@Serializable
private data class DiscountDb(
    @SerialName("discount_id") val discountId: Int,
    val type: String,
    val percent: Int
)

@Serializable
private data class PaymentDb(
    @SerialName("payment_id") val paymentId: Int,
    @SerialName("transaction_id") val transactionId: Int,
    val method: String,
    val amount: Double,
    val status: String,
    @SerialName("reference_no") val referenceNo: String,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("paypal_order_id") val paypalOrderId: String? = null
)

data class PassengerTicket(
    val ticketId: Int,
    val fareType: String,
    val fare: Double,
    val qrToken: String,
    val expiresAtMillis: Long
)

data class TransitPass(
    val transactionId: Int,
    val referenceNo: String,
    val lineName: String,
    val origin: String,
    val destination: String,
    val tickets: List<PassengerTicket>,
    val purchasedAtMillis: Long
) {
    val passengersCount: Int get() = tickets.size
    val expiresAtTimestamp: Long get() = tickets.minOfOrNull { it.expiresAtMillis } ?: 0L
    val isExpiringSoon: Boolean
        get() = (expiresAtTimestamp - System.currentTimeMillis()) in 1..3_600_000L

    val expiryDisplay: String
        get() {
            val remaining = expiresAtTimestamp - System.currentTimeMillis()
            if (remaining <= 0L) return "Expired"
            val mins = (remaining + 59_999L) / 60_000L
            val hr = mins / 60
            val min = mins % 60
            return when {
                hr > 0 -> "Expires in ${hr} hr ${min} min"
                else -> "Expires in ${mins} min"
            }
        }

    val warningDisplay: String
        get() {
            val remaining = expiresAtTimestamp - System.currentTimeMillis()
            val mins = ((remaining + 59_999L) / 60_000L).coerceAtLeast(0)
            val time = SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(Date(expiresAtTimestamp))
            return "Your pass expires in $mins minutes at $time. Use it before it expires!"
        }
}

/** Handles ISO timestamps from Postgres on Android API 24+, without java.time desugaring. */
private fun parseSupabaseTime(iso: String): Long {
    // PostgreSQL can return microseconds; SimpleDateFormat expects milliseconds.
    val text = iso.trim().replace(Regex("(\\.\\d{3})\\d+"), "$1")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX"
    )
    for (pattern in patterns) {
        try {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            val parsed = formatter.parse(text)
            if (parsed != null) return parsed.time
        } catch (_: Exception) { }
    }
    // Invalid timestamps should never become usable tickets.
    return 0L
}

/** Read-only ticket queries. Existing RLS policies restrict user records. */
class TicketRepository {
    private val client = SupabaseClient.client // same authenticated client used by login and booking

    suspend fun loadActivePasses(): List<TransitPass> {
        val uid = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not signed in. Please sign in to view your tickets.")

        // Only completed purchases are shown, never unpaid/pending bookings.
        val paidTransactions = client.postgrest.from("transactions")
            .select { filter { eq("user_id", uid); eq("status", "paid") } }
            .decodeList<TransactionDb>()
        if (paidTransactions.isEmpty()) return emptyList()
        val transactionMap = paidTransactions.associateBy { it.transactionId }

        // RLS ensures the signed-in user only receives their own tickets/payments.
        val dbTickets = client.postgrest.from("tickets")
            .select { filter { eq("status", "unused") } }
            .decodeList<TicketDb>()
        val now = System.currentTimeMillis()
        val eligible = dbTickets.filter { row ->
            row.transactionId in transactionMap && row.usedAt == null && parseSupabaseTime(row.expiresAt) > now
        }
        if (eligible.isEmpty()) return emptyList()

        val lines = client.postgrest.from("lines").select().decodeList<LineDb>().associateBy { it.lineId }
        val stations = client.postgrest.from("stations").select().decodeList<StationDb>().associateBy { it.stationId }
        val discounts = client.postgrest.from("discounts").select().decodeList<DiscountDb>().associateBy { it.discountId }
        val paymentRefs = client.postgrest.from("payments")
            .select { filter { eq("status", "paid") } }
            .decodeList<PaymentDb>()
            .associateBy { it.transactionId }

        return eligible.groupBy { it.transactionId }.mapNotNull { (transactionId, rows) ->
            val transaction = transactionMap[transactionId] ?: return@mapNotNull null
            if (paymentRefs[transactionId] == null) return@mapNotNull null
            val first = rows.first()
            val lineCode = lines[first.lineId]?.code ?: "LRT1"
            val lineLabel = when (lineCode.replace("-", "").uppercase(Locale.ROOT)) {
                "LRT2" -> "LRT-2"
                else -> "LRT-1"
            }
            val passengerTickets = rows.sortedBy { it.ticketId }.map { ticket ->
                PassengerTicket(
                    ticketId = ticket.ticketId,
                    fareType = when (discounts[ticket.discountId]?.type) {
                        "student" -> "Student"
                        "senior" -> "Senior Citizen"
                        "pwd" -> "PWD"
                        else -> "Regular"
                    },
                    fare = ticket.fare,
                    qrToken = ticket.qrToken,
                    expiresAtMillis = parseSupabaseTime(ticket.expiresAt)
                )
            }
            TransitPass(
                transactionId = transactionId,
                referenceNo = paymentRefs[transactionId]?.referenceNo ?: "TXN-$transactionId",
                lineName = lineLabel,
                origin = stations[first.originId]?.name ?: "Unknown",
                destination = stations[first.destinationId]?.name ?: "Unknown",
                tickets = passengerTickets,
                purchasedAtMillis = parseSupabaseTime(transaction.createdAt)
            )
        }.sortedByDescending { it.purchasedAtMillis }
    }
}
