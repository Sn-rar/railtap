package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private val supabase = SupabaseManager.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        setupBottomNav(this, NavTab.HOME)

        // "Buy Ticket" buttons open the booking screen for that line
        findViewById<View>(R.id.greenLineButton).setOnClickListener { openBooking("LRT-1") }
        findViewById<View>(R.id.purpleLineButton).setOnClickListener { openBooking("LRT-2") }

        // Not signed in -> back to login
        lifecycleScope.launch {
            supabase.auth.awaitInitialization()
            if (supabase.auth.currentSessionOrNull() == null) {
                startActivity(Intent(this@DashboardActivity, LogInActivity::class.java))
                finish()
            }
        }
    }

    private fun openBooking(line: String) {
        val intent = Intent(this, BookingActivity::class.java)
        intent.putExtra(BookingActivity.EXTRA_LINE, line)
        startActivity(intent)
    }
}