
package com.example.railtap

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class TripHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_history)

        loadTripHistory()
    }

    private fun loadTripHistory() {
        val historyList = findViewById<LinearLayout>(R.id.historyList)
        val emptyMessage = findViewById<TextView>(R.id.historyEmptyMessage)

        lifecycleScope.launch {
            try {
                val trips = TripHistoryRepository().getTripHistory()

                historyList.removeAllViews()

                if (trips.isEmpty()) {
                    emptyMessage.text = "No trips yet"
                    emptyMessage.visibility = View.VISIBLE
                    return@launch
                }

                emptyMessage.visibility = View.GONE

                for (trip in trips) {
                    val tripCard = LinearLayout(this@TripHistoryActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(32, 24, 32, 24)
                        setBackgroundColor(
                            android.graphics.Color.WHITE
                        )
                    }

                    val routeText = TextView(this@TripHistoryActivity).apply {
                        text = trip.route
                        textSize = 16f
                        setTextColor(
                            android.graphics.Color.BLACK
                        )
                    }

                    val lineText = TextView(this@TripHistoryActivity).apply {
                        text = trip.line
                        textSize = 14f
                    }

                    val dateText = TextView(this@TripHistoryActivity).apply {
                        text = trip.date
                        textSize = 12f
                    }

                    val fareText = TextView(this@TripHistoryActivity).apply {
                        text = trip.fare
                        textSize = 12f
                    }

                    tripCard.addView(routeText)
                    tripCard.addView(lineText)
                    tripCard.addView(dateText)
                    tripCard.addView(fareText)

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                    params.setMargins(0, 0, 0, 16)
                    historyList.addView(tripCard, params)
                }

            } catch (e: Exception) {
                emptyMessage.text = "Unable to load trip history. Please try again."
                emptyMessage.visibility = View.VISIBLE
            }
        }
    }
}
