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

// Receipt screen (after paying, or from trip history)
class ReceiptActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TOTAL = "extra_total"
        private const val EXTRA_LINE = "extra_line"
        private const val EXTRA_ORIGIN = "extra_origin"
        private const val EXTRA_DESTINATION = "extra_destination"
        private const val EXTRA_REFERENCE_NO = "extra_reference_no"
        private const val EXTRA_PAID_AT = "extra_paid_at"
        private const val EXTRA_FROM_HISTORY = "extra_from_history"
        private const val EXTRA_REGULAR = "extra_regular"
        private const val EXTRA_STUDENT = "extra_student"
        private const val EXTRA_SENIOR = "extra_senior"
        private const val EXTRA_PWD = "extra_pwd"

        
        fun open(
            from: AppCompatActivity,
            total: Double,
            lineCode: String,
            origin: String,
            destination: String,
            referenceNo: String,
            paidAtMillis: Long = System.currentTimeMillis(),
            fromHistory: Boolean = false, // back goes to trip history
            // passenger counts (0 = hidden)
            regular: Int = 0,
            student: Int = 0,
            senior: Int = 0,
            pwd: Int = 0
        ) {
            val intent = Intent(from, ReceiptActivity::class.java).apply {
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_LINE, lineCode)
                putExtra(EXTRA_ORIGIN, origin)
                putExtra(EXTRA_DESTINATION, destination)
                putExtra(EXTRA_REFERENCE_NO, referenceNo)
                putExtra(EXTRA_PAID_AT, paidAtMillis)
                putExtra(EXTRA_FROM_HISTORY, fromHistory)
                putExtra(EXTRA_REGULAR, regular)
                putExtra(EXTRA_STUDENT, student)
                putExtra(EXTRA_SENIOR, senior)
                putExtra(EXTRA_PWD, pwd)
            }
            from.startActivity(intent)
        }
    }

    private val peso: NumberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt)
        applySystemBarPadding()

        val total = intent.getDoubleExtra(EXTRA_TOTAL, 0.0)
        val lineCode = intent.getStringExtra(EXTRA_LINE).orEmpty()
        val paidAt = intent.getLongExtra(EXTRA_PAID_AT, System.currentTimeMillis())

        findViewById<TextView>(R.id.tvReceiptTotal).text = peso.format(total)
        findViewById<TextView>(R.id.tvReceiptTotalFare).text = peso.format(total)
        findViewById<TextView>(R.id.tvReceiptOrigin).text = intent.getStringExtra(EXTRA_ORIGIN) ?: "--"
        findViewById<TextView>(R.id.tvReceiptDestination).text = intent.getStringExtra(EXTRA_DESTINATION) ?: "--"
        fitStationPair(findViewById(R.id.tvReceiptOrigin), findViewById(R.id.tvReceiptDestination))
        findViewById<TextView>(R.id.tvReceiptMethod).text = "PayPal"
        findViewById<TextView>(R.id.tvReceiptReference).text = intent.getStringExtra(EXTRA_REFERENCE_NO) ?: "--"
        findViewById<TextView>(R.id.tvReceiptDateTime).text =
            SimpleDateFormat("MM/dd/yyyy • hh:mm a", Locale.getDefault()).format(Date(paidAt))

        showPassengerBreakdown()

        // green = LRT-1, purple = LRT-2
        val linePill = findViewById<TextView>(R.id.tvReceiptLine)
        if (lineCode.contains("1")) {
            linePill.text = "LRT LINE 1"
            linePill.setBackgroundResource(R.drawable.bg_pill_green)
            linePill.setTextColor(ContextCompat.getColor(this, R.color.green_line))
        } else {
            linePill.text = "LRT LINE 2"
        }

        // buttons
        findViewById<View>(R.id.btnCloseReceipt).setOnClickListener { close() }

        findViewById<View>(R.id.btnShowPasses).setOnClickListener {
            DashboardActivity.open(this, NavTab.TICKETS)
            finish()
        }

        findViewById<View>(R.id.btnDownloadReceipt).setOnClickListener { downloadReceipt() }

        // old trips: no passes button
        if (intent.getBooleanExtra(EXTRA_FROM_HISTORY, false)) {
            findViewById<View>(R.id.btnShowPasses).visibility = View.GONE
            findViewById<TextView>(R.id.tvReceiptMessage).text = "This trip was paid with PayPal."
        }

        // back = same as X
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = close()
        })
    }

    // "Adult x2" rows, skip zeros
    private fun showPassengerBreakdown() {
        val box = findViewById<android.widget.LinearLayout>(R.id.receiptPassengers)
        box.removeAllViews()
        val rows = listOf(
            "Adult" to intent.getIntExtra(EXTRA_REGULAR, 0),
            "Student" to intent.getIntExtra(EXTRA_STUDENT, 0),
            "Senior / PWD" to intent.getIntExtra(EXTRA_SENIOR, 0),
            "PWD" to intent.getIntExtra(EXTRA_PWD, 0)
        ).filter { it.second > 0 }

        val density = resources.displayMetrics.density
        val font = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.plusjakartasans)
        for ((label, count) in rows) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, (14 * density).toInt(), 0, 0)
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 14f
                typeface = font
                setTextColor(android.graphics.Color.parseColor("#6B7280"))
            }, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "× $count"
                textSize = 14f
                typeface = font
                setTextColor(ContextCompat.getColor(this@ReceiptActivity, R.color.neutral_heading))
            })
            box.addView(row)
        }
    }

    private fun close() {
        if (intent.getBooleanExtra(EXTRA_FROM_HISTORY, false)) finish() else goHome()
    }

    private fun goHome() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    // save receipt as an image in Pictures/RailTap
    private fun downloadReceipt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Download needs Android 10 or newer", Toast.LENGTH_SHORT).show()
            return
        }

        val content = findViewById<View>(R.id.receiptContent)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))

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
