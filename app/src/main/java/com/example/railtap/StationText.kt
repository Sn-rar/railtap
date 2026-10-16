package com.example.railtap

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.widget.TextView
import androidx.core.widget.TextViewCompat

// Makes origin + destination the SAME size, shrinking both until they fit.
// Never splits a word across lines (it can still break after a dash, like "REDEMPTORIST-" / "ASEANA").
fun fitStationPair(origin: TextView, destination: TextView, maxLines: Int = 2, minSp: Float = 11f) {
    val views = listOf(origin, destination)
    for (v in views) {
        // remember the size from the xml as the biggest allowed size
        if (v.getTag(R.id.station_max_text_size) == null) v.setTag(R.id.station_max_text_size, v.textSize)
        TextViewCompat.setAutoSizeTextTypeWithDefaults(v, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
        v.maxLines = maxLines
        v.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        v.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
    }

    val fit = Runnable {
        val step = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, origin.resources.displayMetrics)
        val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, minSp, origin.resources.displayMetrics)
        var size = views.maxOf { it.getTag(R.id.station_max_text_size) as Float }
        while (size > minPx && !views.all { fitsAt(it, size, maxLines) }) size -= step
        views.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, size) }
    }
    // need the real widths, so wait for layout if it hasn't happened yet
    if (origin.width > 0 && destination.width > 0) fit.run() else origin.post(fit)
}

private fun fitsAt(v: TextView, sizePx: Float, maxLines: Int): Boolean {
    val width = v.width - v.compoundPaddingLeft - v.compoundPaddingRight
    if (width <= 0) return true
    val text = (v.transformationMethod?.getTransformation(v.text, v) ?: v.text).toString() // includes textAllCaps
    val paint = TextPaint(v.paint).apply { textSize = sizePx }

    // every word (or part after a dash) has to fit on one line, otherwise it would get cut
    val pieces = text.split(' ', '\n').flatMap { word -> word.split(Regex("(?<=-)")) }
    if (pieces.any { it.isNotEmpty() && paint.measureText(it) > width }) return false

    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setLineSpacing(v.lineSpacingExtra, v.lineSpacingMultiplier)
        .setIncludePad(v.includeFontPadding)
        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
        .build()
    return layout.lineCount <= maxLines
}
