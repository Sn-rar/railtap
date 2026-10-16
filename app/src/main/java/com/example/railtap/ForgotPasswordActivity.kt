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

/**
 * Forgot Password page.
 * The user types their email, and Supabase emails them a reset link.
 * Tapping that link on the phone opens ResetPasswordActivity (railtap://reset-password).
 * Layout: res/layout/activity_forgot_password.xml
 */
class ForgotPasswordActivity : AppCompatActivity() {

    companion object {
        /** Used by the Login screen to pre-fill the email the user already typed. */
        const val EXTRA_EMAIL = "extra_email"

        /** Where the link in the email sends the user (must match AndroidManifest.xml). */
        const val RESET_REDIRECT_URL = "railtap://reset-password"
    }

    private val emailInput by lazy { findViewById<TextInputEditText>(R.id.emailInput) }
    private val emailError by lazy { findViewById<TextView>(R.id.emailError) }
    private val sendButton by lazy { findViewById<Button>(R.id.sendResetButton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        applySystemBarPadding() // keep content out from under the status/navigation bars

        // Pre-fill the email from the Login screen, if there was one
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

    /** Swap the form for the "Check your email" message. */
    private fun showLinkSent(email: String) {
        findViewById<View>(R.id.emailForm).visibility = View.GONE
        findViewById<View>(R.id.sentCard).visibility = View.VISIBLE
        findViewById<View>(R.id.backToLoginButton).visibility = View.VISIBLE
        findViewById<View>(R.id.resendLink).visibility = View.VISIBLE
        findViewById<TextView>(R.id.sentMessage).text =
            "We sent a reset link to $email. Open it on this phone to set a new password."
    }
}
