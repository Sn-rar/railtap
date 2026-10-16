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
            if (current != null && current.expiresAtMillis <= System.currentTimeMillis()) {
                findViewById<ImageView>(R.id.imgQRCode).setImageDrawable(null)
                Toast.makeText(this@QRPassActivity, "This QR pass has expired", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            expiryHandler.postDelayed(this, 10_000L)
        }
    }
    private var pass: TransitPass? = null
    private var selected = 0
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
        if (person.expiresAtMillis <= System.currentTimeMillis()) {
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
        findViewById<TextView>(R.id.cardPassengerTitle).text = "Passenger ${selected + 1}"
        findViewById<TextView>(R.id.cardFareType).text = "${person.fareType} Fare | Single Journey"
        findViewById<TextView>(R.id.cardFareBadge).text = when (person.fareType) {
            "Regular" -> "Standard"
            else -> person.fareType
        }
        findViewById<TextView>(R.id.tvTrainFare).text = peso.format(person.fare)
        findViewById<TextView>(R.id.tvValidUntil).text = dateFormat.format(Date(person.expiresAtMillis))
        findViewById<ImageView>(R.id.imgQRCode).setImageBitmap(createQr(person.qrToken))
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
                text = "Passenger ${index + 1}"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor(if (active) "#FFFFFF" else "#605783"))
                textSize = 12f
                setTypeface(typeface, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                background = GradientDrawable().apply {
                    cornerRadius = 100f
                    setColor(Color.parseColor(if (active) "#6B56C8" else "#EAE5F7"))
                }
                setPadding(dp(17), dp(9), dp(17), dp(9))
                setOnClickListener { selected = index; showPassenger() }
            }
            container.addView(tab, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(37)
            ).apply { marginEnd = dp(7) })
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
