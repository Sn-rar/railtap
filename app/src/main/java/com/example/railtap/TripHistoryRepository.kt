
package com.example.railtap

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
private data class TripTransaction(
    @SerialName("transaction_id")
    val transactionId: Int,
    @SerialName("user_id")
    val userId: String,
    val status: String,
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
private data class TripTicket(
    @SerialName("transaction_id")
    val transactionId: Int,
    @SerialName("line_id")
    val lineId: Int,
    @SerialName("origin_id")
    val originId: Int,
    @SerialName("destination_id")
    val destinationId: Int,
    val fare: Double
)

data class TripHistoryStats(
    val totalTrips: Int = 0,
    val frequentRoute: String = "No trips yet" // now holds the most used LINE, e.g. "LRT-2"
)

data class TripHistoryItem(
    val route: String,
    val line: String,
    val date: String,
    val fare: String,
    val origin: String = "",
    val destination: String = ""
)

class TripHistoryRepository {

    private val client = SupabaseClient.client

    suspend fun getMonthlyStats(): TripHistoryStats {
        val userId = client.auth.currentSessionOrNull()
            ?.user?.id
            ?: return TripHistoryStats()

        val month = SimpleDateFormat(
            "yyyy-MM",
            Locale.getDefault()
        ).format(Date())

        val transactions = client.from("transactions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", "paid")
                }
            }
            .decodeList<TripTransaction>()
            .filter { it.createdAt.startsWith(month) }

        if (transactions.isEmpty()) {
            return TripHistoryStats()
        }

        val tickets = mutableListOf<TripTicket>()

        for (transaction in transactions) {
            val transactionTickets = client.from("tickets")
                .select {
                    filter {
                        eq("transaction_id", transaction.transactionId)
                    }
                }
                .decodeList<TripTicket>()

            tickets.addAll(transactionTickets)
        }

        // Most used line this month: count each paid booking once (not once per passenger)
        val frequentLineId = tickets
            .distinctBy { it.transactionId }      // one ticket per booking is enough
            .groupingBy { it.lineId }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val frequentRoute = when (frequentLineId) {
            1 -> "LRT-1"
            2 -> "LRT-2"
            null -> "No trips yet"
            else -> "Line $frequentLineId"
        }

        return TripHistoryStats(
            totalTrips = transactions.size,
            frequentRoute = frequentRoute
        )
    }

    suspend fun getTripHistory(): List<TripHistoryItem> {
        val userId = client.auth.currentSessionOrNull()
            ?.user?.id
            ?: return emptyList()

        val transactions = client.from("transactions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", "paid")
                }
            }
            .decodeList<TripTransaction>()
            .sortedByDescending { it.createdAt }

        if (transactions.isEmpty()) {
            return emptyList()
        }

        val stations = client.from("stations")
            .select()
            .decodeList<Station>()
            .associateBy { it.stationId }

        val history = mutableListOf<TripHistoryItem>()

        for (transaction in transactions) {
            val tickets = client.from("tickets")
                .select {
                    filter {
                        eq("transaction_id", transaction.transactionId)
                    }
                }
                .decodeList<TripTicket>()

            for (ticket in tickets) {
                val origin = stations[ticket.originId]?.name
                    ?: "Unknown station"

                val destination = stations[ticket.destinationId]?.name
                    ?: "Unknown station"

                val lineName = when (ticket.lineId) {
                    1 -> "LRT-1"
                    2 -> "LRT-2"
                    else -> "Train Line ${ticket.lineId}"
                }

                val formattedFare = "₱%.2f".format(
                    Locale.US,
                    ticket.fare
                )

                val formattedDate = try {
                    val inputFormats = listOf(
                        "yyyy-MM-dd'T'HH:mm:ssXXX", // e.g. 2026-10-16T05:48:39+00:00
                        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                        "yyyy-MM-dd'T'HH:mm:ssX",
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        "yyyy-MM-dd'T'HH:mm:ss'Z'"
                    )

                    var parsedDate: Date? = null

                    for (pattern in inputFormats) {
                        try {
                            val parser = SimpleDateFormat(
                                pattern,
                                Locale.US
                            ).apply {
                                isLenient = false
                                timeZone = TimeZone.getTimeZone("UTC")
                            }

                            // Remove the fraction of a second (".747733"), which SimpleDateFormat can't read
                            val cleaned = transaction.createdAt.replace(Regex("\\.\\d+"), "")
                            parsedDate = parser.parse(cleaned)

                            if (parsedDate != null) {
                                break
                            }
                        } catch (_: Exception) {
                            // Try the next timestamp format.
                        }
                    }

                    if (parsedDate != null) {
                        SimpleDateFormat(
                            "MMMM dd, yyyy '·' h:mm a",
                            Locale.ENGLISH
                        ).format(parsedDate)
                    } else {
                        transaction.createdAt
                    }
                } catch (_: Exception) {
                    transaction.createdAt
                }

                history.add(
                    TripHistoryItem(
                        route = "$origin → $destination",
                        line = lineName,
                        date = formattedDate,
                        fare = formattedFare,
                        origin = origin,
                        destination = destination
                    )
                )
            }
        }

        return history
    }
}
