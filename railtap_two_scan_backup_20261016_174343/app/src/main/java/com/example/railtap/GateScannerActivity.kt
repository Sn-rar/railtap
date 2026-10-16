package com.example.railtap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
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

/** Demo scanner account: choose an entrance/exit station, then scan a passenger QR. */
class GateScannerActivity : AppCompatActivity() {
    private val repository = GateScannerRepository()
    private lateinit var camera: DecoratedBarcodeView
    private lateinit var stationSpinner: Spinner
    private lateinit var modeGroup: RadioGroup
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var hint: TextView
    private lateinit var again: MaterialButton
    private var stations: List<GateStation> = emptyList()
    private var mode = "entry"
    private var stationId: Int? = null
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
        stationSpinner = findViewById(R.id.gateStationSpinner)
        modeGroup = findViewById(R.id.gateScanModeGroup)
        status = findViewById(R.id.gateScanStatus)
        details = findViewById(R.id.gateScanDetails)
        hint = findViewById(R.id.gateScannerHint)
        again = findViewById(R.id.btnGateScanAgain)

        findViewById<View>(R.id.btnBackFromGateScanner).setOnClickListener { logOutScanner() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { logOutScanner() }
        })
        modeGroup.check(R.id.gateModeEntry)
        modeGroup.setOnCheckedChangeListener { _, checked ->
            if (processing) return@setOnCheckedChangeListener
            mode = if (checked == R.id.gateModeExit) "exit" else "entry"
            resetForNextScan()
        }
        stationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (processing) return
                stationId = stations.getOrNull(position - 1)?.stationId
                resetForNextScan()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { stationId = null; camera.pause() }
        }
        again.setOnClickListener { resetForNextScan() }
        again.isEnabled = false
        display("READY", "Choose your station, then scan a passenger's QR.")

        camera.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val token = result?.text?.trim() ?: return
                if (!waiting || processing || stationId == null) return
                waiting = false
                camera.pause()
                if (!Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$").matches(token)) {
                    display("INVALID_QR", "This is not a RailTap QR token.")
                    again.isEnabled = true
                    return
                }
                validate(token, stationId!!, mode)
            }
            override fun possibleResultPoints(resultPoints: List<ResultPoint>?) = Unit
        })
        loadStations()
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

    private fun loadStations() {
        lifecycleScope.launch {
            try {
                stations = repository.loadStations()
                val labels = mutableListOf("Select station...")
                labels.addAll(stations.map { it.label })
                stationSpinner.adapter = ArrayAdapter(this@GateScannerActivity,
                    android.R.layout.simple_spinner_dropdown_item, labels)
                stationSpinner.setSelection(0)
                stationId = null
                resetForNextScan()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                display("ERROR", "Could not load LRT stations: ${e.message}")
            }
        }
    }

    private fun resetForNextScan() {
        if (!::status.isInitialized || processing) return
        waiting = true
        again.isEnabled = false
        display("READY", if (stationId == null) {
            "Select your station above to activate the camera."
        } else {
            "${if (mode == "entry") "ENTRANCE" else "EXIT"} at " +
                (stations.firstOrNull { it.stationId == stationId }?.label ?: "station") +
                ". Point the camera at the passenger QR."
        })
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
        if (cameraAllowed && waiting && !processing && stationId != null) camera.resume()
        else camera.pause()
    }

    private fun validate(token: String, chosenStation: Int, chosenMode: String) {
        processing = true
        stationSpinner.isEnabled = false
        modeGroup.isEnabled = false
        findViewById<View>(R.id.gateModeEntry).isEnabled = false
        findViewById<View>(R.id.gateModeExit).isEnabled = false
        display("CHECKING", "Verifying ${chosenMode.uppercase(Locale.US)} scan securely in Supabase...")
        lifecycleScope.launch {
            try {
                val result = repository.scan(token, chosenMode, chosenStation)
                val text = when (result.scanResult.uppercase(Locale.US)) {
                    "ENTRY_SUCCESS" -> "ENTRY SUCCESSFUL. Ticket #${result.ticketId} is now ENTERED. Passenger must retain the same QR for exit."
                    "EXIT_SUCCESS" -> "EXIT SUCCESSFUL. Ticket #${result.ticketId} journey is COMPLETED."
                    "ALREADY_ENTERED" -> "Ticket #${result.ticketId} was already scanned for entrance. Use EXIT mode at its destination."
                    "ALREADY_USED" -> "Ticket #${result.ticketId} was already scanned at exit."
                    "NOT_ENTERED" -> "This ticket has not been scanned at the entrance yet."
                    "WRONG_STATION" -> result.detail ?: "Wrong station selected. Check the ticket's origin/destination."
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
                stationSpinner.isEnabled = true
                modeGroup.isEnabled = true
                findViewById<View>(R.id.gateModeEntry).isEnabled = true
                findViewById<View>(R.id.gateModeExit).isEnabled = true
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
        hint.text = "Scanner account only • ${mode.uppercase(Locale.US)} mode • Station selection is for demonstration"
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
