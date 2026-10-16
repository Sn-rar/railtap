package com.example.railtap

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        applySystemBarPadding() // keep content out from under the status/navigation bars

        lifecycleScope.launch {
            // Show the splash for 3 seconds while checking for a saved login at the same time
            val minSplash = async { delay(3000) }

            // Supabase remembers the login on the phone after signing in.
            // Wait until it has finished loading that saved login (max 5 seconds).
            val status = withTimeoutOrNull(5000) {
                SupabaseClient.client.auth.sessionStatus.first { it != SessionStatus.Initializing }
            }
            minSplash.await()

            val loggedIn = when (status) {
                is SessionStatus.Authenticated -> true
                // Saved login exists but couldn't refresh (e.g. no internet): still let them in
                is SessionStatus.RefreshFailure -> true
                else -> SupabaseClient.client.auth.currentSessionOrNull() != null
            }

            // Gate scanner accounts -> QR scanner (Cat's ScannerOnlyRole).
            // Logged in before -> straight to Home. Otherwise -> Login screen.
            val next = when {
                loggedIn && ScannerOnlyRole.isScanner() -> GateScannerActivity::class.java
                loggedIn -> DashboardActivity::class.java
                else -> LogInActivity::class.java
            }
            // Fade into the next screen (no sliding), then remove the splash from the back stack
            startWithFade(Intent(this@SplashActivity, next), fadeMs = 300)
            finishWithoutAnimation()
        }
    }
}