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
        applySystemBarPadding()

        lifecycleScope.launch {
            // show splash for 3s while checking login
            val minSplash = async { delay(3000) }

            // wait for the saved session to load (max 5s)
            val status = withTimeoutOrNull(5000) {
                SupabaseClient.client.auth.sessionStatus.first { it != SessionStatus.Initializing }
            }
            minSplash.await()

            val loggedIn = when (status) {
                is SessionStatus.Authenticated -> true
                // offline but has a session, let them in
                is SessionStatus.RefreshFailure -> true
                else -> SupabaseClient.client.auth.currentSessionOrNull() != null
            }

            // scanner -> gate scanner, logged in -> home, else login
            val next = when {
                loggedIn && ScannerOnlyRole.isScanner() -> GateScannerActivity::class.java
                loggedIn -> DashboardActivity::class.java
                else -> LogInActivity::class.java
            }
            startWithFade(Intent(this@SplashActivity, next), fadeMs = 300)
            finishWithoutAnimation()
        }
    }
}