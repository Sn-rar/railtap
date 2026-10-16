package com.example.railtap

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// same columns as supabase
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
    @SerialName("used_at") val usedAt: String? = null,
    @SerialName("entered_at") val enteredAt: String? = null,
    @SerialName("entry_station_id") val entryStationId: Int? = null,
    @SerialName("exit_station_id") val exitStationId: Int? = null
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
    val expiresAtMillis: Long,
    val scannedAtMillis: Long? = null,
    val status: String = "unused",
    val enteredAtMillis: Long? = null
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
        get() = tickets.any { ticket ->
            val end = if (ticket.status == "entered") {
                (ticket.enteredAtMillis ?: 0L) + 4L * 60L * 60L * 1000L
            } else ticket.expiresAtMillis
            val remaining = end - System.currentTimeMillis()
            remaining in 1..3_600_000L
        }

    val expiryDisplay: String
        get() {
            if (tickets.any { it.status == "entered" }) return "ENTERED • Scan again at exit"
            val remaining = expiresAtTimestamp - System.currentTimeMillis()
            if (remaining <= 0L) return "Expired"
            val mins = (remaining + 59_999L) / 60_000L
            return "Expires in ${mins / 60} hr ${mins % 60} min"
        }

    val warningDisplay: String
        get() = if (tickets.any { it.status == "entered" }) {
            "Passenger entered. Use the SAME QR to exit the booked destination station (within 4 hours)."
        } else {
            "Your ticket is nearing its entry deadline. Enter before it expires!"
        }

}

// active + used tickets
data class TicketOverview(
    val active: List<TransitPass>,
    val scanned: List<TransitPass>
)

data class TicketUsageStatus(
    val status: String,
    val scannedAtMillis: Long?,
    val enteredAtMillis: Long?
)

// parse supabase timestamps
private fun parseSupabaseTime(iso: String): Long {
    // cut microseconds to millis
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
    // bad timestamp = not usable
    return 0L
}

// loads tickets (RLS only returns the user's own)
class TicketRepository {
    private val client = SupabaseClient.client

    // only usable passes
    suspend fun loadActivePasses(): List<TransitPass> = loadTicketOverview().active

    // refresh after the gate scans it
    suspend fun getTicketUsage(ticketId: Int): TicketUsageStatus? {
        val rows = client.postgrest.from("tickets")
            .select { filter { eq("ticket_id", ticketId) } }
            .decodeList<TicketDb>()
        val row = rows.firstOrNull() ?: return null
        return TicketUsageStatus(row.status, row.usedAt?.let(::parseSupabaseTime), row.enteredAt?.let(::parseSupabaseTime))
    }

    // active + scanned tickets
    suspend fun loadTicketOverview(): TicketOverview {
        val uid = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not signed in. Please sign in to view your tickets.")

        val paidTransactions = client.postgrest.from("transactions")
            .select { filter { eq("user_id", uid); eq("status", "paid") } }
            .decodeList<TransactionDb>()
        if (paidTransactions.isEmpty()) return TicketOverview(emptyList(), emptyList())
        val transactionMap = paidTransactions.associateBy { it.transactionId }

        // don't filter by 'unused' here or scanned ones disappear
        val dbTickets = client.postgrest.from("tickets").select().decodeList<TicketDb>()
        val now = System.currentTimeMillis()
        val relevant = dbTickets.filter { row ->
            row.transactionId in transactionMap && (
                (row.status == "unused" && row.usedAt == null &&
                    parseSupabaseTime(row.expiresAt) > now) ||
                (row.status == "entered" && row.enteredAt != null && row.usedAt == null) ||
                (row.status == "used" && row.usedAt != null)
            )
        }
        if (relevant.isEmpty()) return TicketOverview(emptyList(), emptyList())

        val lines = client.postgrest.from("lines").select().decodeList<LineDb>().associateBy { it.lineId }
        val stations = client.postgrest.from("stations").select().decodeList<StationDb>().associateBy { it.stationId }
        val discounts = client.postgrest.from("discounts").select().decodeList<DiscountDb>().associateBy { it.discountId }
        val paymentRefs = client.postgrest.from("payments")
            .select { filter { eq("status", "paid") } }
            .decodeList<PaymentDb>()
            .associateBy { it.transactionId }

        val activePasses = mutableListOf<TransitPass>()
        val scannedPasses = mutableListOf<TransitPass>()
        for ((transactionId, rows) in relevant.groupBy { it.transactionId }) {
            val transaction = transactionMap[transactionId] ?: continue
            val payment = paymentRefs[transactionId] ?: continue

            fun makePass(passengerRows: List<TicketDb>): TransitPass {
                val first = passengerRows.first()
                val code = lines[first.lineId]?.code ?: "LRT1"
                val lineLabel = if (code.replace("-", "").uppercase(Locale.ROOT) == "LRT2") {
                    "LRT-2"
                } else { "LRT-1" }
                return TransitPass(
                    transactionId = transactionId,
                    referenceNo = payment.referenceNo,
                    lineName = lineLabel,
                    origin = stations[first.originId]?.name ?: "Unknown",
                    destination = stations[first.destinationId]?.name ?: "Unknown",
                    tickets = passengerRows.sortedBy { it.ticketId }.map { row ->
                        PassengerTicket(
                            ticketId = row.ticketId,
                            fareType = when (discounts[row.discountId]?.type) {
                                "student" -> "Student"
                                "senior" -> "Senior Citizen"
                                "pwd" -> "PWD"
                                else -> "Regular"
                            },
                            fare = row.fare,
                            qrToken = row.qrToken,
                            expiresAtMillis = parseSupabaseTime(row.expiresAt),
                            scannedAtMillis = row.usedAt?.let(::parseSupabaseTime),
                            status = row.status,
                            enteredAtMillis = row.enteredAt?.let(::parseSupabaseTime)
                        )
                    },
                    purchasedAtMillis = parseSupabaseTime(transaction.createdAt)
                )
            }

            val activeRows = rows.filter { it.status == "unused" || it.status == "entered" }
            val scannedRows = rows.filter { it.status == "used" }
            if (activeRows.isNotEmpty()) activePasses.add(makePass(activeRows))
            if (scannedRows.isNotEmpty()) scannedPasses.add(makePass(scannedRows))
        }
        return TicketOverview(
            active = activePasses.sortedByDescending { it.purchasedAtMillis },
            scanned = scannedPasses.sortedByDescending { pass ->
                pass.tickets.maxOfOrNull { it.scannedAtMillis ?: 0L } ?: 0L
            }
        )
    }
}
