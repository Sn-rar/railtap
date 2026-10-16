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
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The same QR remains visible after ENTRY; only EXIT permanently consumes it. */
class QRPassActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_TRANSACTION_ID = "railtap_transaction_id"
        fun openIntent(context: Context, transactionId: Int): Intent =
            Intent(context, QRPassActivity::class.java).putExtra(EXTRA_TRANSACTION_ID, transactionId)
    }

    private val repo = TicketRepository()
    private val handler = Handler(Looper.getMainLooper())
    private var pass: TransitPass? = null
    private var selected = 0
    private val latest = mutableMapOf<Int, TicketUsageStatus>()
    private var statusJob: Job? = null
    private val peso = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    private val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    private val refresher = object : Runnable {
        override fun run() {
            refreshSelectedTicket()
            showPassenger()
            handler.postDelayed(this, 8_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr)
        applySystemBarPadding()
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresher)
        val transactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)
        if (transactionId == -1) {
            Toast.makeText(this, "Missing ticket reference", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                val group = repo.loadActivePasses().firstOrNull { it.transactionId == transactionId }
                if (group == null || group.tickets.isEmpty()) {
                    Toast.makeText(this@QRPassActivity, "No active QR tickets in this booking", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }
                pass = group
                for (t in group.tickets) latest[t.ticketId] = TicketUsageStatus(t.status, t.scannedAtMillis, t.enteredAtMillis)
                selected = selected.coerceIn(0, group.tickets.lastIndex)
                showPassenger()
                refresher.run()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                Toast.makeText(this@QRPassActivity, "Unable to load QR: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun usage(ticket: PassengerTicket): TicketUsageStatus =
        latest[ticket.ticketId] ?: TicketUsageStatus(ticket.status, ticket.scannedAtMillis, ticket.enteredAtMillis)

    private fun showPassenger() {
        val group = pass ?: return
        val person = group.tickets.getOrNull(selected) ?: return
        val live = usage(person)
        val state = live.status
        val finished = state == "used"
        val entered = state == "entered"
        val expired = state == "expired" || (state == "unused" && person.expiresAtMillis <= System.currentTimeMillis())
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
        findViewById<TextView>(R.id.cardFareBadge).apply {
            text = when {
                finished -> "COMPLETED"
                entered -> "ENTERED"
                expired -> "EXPIRED"
                else -> "READY TO ENTER"
            }
            backgroundTintList = ColorStateList.valueOf(when {
                finished -> Color.parseColor("#00875A")
                entered -> Color.parseColor("#1769AA")
                expired -> Color.parseColor("#B91C1C")
                else -> color
            })
        }
        findViewById<TextView>(R.id.tvTrainFare).text = peso.format(person.fare)
        val validCaption = findViewById<TextView>(R.id.lblValidUntil)
        val validValue = findViewById<TextView>(R.id.tvValidUntil)
        validCaption.text = when {
            finished -> "EXITED AT:"
            entered -> "ENTERED AT:"
            else -> "VALID TO ENTER UNTIL:"
        }
        validValue.text = when {
            finished -> live.scannedAtMillis?.let { dateFormat.format(Date(it)) } ?: "Journey completed"
            entered -> live.enteredAtMillis?.let { dateFormat.format(Date(it)) } ?: "Entry registered"
            else -> dateFormat.format(Date(person.expiresAtMillis))
        }

        val qrImage = findViewById<ImageView>(R.id.imgQRCode)
        val frame = qrImage.parent as FrameLayout
        var label = frame.findViewWithTag<TextView>("journey_qr_overlay")
        if (label == null) {
            label = TextView(this).apply {
                tag = "journey_qr_overlay"
                gravity = Gravity.CENTER
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#00875A"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EDF8F2"))
                    cornerRadius = dp(16).toFloat()
                }
            }
            frame.addView(label, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        val disableQr = finished || expired
        label.text = if (finished) "✓\nJOURNEY\nCOMPLETED" else "TICKET EXPIRED"
        label.visibility = if (disableQr) View.VISIBLE else View.GONE
        qrImage.visibility = if (disableQr) View.INVISIBLE else View.VISIBLE
        if (disableQr) qrImage.setImageDrawable(null)
        else qrImage.setImageBitmap(createQr(person.qrToken))

        val btn = findViewById<MaterialButton>(R.id.btnNextPassenger)
        if (group.tickets.size == 1) {
            btn.text = if (entered) "SHOW THIS QR AT THE EXIT GATE" else "BACK TO MY TICKETS"
            btn.isEnabled = !entered
            btn.setOnClickListener { finish() }
        } else {
            val next = (selected + 1) % group.tickets.size
            btn.isEnabled = true
            btn.text = "NEXT PASSENGER (${next + 1}/${group.tickets.size}) →"
            btn.setOnClickListener { selected = next; showPassenger(); refreshSelectedTicket() }
        }
        val tabs = findViewById<LinearLayout>(R.id.passengerTabsContainer)
        tabs.removeAllViews()
        for (i in group.tickets.indices) {
            val ticket = group.tickets[i]
            val stateLabel = when (usage(ticket).status) {
                "entered" -> " • IN"
                "used" -> " ✓"
                else -> ""
            }
            tabs.addView(TextView(this).apply {
                text = "Passenger ${i + 1}$stateLabel"
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(Color.parseColor(if (i == selected) "#FFFFFF" else "#605783"))
                background = GradientDrawable().apply {
                    cornerRadius = dp(30).toFloat()
                    setColor(Color.parseColor(if (i == selected) "#6B56C8" else "#EAE5F7"))
                }
                setPadding(dp(14), dp(7), dp(14), dp(7))
                setOnClickListener { selected = i; showPassenger(); refreshSelectedTicket() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(37)).apply {
                marginEnd = dp(6)
            })
        }
    }

    /** Read-only refresh. Supabase is the source of truth for entry/exit state. */
    private fun refreshSelectedTicket() {
        val ticket = pass?.tickets?.getOrNull(selected) ?: return
        if (statusJob?.isActive == true) return
        val ticketId = ticket.ticketId
        statusJob = lifecycleScope.launch {
            try {
                val newStatus = repo.getTicketUsage(ticketId) ?: return@launch
                if (latest[ticketId] != newStatus) {
                    latest[ticketId] = newStatus
                    showPassenger()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { Log.w("RailTapQR", "Could not refresh ticket state", e) }
        }
    }

    private fun createQr(token: String): Bitmap {
        val side = 512
        val matrix = QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, side, side,
            mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(side * side)
        for (y in 0 until side) for (x in 0 until side) {
            pixels[y * side + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
