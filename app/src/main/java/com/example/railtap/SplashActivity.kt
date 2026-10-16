package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        applySystemBarPadding() // keep content out from under the status/navigation bars

        Handler(Looper.getMainLooper()).postDelayed({
            val nextScreen = if (ScannerOnlyRole.isScanner()) {
                GateScannerActivity::class.java
            } else {
                LogInActivity::class.java
            }
            startActivity(Intent(this, nextScreen))
            finish() // removes SplashActivity from the back stack
        }, 3000) // 3000ms = 3 seconds
    }
}