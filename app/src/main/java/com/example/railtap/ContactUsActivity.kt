package com.example.railtap

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast

class ContactUsActivity : LegalBaseActivity() {
    override val screenLayout = R.layout.activity_contact_us
    override val screenTitle = "Contact Us"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tap the email address to open the user's mail app
        findViewById<TextView>(R.id.tvEmail).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:railtap.support@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "RailTap Support")
            }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }
    }
}