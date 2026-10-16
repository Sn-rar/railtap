package com.example.railtap

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.auth.auth
import android.widget.Toast
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Staff-only QR gate scanner, opened immediately after scanner-role login.
 * Supabase authorizes redemption using the server-issued app_metadata claim.
 * A VALID result updates the existing tickets.status/used_at fields for real.
 */
class GateScannerActivity : AppCompatActivity() {
    private val repository = GateScannerRepository()
    private lateinit var scanner: DecoratedBarcodeView
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var hint: TextView
    private lateinit var again: MaterialButton
    private var waiting = true
    private var allowed = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        allowed = granted
        if (granted) startCameraIfReady() else {
            display("CAMERA REQUIRED", "Grant camera access in Android settings to scan.")
            again.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ScannerOnlyRole.isScanner()) {
            startActivity(Intent(this, LogInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        setContentView(R.layout.activity_qr_scanner_gate)
        applySystemBarPadding()
        scanner = findViewById(R.id.gateBarcodeScanner)
        status = findViewById(R.id.gateScanStatus)
        details = findViewById(R.id.gateScanDetails)
        hint = findViewById(R.id.gateScannerHint)
        again = findViewById(R.id.btnGateScanAgain)

        findViewById<View>(R.id.btnBackFromGateScanner).setOnClickListener { logOutScanner() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { logOutScanner() }
        })
        again.setOnClickListener {
            waiting = true
            again.isEnabled = false
            display("READY", "Point the camera at a RailTap passenger QR displayed on another phone.")
            startCameraIfReady()
        }
        again.isEnabled = false
        display("READY", "Point the camera at a RailTap passenger QR displayed on another phone.")

        scanner.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val token = result?.text?.trim() ?: return
                if (!waiting) return
                waiting = false
                scanner.pause()
                if (!Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$").matches(token)) {
                    display("INVALID QR", "This QR is not a RailTap ticket UUID.")
                    again.isEnabled = true
                    return
                }
                redeem(token)
            }
            override fun possibleResultPoints(resultPoints: List<ResultPoint>?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        if (::scanner.isInitialized) {
            if (!ScannerOnlyRole.isScanner()) {
                startActivity(Intent(this, LogInActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } else {
                requestCamera()
            }
        }
    }

    override fun onPause() {
        if (::scanner.isInitialized) scanner.pause()
        super.onPause()
    }

    private fun logOutScanner() {
        scanner.pause()
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signOut()
                startActivity(Intent(this@GateScannerActivity, LogInActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@GateScannerActivity,
                    "Could not sign out: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            allowed = true
            startCameraIfReady()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraIfReady() {
        if (!::scanner.isInitialized || !waiting) return
        if (!allowed) {
            requestCamera()
        } else {
            scanner.resume()
        }
    }

    private fun redeem(token: String) {
        display("CHECKING", "Checking payment, expiry, scanner permission, and one-time usage…")
        lifecycleScope.launch {
            try {
                val result = repository.redeem(token)
                val code = result.scanResult.uppercase(Locale.US)
                val message = when (code) {
                    "VALID" -> "Ticket #${result.ticketId} accepted. It has been marked USED in Supabase."
                    "ALREADY_USED" -> "Ticket #${result.ticketId} was already redeemed; second scan rejected."
                    "EXPIRED" -> "Ticket #${result.ticketId} has expired and cannot be used."
                    "UNPAID" -> "Payment for this ticket has not been confirmed."
                    "NOT_FOUND" -> "The QR token does not match a RailTap ticket."
                    else -> result.detail ?: "Ticket rejected."
                }
                display(code, message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val error = e.message.orEmpty()
                val message = when {
                    error.contains("42501", true) || error.contains("Scanner authorization required", true) ->
                        "This account is not authorized as a gate scanner. Ask a project admin to assign the scanner role, then sign out and back in."
                    error.contains("PGRST202", true) || error.contains("redeem_ticket_at_gate", true) ->
                        "The protected redemption RPC is missing. Install the included Supabase SQL first."
                    else -> "Could not validate QR: ${e.message ?: "Check your connection."}"
                }
                display("ERROR", message)
            } finally {
                again.isEnabled = true
            }
        }
    }

    private fun display(code: String, message: String) {
        if (!::status.isInitialized) return
        val (title, color) = when (code.uppercase(Locale.US)) {
            "VALID" -> "VALID — Entry accepted" to Color.parseColor("#00875A")
            "ALREADY_USED" -> "ALREADY USED — Rejected" to Color.parseColor("#B91C1C")
            "EXPIRED" -> "EXPIRED — Rejected" to Color.parseColor("#B91C1C")
            "UNPAID" -> "UNPAID — Rejected" to Color.parseColor("#B91C1C")
            "NOT_FOUND", "INVALID QR" -> "INVALID QR — Rejected" to Color.parseColor("#B91C1C")
            "CHECKING" -> "Checking Supabase…" to Color.parseColor("#6B46C1")
            "READY" -> "Ready to scan" to Color.parseColor("#0B1A30")
            else -> "Scan error" to Color.parseColor("#B91C1C")
        }
        status.text = title
        status.setTextColor(color)
        details.text = message
        hint.text = if (code.uppercase(Locale.US) == "VALID") {
            "This really changed your ticket status in Supabase. Scanning it again must fail."
        } else {
            "Requires an authorized gate-scanner account and an internet connection."
        }
    }
}
