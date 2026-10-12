package com.example.railtap

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BookingActivity : AppCompatActivity() {

    companion object {
        // Key used to pass which line was picked ("LRT-1" or "LRT-2")
        const val EXTRA_LINE = "extra_line"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking)

        // Show which line the user picked in the top bar
        val line = intent.getStringExtra(EXTRA_LINE)
        findViewById<TextView>(R.id.tvToolbarTitle).text = when (line) {
            "LRT-1" -> "LRT-1 Ticket"
            "LRT-2" -> "LRT-2 Ticket"
            else -> "Buy Ticket"
        }

        // Back arrow closes this screen and returns to the previous one
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }
}
