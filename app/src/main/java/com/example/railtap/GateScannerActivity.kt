package com.example.railtap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

// gate scanner: pick station, then scan QR
class GateScannerActivity : AppCompatActivity() {
    private val repository = GateScannerRepository()
    private lateinit var camera: DecoratedBarcodeView
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var hint: TextView
    private lateinit var again: MaterialButton
    private var cameraAllowed = false
    private var waiting = true
    private var processing = false

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraAllowed = granted
        if (granted) resumeCameraIfReady()
        else display("ERROR", "Camera permission is required to scan tickets.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ScannerOnlyRole.isScanner()) { openLogin(); return }
        setContentView(R.layout.activity_qr_scanner_gate)
        applySystemBarPadding()
        camera = findViewById(R.id.gateBarcodeScanner)
        status = findViewById(R.id.gateScanStatus)
        details = findViewById(R.id.gateScanDetails)
        hint = findViewById(R.id.gateScannerHint)
        again = findViewById(R.id.btnGateScanAgain)

        findViewById<View>(R.id.btnBackFromGateScanner).setOnClickListener { logOutScanner() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { logOutScanner() }
        })
        again.setOnClickListener { resetForNextScan() }
        again.isEnabled = false
        display("READY", "Scan the same QR twice: first scan enters, second completes the journey.")

        camera.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val token = result?.text?.trim() ?: return
                if (!waiting || processing) return
                waiting = false
                camera.pause()
                if (!Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$").matches(token)) {
                    display("INVALID_QR", "This is not a RailTap QR token.")
                    again.isEnabled = true
                    return
                }
                validate(token)
            }
            override fun possibleResultPoints(resultPoints: List<ResultPoint>?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        if (::camera.isInitialized) {
            if (!ScannerOnlyRole.isScanner()) openLogin() else requestCamera()
        }
    }

    override fun onPause() {
        if (::camera.isInitialized) camera.pause()
        super.onPause()
    }

    private fun resetForNextScan() {
        if (!::status.isInitialized || processing) return
        waiting = true
        again.isEnabled = false
        display("READY", "Point the camera at a RailTap QR. Supabase automatically decides ENTRY or EXIT.")
        resumeCameraIfReady()
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraAllowed = true
            resumeCameraIfReady()
        } else askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun resumeCameraIfReady() {
        if (!::camera.isInitialized) return
        if (cameraAllowed && waiting && !processing ) camera.resume()
        else camera.pause()
    }

    private fun validate(token: String) {
        processing = true
        display("CHECKING", "Checking the ticket and its journey status in Supabase...")
        lifecycleScope.launch {
            try {
                val result = repository.scan(token)
                val text = when (result.scanResult.uppercase(Locale.US)) {
                    "ENTRY_SUCCESS" -> "ENTRY SUCCESSFUL. Ticket #${result.ticketId} is now ENTERED. Passenger must retain the same QR for exit."
                    "EXIT_SUCCESS" -> "EXIT SUCCESSFUL. Ticket #${result.ticketId} journey is COMPLETED."
                    "ALREADY_ENTERED" -> "Ticket #${result.ticketId} was already scanned for entrance."
                    "ALREADY_USED" -> "Ticket #${result.ticketId} was already scanned at exit."
                    "INVALID_JOURNEY" -> "This ticket has an incomplete journey record."
                    "INVALID_ROUTE" -> result.detail ?: "The ticket has an invalid booked route."
                    "JOURNEY_TIMEOUT" -> "Entry was more than 4 hours ago; staff assistance required."
                    "EXPIRED" -> "Ticket expired before entrance."
                    "UNPAID" -> "Ticket payment is not confirmed."
                    "NOT_FOUND" -> "This QR is not a RailTap ticket."
                    else -> result.detail ?: "Scan rejected by Supabase."
                }
                display(result.scanResult, text)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                display("ERROR", "Could not scan: ${e.message ?: "Check your connection and scanner sign-in."}")
            } finally {
                processing = false
                again.isEnabled = true
            }
        }
    }

    private fun display(code: String, message: String) {
        if (!::status.isInitialized) return
        val success = code == "ENTRY_SUCCESS" || code == "EXIT_SUCCESS"
        val color = if (success) Color.parseColor("#00875A") else if (code == "READY")
            Color.parseColor("#0B1A30") else if (code == "CHECKING")
            Color.parseColor("#6B46C1") else Color.parseColor("#B91C1C")
        status.text = when (code) {
            "ENTRY_SUCCESS" -> "✓ ENTRY SUCCESSFUL"
            "EXIT_SUCCESS" -> "✓ JOURNEY COMPLETED"
            "ALREADY_ENTERED" -> "ALREADY ENTERED"
            "ALREADY_USED" -> "ALREADY SCANNED AT EXIT"
            "NOT_ENTERED" -> "ENTRY SCAN REQUIRED"
            "READY" -> "Ready to scan"
            "CHECKING" -> "Checking Supabase..."
            else -> code.replace('_', ' ')
        }
        status.setTextColor(color)
        details.text = message
        hint.text = "Scanner only • Automatic first/second scan • Booked route (demo)"
    }

    private fun logOutScanner() {
        camera.pause()
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signOut()
                openLogin()
            } catch (e: Exception) {
                Toast.makeText(this@GateScannerActivity, "Sign out failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openLogin() {
        startActivity(Intent(this, LogInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
