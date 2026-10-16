
package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        applySystemBarPadding() // keep content out from under the status/navigation bars

        setupBottomNav(this, NavTab.HOME)

        // Buy Ticket buttons for both train lines
        findViewById<View>(R.id.greenLineButton).setOnClickListener {
            openBooking("LRT-1")
        }

        findViewById<View>(R.id.purpleLineButton).setOnClickListener {
            openBooking("LRT-2")
        }

        loadTripHistory()

        findViewById<TextView>(R.id.showAllHistory).setOnClickListener {
            startActivity(Intent(this, TripHistoryActivity::class.java))
        }
    }

    private fun loadTripHistory() {
        val totalTripsValue =
            findViewById<TextView>(R.id.totalTripsValue)

        val frequentRouteValue =
            findViewById<TextView>(R.id.frequentRouteValue)

        lifecycleScope.launch {
            try {
                val stats = TripHistoryRepository().getMonthlyStats()

                totalTripsValue.text = stats.totalTrips.toString()
                frequentRouteValue.text = stats.frequentRoute

            } catch (e: Exception) {
                totalTripsValue.text = "--"
                frequentRouteValue.text = "Unable to load"
            }
        }
    }

    private fun openBooking(line: String) {
        val intent = Intent(this, BookingActivity::class.java)
        intent.putExtra(BookingActivity.EXTRA_LINE, line)
        startActivity(intent)
    }
}
