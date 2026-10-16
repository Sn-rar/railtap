package com.example.railtap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

class BookingActivity : AppCompatActivity() {

    companion object {
        // Key used to pass which line was picked ("LRT-1" or "LRT-2")
        const val EXTRA_LINE = "extra_line"

        // Keys for the next (payment) screen
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_REFERENCE_NO = "extra_reference_no"
        const val EXTRA_TOTAL = "extra_total"

        private const val MAX_PASSENGERS = 10
        private const val MINUTES_PER_STOP = 2 // rough estimate for the travel-time label
    }

    private val repo = BookingRepository()
    private val peso: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("en", "PH"))

    // ---- State ----
    private var lineCode = ""
    private var stations: List<Station> = emptyList()
    private var origin: Station? = null
    private var destination: Station? = null
    private var baseFare: BigDecimal? = null
    private var discounts: Map<String, Int> =
        mapOf("none" to 0, "student" to 50, "senior" to 50) // overwritten from DB
    private var countRegular = 0
    private var countStudent = 0
    private var countSenior = 0
    private var isSubmitting = false
    private var pendingBooking: BookingResult? = null // booking waiting for PayPal

    // ---- Views ----
    private val tvOrigin by lazy { findViewById<TextView>(R.id.tvBookingOrigin) }
    private val tvDestination by lazy { findViewById<TextView>(R.id.tvBookingDestination) }
    private val tvTravelTime by lazy { findViewById<TextView>(R.id.tvTravelTime) }

    private val tvRegularPrice by lazy { findViewById<TextView>(R.id.tvRegularPrice) }
    private val tvStudentPrice by lazy { findViewById<TextView>(R.id.tvStudentPrice) }
    private val tvSeniorPrice by lazy { findViewById<TextView>(R.id.tvSeniorPrice) }
    private val badgeStudent by lazy { findViewById<TextView>(R.id.badgeStudentDiscount) }
    private val badgeSenior by lazy { findViewById<TextView>(R.id.badgeSeniorDiscount) }

    private val tvCountRegular by lazy { findViewById<TextView>(R.id.tvCountRegular) }
    private val tvCountStudent by lazy { findViewById<TextView>(R.id.tvCountStudent) }
    private val tvCountSenior by lazy { findViewById<TextView>(R.id.tvCountSenior) }
    private val btnMinusRegular by lazy { findViewById<TextView>(R.id.btnMinusRegular) }
    private val btnMinusStudent by lazy { findViewById<TextView>(R.id.btnMinusStudent) }
    private val btnMinusSenior by lazy { findViewById<TextView>(R.id.btnMinusSenior) }

    private val tvTicketSummary by lazy { findViewById<TextView>(R.id.tvTicketSummary) }
    private val tvTotalPrice by lazy { findViewById<TextView>(R.id.tvTotalPrice) }
    private val btnProceed by lazy { findViewById<MaterialButton>(R.id.btnProceedPayment) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ScannerOnlyRole.isScanner()) {
            startActivity(Intent(this, GateScannerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        setContentView(R.layout.activity_booking)
        applySystemBarPadding() // keep content out from under the status/navigation bars

        val line = intent.getStringExtra(EXTRA_LINE)
        findViewById<TextView>(R.id.tvToolbarTitle).text = when (line) {
            "LRT-1" -> "LRT-1 Ticket"
            "LRT-2" -> "LRT-2 Ticket"
            else -> "Buy Ticket"
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        if (line == null) {
            Toast.makeText(this, "No line selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // The database stores codes as "LRT1"/"LRT2" (no hyphen), while the
        // intent passes "LRT-1"/"LRT-2". Normalize before querying.
        lineCode = line.replace("-", "")

        setupPayment()
        setupStationPickers()
        setupCounters()
        setupProceed()

        tvOrigin.text = "Select station"
        tvDestination.text = "Select station"
        renderAll()
        loadInitialData()
    }

    // ------------------------------------------------------------------
    // Data loading
    // ------------------------------------------------------------------

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                stations = repo.getStations(lineCode)
                android.util.Log.d("Booking", "stations loaded: ${stations.size} for '$lineCode'")
                if (stations.isEmpty()) {
                    toast("0 stations returned for '$lineCode' (check RLS policies / line code)")
                }
                discounts = try {
                    repo.getDiscounts().ifEmpty { discounts }
                } catch (e: Exception) {
                    android.util.Log.e("Booking", "discounts failed", e)
                    discounts
                }
                renderAll()
            } catch (e: Exception) {
                android.util.Log.e("Booking", "stations load failed", e)
                toast("Load failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun loadFare() {
        val o = origin
        val d = destination
        baseFare = null
        renderAll()
        if (o == null || d == null) return

        lifecycleScope.launch {
            try {
                val fare = repo.getBaseFare(o, d)
                // Ignore stale results if the user changed stations meanwhile
                if (origin == o && destination == d) {
                    baseFare = fare
                    if (fare == null) toast("No fare available for this route")
                    renderAll()
                }
            } catch (e: Exception) {
                toast("Couldn't load fare. Check your connection.")
            }
        }
    }

    // ------------------------------------------------------------------
    // Station selection
    // ------------------------------------------------------------------

    private fun setupStationPickers() {
        findViewById<View>(R.id.originBox).setOnClickListener {
            showStationPicker("Origin Station", exclude = destination) {
                origin = it
                tvOrigin.text = it.name
                loadFare()
            }
        }
        findViewById<View>(R.id.destinationBox).setOnClickListener {
            showStationPicker("Destination Station", exclude = origin) {
                destination = it
                tvDestination.text = it.name
                loadFare()
            }
        }
    }

    private fun showStationPicker(
        title: String,
        exclude: Station?,
        onPicked: (Station) -> Unit
    ) {
        val options = stations.filter { it.stationId != exclude?.stationId }
        if (options.isEmpty()) {
            toast("No stations loaded. Retrying…")
            loadInitialData() // retry so the real error shows up
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(options.map { it.name }.toTypedArray()) { _, which ->
                onPicked(options[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------
    // Passenger counters
    // ------------------------------------------------------------------

    private fun totalPassengers() = countRegular + countStudent + countSenior

    private fun setupCounters() {
        findViewById<View>(R.id.btnPlusRegular).setOnClickListener {
            if (canAddPassenger()) { countRegular++; renderAll() }
        }
        findViewById<View>(R.id.btnPlusStudent).setOnClickListener {
            if (canAddPassenger()) { countStudent++; renderAll() }
        }
        findViewById<View>(R.id.btnPlusSenior).setOnClickListener {
            if (canAddPassenger()) { countSenior++; renderAll() }
        }
        btnMinusRegular.setOnClickListener {
            if (countRegular > 0) { countRegular--; renderAll() }
        }
        btnMinusStudent.setOnClickListener {
            if (countStudent > 0) { countStudent--; renderAll() }
        }
        btnMinusSenior.setOnClickListener {
            if (countSenior > 0) { countSenior--; renderAll() }
        }
    }

    private fun canAddPassenger(): Boolean {
        if (totalPassengers() >= MAX_PASSENGERS) {
            toast("Maximum of $MAX_PASSENGERS passengers per booking")
            return false
        }
        return true
    }

    // ------------------------------------------------------------------
    // Payment (PayPal is the only accepted method in the schema)
    // ------------------------------------------------------------------

    private fun setupPayment() {
        // The payments table only allows method = 'paypal', so hide the
        // alternative wallet option and the "Change" link.
        findViewById<View>(R.id.optRTWallet).visibility = View.GONE
        findViewById<View>(R.id.btnChangePayment).visibility = View.GONE
    }

    // ------------------------------------------------------------------
    // Pricing
    // ------------------------------------------------------------------

    /** Same rounding as the SQL function: base * (100 - pct) / 100, 2 dp. */
    private fun fareFor(type: String): BigDecimal? {
        val base = baseFare ?: return null
        val pct = discounts[type] ?: 0
        return base.multiply(BigDecimal(100 - pct))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    }

    private fun totalPrice(): BigDecimal {
        val regular = fareFor("none") ?: return BigDecimal.ZERO
        val student = fareFor("student") ?: BigDecimal.ZERO
        val senior = fareFor("senior") ?: BigDecimal.ZERO
        return regular.multiply(BigDecimal(countRegular))
            .add(student.multiply(BigDecimal(countStudent)))
            .add(senior.multiply(BigDecimal(countSenior)))
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private fun renderAll() {
        // Travel time estimate
        val o = origin
        val d = destination
        tvTravelTime.text = if (o != null && d != null) {
            val stops = kotlin.math.abs(o.position - d.position)
            "~${stops * MINUTES_PER_STOP} min · $stops stops"
        } else "--"

        // Per-person prices + discount badges
        fun price(type: String) =
            fareFor(type)?.let { "${peso.format(it)} / person" } ?: "-- / person"
        tvRegularPrice.text = price("none")
        tvStudentPrice.text = price("student")
        tvSeniorPrice.text = price("senior")
        badgeStudent.text = "${discounts["student"] ?: 0}% OFF"
        badgeSenior.text = "${discounts["senior"] ?: 0}% OFF"

        // Counts
        tvCountRegular.text = countRegular.toString()
        tvCountStudent.text = countStudent.toString()
        tvCountSenior.text = countSenior.toString()

        // Dim the minus button when it can't be used
        val active = 0xFF0B1A30.toInt()
        val dim = 0xFF94A3B8.toInt()
        btnMinusRegular.setTextColor(if (countRegular > 0) active else dim)
        btnMinusStudent.setTextColor(if (countStudent > 0) active else dim)
        btnMinusSenior.setTextColor(if (countSenior > 0) active else dim)

        // Summary line + total
        val parts = buildList {
            if (countRegular > 0) add("$countRegular Regular")
            if (countStudent > 0) add("$countStudent Student")
            if (countSenior > 0) add("$countSenior Senior/PWD")
        }
        tvTicketSummary.text = if (parts.isEmpty()) "No passengers" else parts.joinToString(" · ")
        tvTotalPrice.text =
            if (baseFare == null) "--" else peso.format(totalPrice())

        // Proceed button only when everything needed is present
        val ready = origin != null && destination != null &&
                baseFare != null && totalPassengers() > 0 && !isSubmitting
        btnProceed.isEnabled = ready
        btnProceed.alpha = if (ready) 1f else 0.5f
    }

    // ------------------------------------------------------------------
    // Checkout
    // ------------------------------------------------------------------

    private fun setupProceed() {
        btnProceed.setOnClickListener { submitBooking() }
    }

    private fun submitBooking() {
        val o = origin ?: return toast("Select an origin station")
        val d = destination ?: return toast("Select a destination station")
        if (totalPassengers() == 0) return toast("Add at least one passenger")

        isSubmitting = true
        btnProceed.text = "Processing…"
        renderAll()

        lifecycleScope.launch {
            try {
                // Server recomputes every fare; the app total is only a preview.
                val result = repo.createBooking(
                    lineCode, o, d, countRegular, countStudent, countSenior
                )
                openPayment(result)
            } catch (e: Exception) {
                toast("Booking failed: ${e.message ?: "please try again"}")
            } finally {
                isSubmitting = false
                btnProceed.text = "Proceed to Payment  →"
                renderAll()
            }
        }
    }

    /**
     * A pending transaction, its tickets, and a pending PayPal payment now exist.
     * Create a PayPal (sandbox) order for the server's total and open it in a browser tab.
     * PayPal then sends the user back to onNewIntent() below.
     */
    private suspend fun openPayment(result: BookingResult) {
        pendingBooking = result
        // PayPal wants the amount as text with 2 decimals, e.g. "45.00"
        val amount = BigDecimal.valueOf(result.totalAmount)
            .setScale(2, RoundingMode.HALF_UP).toPlainString()

        val approveUrl = withContext(Dispatchers.IO) {   // network call, off the main thread
            PayPalHelper.createOrder(amount, result.referenceNo)
        }
        CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(approveUrl))
    }

    /**
     * PayPal sends the user back here with railtap://paypal/return?token=ORDER_ID
     * (or railtap://paypal/cancel). Works because of the intent-filter and
     * launchMode="singleTop" in AndroidManifest.xml.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data ?: return

        if (uri.path == "/cancel") {
            toast("Payment cancelled")
            return
        }
        val orderId = uri.getQueryParameter("token") ?: return // PayPal's order ID

        isSubmitting = true
        btnProceed.text = "Confirming payment…"
        renderAll()

        lifecycleScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { PayPalHelper.captureOrder(orderId) }
                if (status == "COMPLETED") {
                    val booking = pendingBooking
                    if (booking != null) {
                        // Mark the booking as paid in Supabase
                        try {
                            repo.markBookingPaid(booking.transactionId, orderId)
                        } catch (e: Exception) {
                            // PayPal already took the money, so still show the receipt,
                            // but tell the user the booking couldn't be updated.
                            toast("Paid, but couldn't update booking: ${e.message}")
                        }

                        ReceiptActivity.open(
                            from = this@BookingActivity,
                            total = booking.totalAmount,
                            lineCode = lineCode,
                            origin = origin?.name ?: "--",
                            destination = destination?.name ?: "--",
                            referenceNo = booking.referenceNo
                        )
                        finish() // so "back" from the receipt doesn't return to booking
                    } else {
                        toast("Payment successful!")
                    }
                } else {
                    toast("Payment status: $status")
                }
            } catch (e: Exception) {
                toast("Payment failed: ${e.message}")
            } finally {
                isSubmitting = false
                btnProceed.text = "Proceed to Payment  →"
                renderAll()
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}