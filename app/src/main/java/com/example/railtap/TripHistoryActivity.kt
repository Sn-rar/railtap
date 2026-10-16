package com.example.railtap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Trip History page: a card for every paid trip, newest first.
 * Purple header like the Booking screen (layout_purple_header.xml); LegalBaseActivity fills in the title + back button.
 * Layouts: res/layout/activity_trip_history.xml + item_trip_history.xml (one card)
 */
class TripHistoryActivity : LegalBaseActivity() {
    override val screenLayout = R.layout.activity_trip_history
    override val screenTitle = "Trip History"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // White status bar icons (clock, battery) on the purple header
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }

    override fun onStart() {
        super.onStart()
        loadTripHistory()
    }

    private fun loadTripHistory() {
        val historyList = findViewById<LinearLayout>(R.id.historyList)
        val loading = findViewById<View>(R.id.historyLoading)

        loading.visibility = View.VISIBLE
        hideEmptyState()

        lifecycleScope.launch {
            try {
                val trips = TripHistoryRepository().getTripHistory()
                historyList.removeAllViews()

                if (trips.isEmpty()) {
                    showEmptyState("No trips yet", "Trips you pay for will show up here.")
                } else {
                    trips.forEach { historyList.addView(makeTripCard(it, historyList)) }
                }
            } catch (e: Exception) {
                showEmptyState(
                    "Couldn't load your trips",
                    "Check your internet connection and try again."
                )
            } finally {
                loading.visibility = View.GONE
            }
        }
    }

    /** Builds one trip card from item_trip_history.xml. */
    private fun makeTripCard(trip: TripHistoryItem, parent: LinearLayout): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_trip_history, parent, false)

        // If the repository didn't give origin/destination separately, split "A → B"
        val parts = trip.route.split("→").map { it.trim() }
        val origin = trip.origin.ifEmpty { parts.getOrElse(0) { "--" } }
        val destination = trip.destination.ifEmpty { parts.getOrElse(1) { "--" } }

        card.findViewById<TextView>(R.id.tripOrigin).text = origin
        card.findViewById<TextView>(R.id.tripDestination).text = destination
        card.findViewById<TextView>(R.id.tripDate).text = trip.date
        card.findViewById<TextView>(R.id.tripFare).text = trip.fare

        // Line pill: green for LRT-1, purple for LRT-2 (same as the receipt)
        val pill = card.findViewById<TextView>(R.id.tripLine)
        pill.text = trip.line
        if (trip.line.contains("1")) {
            pill.setBackgroundResource(R.drawable.bg_pill_green)
            pill.setTextColor(ContextCompat.getColor(this, R.color.green_line))
        }
        return card
    }

    private fun showEmptyState(title: String, message: String) {
        findViewById<TextView>(R.id.historyEmptyTitle).text = title
        findViewById<TextView>(R.id.historyEmptyMessage).text = message
        findViewById<View>(R.id.historyEmptyState).visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        findViewById<View>(R.id.historyEmptyState).visibility = View.GONE
    }
}
