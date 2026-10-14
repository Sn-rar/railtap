package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class LogInActivity : AppCompatActivity() {

    private val supabase = SupabaseManager.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 1. Find the views by their IDs from the XML layout
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val signInButton = findViewById<Button>(R.id.signInButton)
        val signUpLink = findViewById<TextView>(R.id.signUpLink)

        // Skip the login screen if a saved session already exists
        lifecycleScope.launch {
            supabase.auth.awaitInitialization()
            if (supabase.auth.currentSessionOrNull() != null) {
                goToDashboard()
            }
        }

        // 2. Set click listener for the Sign In button
        signInButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            signInButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    supabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    Toast.makeText(this@LogInActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                    goToDashboard()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@LogInActivity,
                        "Sign in failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    signInButton.isEnabled = true
                }
            }
        }

        // 3. Set click listener for the Sign Up text at the bottom
        signUpLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}