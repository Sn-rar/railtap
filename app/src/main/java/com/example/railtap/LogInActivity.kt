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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        applySystemBarPadding() // keep content out from under the status/navigation bars

        // 1. Find the views by their IDs from the XML layout
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val signInButton = findViewById<Button>(R.id.signInButton)
        val signUpLink = findViewById<TextView>(R.id.signUpLink)
        val passwordError = findViewById<TextView>(R.id.passwordError) // red text above the password box
        hideErrorWhenTyping(passwordError, emailInput, passwordInput)

        // 2. Set click listener for the Sign In button
        signInButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                passwordError.showError("Please enter your email and password")
                return@setOnClickListener
            }
            passwordError.hideError()

            signInButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    SupabaseClient.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    Toast.makeText(this@LogInActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                    val nextScreen = if (ScannerOnlyRole.isScanner()) {
                        GateScannerActivity::class.java
                    } else {
                        DashboardActivity::class.java
                    }
                    startActivity(Intent(this@LogInActivity, nextScreen).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                } catch (e: Exception) {
                    val msg = e.message.orEmpty()
                    when {
                        // Wrong email or password
                        msg.contains("invalid", ignoreCase = true) &&
                            msg.contains("credentials", ignoreCase = true) ->
                            passwordError.showError("Incorrect email or password")

                        // Account made but email not confirmed yet
                        msg.contains("not confirmed", ignoreCase = true) ->
                            passwordError.showError("Please confirm your email first, then sign in")

                        // Anything else (e.g. no internet)
                        else -> Toast.makeText(
                            this@LogInActivity,
                            "Sign in failed: $msg",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    signInButton.isEnabled = true
                }
            }
        }

        // "Forgot password?" opens the Forgot Password page (with the email pre-filled)
        findViewById<TextView>(R.id.forgotPasswordText).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java).apply {
                putExtra(ForgotPasswordActivity.EXTRA_EMAIL, emailInput.text.toString().trim())
            })
        }

        // 3. Set click listener for the Sign Up text at the bottom
        signUpLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }
}