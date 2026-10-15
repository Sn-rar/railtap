package com.example.railtap

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Payment receipt shown after a successful PayPal payment.
 * Layout: res/layout/activity_receipt.xml
 * Opened from BookingActivity with ReceiptActivity.open(...)
 */
class ReceiptActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TOTAL = "extra_total"
        private const val EXTRA_LINE = "extra_line"
        private const val EXTRA_ORIGIN = "extra_origin"
        private const val EXTRA_DESTINATION = "extra_destination"
        private const val EXTRA_REFERENCE_NO = "extra_reference_no"
        private const val EXTRA_PAID_AT = "extra_paid_at"

        /** Opens the receipt screen with the booking details. */
        fun open(
            from: AppCompatActivity,
            total: Double,
            lineCode: String,       // "LRT1" or "LRT2"
            origin: String,
            destination: String,
            referenceNo: String
        ) {
            val intent = Intent(from, ReceiptActivity::class.java).apply {
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_LINE, lineCode)
                putExtra(EXTRA_ORIGIN, origin)
                putExtra(EXTRA_DESTINATION, destination)
                putExtra(EXTRA_REFERENCE_NO, referenceNo)
                putExtra(EXTRA_PAID_AT, System.currentTimeMillis())
            }
            from.startActivity(intent)
        }
    }

    private val peso: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt)
        applySystemBarPadding() // keep content out from under the status/navigation bars

        // ---- Read the booking details ----
        val total = intent.getDoubleExtra(EXTRA_TOTAL, 0.0)
        val lineCode = intent.getStringExtra(EXTRA_LINE).orEmpty()
        val paidAt = intent.getLongExtra(EXTRA_PAID_AT, System.currentTimeMillis())

        // ---- Fill in the screen ----
        findViewById<TextView>(R.id.tvReceiptTotal).text = peso.format(total)
        findViewById<TextView>(R.id.tvReceiptTotalFare).text = peso.format(total)
        findViewById<TextView>(R.id.tvReceiptOrigin).text = intent.getStringExtra(EXTRA_ORIGIN) ?: "--"
        findViewById<TextView>(R.id.tvReceiptDestination).text = intent.getStringExtra(EXTRA_DESTINATION) ?: "--"
        findViewById<TextView>(R.id.tvReceiptMethod).text = "PayPal"
        findViewById<TextView>(R.id.tvReceiptReference).text = intent.getStringExtra(EXTRA_REFERENCE_NO) ?: "--"
        findViewById<TextView>(R.id.tvReceiptDateTime).text =
            SimpleDateFormat("MM/dd/yyyy • hh:mm a", Locale.getDefault()).format(Date(paidAt))

        // Line pill: green for LRT-1, purple for LRT-2
        val linePill = findViewById<TextView>(R.id.tvReceiptLine)
        if (lineCode.contains("1")) {
            linePill.text = "LRT LINE 1"
            linePill.setBackgroundResource(R.drawable.bg_pill_green)
            linePill.setTextColor(ContextCompat.getColor(this, R.color.green_line))
        } else {
            linePill.text = "LRT LINE 2"
        }

        // ---- Buttons ----
        findViewById<View>(R.id.btnCloseReceipt).setOnClickListener { goHome() }

        findViewById<View>(R.id.btnShowPasses).setOnClickListener {
            startActivity(Intent(this, TicketsActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.btnDownloadReceipt).setOnClickListener { downloadReceipt() }

        // Phone's back button also goes home (not back to the booking/payment screen)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    /** Go back to the Dashboard and clear the booking screens behind it. */
    private fun goHome() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    /** Saves a picture of the receipt to the phone's Pictures/RailTap folder. */
    private fun downloadReceipt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Download needs Android 10 or newer", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Draw the receipt content into an image
        val content = findViewById<View>(R.id.receiptContent)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))

        // 2. Save it to Pictures/RailTap
        val reference = intent.getStringExtra(EXTRA_REFERENCE_NO) ?: "receipt"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "RailTap_$reference.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RailTap")
        }
        try {
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Couldn't create file")
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(this, "Receipt saved to Pictures/RailTap", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
