package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Set New Password page.
 * Opens when the user taps the reset link from the email (railtap://reset-password).
 * The link logs them in temporarily so they can choose a new password.
 * Layout: res/layout/activity_reset_password.xml
 */
class ResetPasswordActivity : AppCompatActivity() {

    private val newPasswordInput by lazy { findViewById<TextInputEditText>(R.id.newPasswordInput) }
    private val confirmInput by lazy { findViewById<TextInputEditText>(R.id.confirmPasswordInput) }
    private val passwordError by lazy { findViewById<TextView>(R.id.passwordError) }
    private val confirmError by lazy { findViewById<TextView>(R.id.confirmPasswordError) }
    private val updateButton by lazy { findViewById<Button>(R.id.updatePasswordButton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)
        applySystemBarPadding() // keep content out from under the status/navigation bars

        // Read the login info inside the reset link
        SupabaseClient.client.handleDeeplinks(intent)

        hideErrorWhenTyping(passwordError, newPasswordInput)
        hideErrorWhenTyping(confirmError, newPasswordInput, confirmInput)

        findViewById<View>(R.id.backButton).setOnClickListener { goToLogin() }
        updateButton.setOnClickListener { updatePassword() }
    }

    // If this page is already open and the user taps a reset link again
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SupabaseClient.client.handleDeeplinks(intent)
    }

    private fun updatePassword() {
        val password = newPasswordInput.text.toString().trim()
        val confirm = confirmInput.text.toString().trim()

        // Check both rules and show every problem at once
        var hasError = false
        if (password.length < 6) {
            passwordError.showError("Password must be at least 6 characters")
            hasError = true
        } else passwordError.hideError()
        if (password != confirm) {
            confirmError.showError("Passwords do not match")
            hasError = true
        } else confirmError.hideError()
        if (hasError) return

        updateButton.isEnabled = false
        updateButton.text = "Updating…"

        lifecycleScope.launch {
            try {
                // Wait (max 8 seconds) until the reset link has logged the user in
                val ready = withTimeoutOrNull(8000) {
                    SupabaseClient.client.auth.sessionStatus.first { it is SessionStatus.Authenticated }
                }
                if (ready == null) {
                    confirmError.showError("This reset link is invalid or has expired. Please request a new one.")
                    return@launch
                }

                // Save the new password
                SupabaseClient.client.auth.updateUser { this.password = password }

                // Log out so they sign in fresh with the new password
                SupabaseClient.client.auth.signOut()
                Toast.makeText(
                    this@ResetPasswordActivity,
                    "Password updated! Please sign in with your new password.",
                    Toast.LENGTH_LONG
                ).show()
                goToLogin()
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                val friendly = when {
                    msg.contains("different from the old", ignoreCase = true) ||
                        msg.contains("same", ignoreCase = true) ->
                        "New password must be different from your old one"
                    msg.contains("expired", ignoreCase = true) || msg.contains("invalid", ignoreCase = true) ->
                        "This reset link is invalid or has expired. Please request a new one."
                    else -> "Couldn't update your password. Please try again."
                }
                confirmError.showError(friendly)
            } finally {
                updateButton.isEnabled = true
                updateButton.text = "Update Password"
            }
        }
    }

    /** Open the Login screen and clear everything behind it. */
    private fun goToLogin() {
        startActivity(Intent(this, LogInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
