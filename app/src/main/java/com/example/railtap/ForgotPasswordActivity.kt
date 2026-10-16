package com.example.railtap

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

// Forgot password: sends a reset link to their email
class ForgotPasswordActivity : AppCompatActivity() {

    companion object {
        // email from the login screen
        const val EXTRA_EMAIL = "extra_email"

        // must match the manifest
        const val RESET_REDIRECT_URL = "railtap://reset-password"
    }

    private val emailInput by lazy { findViewById<TextInputEditText>(R.id.emailInput) }
    private val emailError by lazy { findViewById<TextView>(R.id.emailError) }
    private val sendButton by lazy { findViewById<Button>(R.id.sendResetButton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        applySystemBarPadding()

        // prefill email from login
        intent.getStringExtra(EXTRA_EMAIL)?.let { emailInput.setText(it) }
        hideErrorWhenTyping(emailError, emailInput)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.backToLoginButton).setOnClickListener { finish() }
        sendButton.setOnClickListener { sendResetLink() }
        findViewById<View>(R.id.resendLink).setOnClickListener { sendResetLink() }
    }

    private fun sendResetLink() {
        val email = emailInput.text.toString().trim()

        if (email.isEmpty()) {
            emailError.showError("Please enter your email")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError.showError("Please enter a valid email")
            return
        }
        emailError.hideError()

        sendButton.isEnabled = false
        sendButton.text = "Sending…"

        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.resetPasswordForEmail(
                    email = email,
                    redirectUrl = RESET_REDIRECT_URL
                )
                showLinkSent(email)
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                val friendly = when {
                    msg.contains("rate limit", ignoreCase = true) ||
                        msg.contains("seconds", ignoreCase = true) ->
                        "Too many tries. Please wait a minute and try again."
                    else -> "Couldn't send the link. Check your internet and try again."
                }
                emailError.showError(friendly)
            } finally {
                sendButton.isEnabled = true
                sendButton.text = "Send Reset Link"
            }
        }
    }

    // show "check your email"
    private fun showLinkSent(email: String) {
        findViewById<View>(R.id.emailForm).visibility = View.GONE
        findViewById<View>(R.id.sentCard).visibility = View.VISIBLE
        findViewById<View>(R.id.backToLoginButton).visibility = View.VISIBLE
        findViewById<View>(R.id.resendLink).visibility = View.VISIBLE
        findViewById<TextView>(R.id.sentMessage).text =
            "We sent a reset link to $email. Open it on this phone to set a new password."
    }
}
