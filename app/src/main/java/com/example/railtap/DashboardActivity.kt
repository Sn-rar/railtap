
package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
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

        // Profile picture circle (top right): tapping it opens the Account tab
        findViewById<View>(R.id.avatarCircle).setOnClickListener {
            findViewById<View>(R.id.navAccount).performClick() // same as tapping Account in the bottom bar
        }

        findViewById<TextView>(R.id.showAllHistory).setOnClickListener {
            startActivity(Intent(this, TripHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfilePicture()
    }

    /** Shows the user's profile picture (same as in Account) in the top-right circle. */
    private fun loadProfilePicture() {
        val avatar = findViewById<ImageView>(R.id.avatarCircle)

        // Show the saved copy instantly, then refresh it from Supabase
        ProfileAvatar.cached?.let { avatar.showProfilePicture(it) }
        lifecycleScope.launch {
            ProfileAvatar.load()?.let { avatar.showProfilePicture(it) }
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
