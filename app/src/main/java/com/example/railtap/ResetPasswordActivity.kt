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

// Set new password (opened from the reset link in the email)
class ResetPasswordActivity : AppCompatActivity() {

    private val newPasswordInput by lazy { findViewById<TextInputEditText>(R.id.newPasswordInput) }
    private val confirmInput by lazy { findViewById<TextInputEditText>(R.id.confirmPasswordInput) }
    private val passwordError by lazy { findViewById<TextView>(R.id.passwordError) }
    private val confirmError by lazy { findViewById<TextView>(R.id.confirmPasswordError) }
    private val updateButton by lazy { findViewById<Button>(R.id.updatePasswordButton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)
        applySystemBarPadding()

        // log in using the link
        SupabaseClient.client.handleDeeplinks(intent)

        hideErrorWhenTyping(passwordError, newPasswordInput)
        hideErrorWhenTyping(confirmError, newPasswordInput, confirmInput)

        findViewById<View>(R.id.backButton).setOnClickListener { goToLogin() }
        updateButton.setOnClickListener { updatePassword() }
    }

    // link tapped again while open
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SupabaseClient.client.handleDeeplinks(intent)
    }

    private fun updatePassword() {
        val password = newPasswordInput.text.toString().trim()
        val confirm = confirmInput.text.toString().trim()

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
                // wait for the link to log them in
                val ready = withTimeoutOrNull(8000) {
                    SupabaseClient.client.auth.sessionStatus.first { it is SessionStatus.Authenticated }
                }
                if (ready == null) {
                    confirmError.showError("This reset link is invalid or has expired. Please request a new one.")
                    return@launch
                }

                SupabaseClient.client.auth.updateUser { this.password = password }

                // log out so they sign in again
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

    private fun goToLogin() {
        startActivity(Intent(this, LogInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
