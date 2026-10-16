package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        applySystemBarPadding() // keep content out from under the status/navigation bars

        val backButton = findViewById<ImageButton>(R.id.backButton)

        val fullNameInput = findViewById<TextInputEditText>(R.id.fullNameInput)
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val confirmPasswordInput = findViewById<TextInputEditText>(R.id.confirmPasswordInput)
        val signUpButton = findViewById<Button>(R.id.signUpButton)

        // Red error messages above the password boxes
        val passwordError = findViewById<TextView>(R.id.passwordError)
        val confirmPasswordError = findViewById<TextView>(R.id.confirmPasswordError)
        hideErrorWhenTyping(passwordError, passwordInput)
        hideErrorWhenTyping(confirmPasswordError, passwordInput, confirmPasswordInput)

        // Back button
        backButton.setOnClickListener {
            finish() // the Login screen is already behind this one
        }

        // Sign up logic
        signUpButton.setOnClickListener {
            val fullName = fullNameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check both password rules and show every problem at once
            var hasError = false
            if (password.length < 6) {
                passwordError.showError("Password must be at least 6 characters")
                hasError = true
            } else {
                passwordError.hideError()
            }
            if (password != confirmPassword) {
                confirmPasswordError.showError("Passwords do not match")
                hasError = true
            } else {
                confirmPasswordError.hideError()
            }
            if (hasError) return@setOnClickListener

            signUpButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    // Create the user and store the full name in their metadata
                    SupabaseClient.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                        data = buildJsonObject {
                            put("full_name", fullName)
                        }
                    }

                    if (SupabaseClient.client.auth.currentSessionOrNull() != null) {
                        // Email confirmation is OFF: the user is already signed in
                        Toast.makeText(this@SignUpActivity, "Account created!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SignUpActivity, DashboardActivity::class.java))
                        finish()
                    } else {
                        // Email confirmation is ON: user must verify before signing in
                        Toast.makeText(
                            this@SignUpActivity,
                            "Account created! Check your email to confirm, then sign in.",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(Intent(this@SignUpActivity, LogInActivity::class.java))
                        finish()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@SignUpActivity,
                        "Sign up failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    signUpButton.isEnabled = true
                }
            }
        }
    }
}