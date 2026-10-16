package com.example.railtap

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QRPassActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_TRANSACTION_ID = "railtap_transaction_id"
        fun openIntent(context: Context, transactionId: Int): Intent =
            Intent(context, QRPassActivity::class.java).putExtra(EXTRA_TRANSACTION_ID, transactionId)
    }

    private val repo = TicketRepository()
    private val expiryHandler = Handler(Looper.getMainLooper())
    private val expiryCheck = object : Runnable {
        override fun run() {
            val current = pass?.tickets?.getOrNull(selected)
            if (current != null && !scannedTicketTimes.containsKey(current.ticketId) &&
                current.expiresAtMillis <= System.currentTimeMillis()) {
                findViewById<ImageView>(R.id.imgQRCode).setImageDrawable(null)
                Toast.makeText(this@QRPassActivity, "This QR pass has expired", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            checkCurrentTicketStatus()
            expiryHandler.postDelayed(this, 8_000L)
        }
    }
    private var pass: TransitPass? = null
    private var selected = 0
    private val scannedTicketTimes = mutableMapOf<Int, Long>()
    private var statusJob: Job? = null
    private val peso = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy, h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr)
        applySystemBarPadding()
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnNextPassenger).setOnClickListener {
            val currentPass = pass ?: return@setOnClickListener
            if (currentPass.tickets.isNotEmpty()) {
                selected = (selected + 1) % currentPass.tickets.size
                showPassenger()
                checkCurrentTicketStatus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        expiryHandler.removeCallbacks(expiryCheck)
        expiryHandler.postDelayed(expiryCheck, 10_000L)
        val transactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)
        if (transactionId == -1) {
            Toast.makeText(this, "Missing ticket reference", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // Re-check payment, usage and expiry every time this screen opens.
        lifecycleScope.launch {
            try {
                pass = repo.loadActivePasses().firstOrNull { it.transactionId == transactionId }
                if (pass == null || pass?.tickets.isNullOrEmpty()) {
                    Toast.makeText(this@QRPassActivity, "No active paid tickets for this booking", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }
                selected = selected.coerceIn(0, pass!!.tickets.lastIndex)
                showPassenger()
                checkCurrentTicketStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Toast.makeText(this@QRPassActivity, "Unable to load QR: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        expiryHandler.removeCallbacks(expiryCheck)
        super.onPause()
    }

    private fun showPassenger() {
        val group = pass ?: return
        if (group.tickets.isEmpty()) return
        val person = group.tickets[selected]
        val alreadyScanned = scannedTicketTimes.containsKey(person.ticketId)
        if (!alreadyScanned && person.expiresAtMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, "Ticket expired. Please refresh.", Toast.LENGTH_LONG).show()
            findViewById<ImageView>(R.id.imgQRCode).setImageDrawable(null)
            finish()
            return
        }
        val lrt1 = group.lineName == "LRT-1"
        val color = Color.parseColor(if (lrt1) "#00875A" else "#6B56C8")
        findViewById<TextView>(R.id.lineBadge).apply {
            text = group.lineName
            backgroundTintList = ColorStateList.valueOf(color)
        }
        findViewById<TextView>(R.id.refNumber).text = "Ref # ${group.referenceNo}"
        findViewById<TextView>(R.id.tvOrigin).text = group.origin.uppercase(Locale.getDefault())
        findViewById<TextView>(R.id.tvDestination).text = group.destination.uppercase(Locale.getDefault())
        findViewById<ImageView>(R.id.routeArrow).setColorFilter(color)
        findViewById<TextView>(R.id.cardPassengerTitle).text = if (alreadyScanned) {
            "Passenger ${selected + 1} — SCANNED"
        } else { "Passenger ${selected + 1}" }
        findViewById<TextView>(R.id.cardFareType).text = "${person.fareType} Fare | Single Journey"
        findViewById<TextView>(R.id.cardFareBadge).apply {
            text = if (alreadyScanned) "SCANNED" else when (person.fareType) {
                "Regular" -> "Standard"
                else -> person.fareType
            }
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(
                if (alreadyScanned) "#00875A" else "#6B56C8"
            ))
        }
        findViewById<TextView>(R.id.tvTrainFare).text = peso.format(person.fare)
        findViewById<TextView>(R.id.lblValidUntil).text = if (alreadyScanned) "SCANNED AT:" else "VALID UNTIL:"
        findViewById<TextView>(R.id.tvValidUntil).text = if (alreadyScanned) {
            scannedTicketTimes[person.ticketId]?.let { dateFormat.format(Date(it)) } ?: "Scanned"
        } else { dateFormat.format(Date(person.expiresAtMillis)) }
        val qrImage = findViewById<ImageView>(R.id.imgQRCode)
        val qrFrame = qrImage.parent as FrameLayout
        var scannedLabel = qrFrame.findViewWithTag<TextView>("railtap_scanned_qr_overlay")
        if (scannedLabel == null) {
            scannedLabel = TextView(this).apply {
                tag = "railtap_scanned_qr_overlay"
                text = "✓\nSCANNED"
                textSize = 26f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#00875A"))
                visibility = View.GONE
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E9F7EF"))
                    cornerRadius = dp(12).toFloat()
                }
            }
            qrFrame.addView(scannedLabel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        scannedLabel.visibility = if (alreadyScanned) View.VISIBLE else View.GONE
        qrImage.visibility = if (alreadyScanned) View.INVISIBLE else View.VISIBLE
        if (alreadyScanned) qrImage.setImageDrawable(null)
        else qrImage.setImageBitmap(createQr(person.qrToken))
        val next = (selected + 1) % group.tickets.size
        findViewById<MaterialButton>(R.id.btnNextPassenger).apply {
            if (group.tickets.size == 1) {
                text = "BACK TO MY TICKETS"
                setOnClickListener { finish() }
            } else {
                text = if (next == 0) "FIRST PASSENGER QR (1/${group.tickets.size})  →"
                       else "NEXT PASSENGER QR (${next + 1}/${group.tickets.size})  →"
                setOnClickListener {
                    selected = next
                    showPassenger()
                    checkCurrentTicketStatus()
                }
            }
        }
        populateTabs(group)
    }

    private fun populateTabs(group: TransitPass) {
        val container = findViewById<LinearLayout>(R.id.passengerTabsContainer)
        container.removeAllViews()
        group.tickets.indices.forEach { index ->
            val active = index == selected
            val tab = TextView(this).apply {
                val used = scannedTicketTimes.containsKey(group.tickets[index].ticketId)
                text = if (used) "Passenger ${index + 1} ✓" else "Passenger ${index + 1}"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor(if (active) "#FFFFFF" else "#605783"))
                textSize = 12f
                setTypeface(typeface, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                background = GradientDrawable().apply {
                    cornerRadius = 100f
                    setColor(Color.parseColor(if (active) "#6B56C8" else "#EAE5F7"))
                }
                setPadding(dp(17), dp(9), dp(17), dp(9))
                setOnClickListener {
                    selected = index
                    showPassenger()
                    checkCurrentTicketStatus()
                }
            }
            container.addView(tab, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(37)
            ).apply { marginEnd = dp(7) })
        }
    }

    /** Read-only status check; Supabase alone decides when a ticket was scanned. */
    private fun checkCurrentTicketStatus() {
        val ticket = pass?.tickets?.getOrNull(selected) ?: return
        if (statusJob?.isActive == true) return
        val ticketId = ticket.ticketId
        statusJob = lifecycleScope.launch {
            try {
                val usage = repo.getTicketUsage(ticketId) ?: return@launch
                if (usage.status == "used") {
                    if (!scannedTicketTimes.containsKey(ticketId)) {
                        scannedTicketTimes[ticketId] = usage.scannedAtMillis ?: System.currentTimeMillis()
                        if (pass?.tickets?.getOrNull(selected)?.ticketId == ticketId) {
                            showPassenger()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("RailTapQR", "Could not refresh scan status", e)
            }
        }
    }

    private fun createQr(token: String): Bitmap {
        // Demo scanner credential: the existing database's unique qr_token.
        // Real gate validation must happen on a trusted backend, not in this app.
        val side = 512
        val matrix = QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, side, side,
            mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(side * side)
        for (y in 0 until side) for (x in 0 until side) {
            pixels[y * side + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
