package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
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

    private val supabase = SupabaseManager.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val backButton = findViewById<ImageButton>(R.id.backButton)

        val fullNameInput = findViewById<TextInputEditText>(R.id.fullNameInput)
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val confirmPasswordInput = findViewById<TextInputEditText>(R.id.confirmPasswordInput)
        val signUpButton = findViewById<Button>(R.id.signUpButton)

        // the back button
        backButton.setOnClickListener {
            startActivity(Intent(this, LogInActivity::class.java))
        }

        // the signup logic
        signUpButton.setOnClickListener {
            val fullName = fullNameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            signUpButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    // The full name is stored as user metadata on the auth user
                    // (read it later with supabase.auth.currentUserOrNull()?.userMetadata).
                    supabase.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                        data = buildJsonObject { put("full_name", fullName) }
                    }

                    if (supabase.auth.currentSessionOrNull() != null) {
                        // Email confirmation is OFF: user is signed in right away
                        Toast.makeText(this@SignUpActivity, "Account created!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SignUpActivity, DashboardActivity::class.java))
                        finish()
                    } else {
                        // Email confirmation is ON: user must confirm before signing in
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