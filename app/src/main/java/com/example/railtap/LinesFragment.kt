package com.example.railtap

import android.os.Bundle
import android.view.View
import android.widget.TextView

// Lines tab (map)
class LinesFragment : TabFragment(R.layout.fragment_lines) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // title changes with the selected line
        val title = findViewById<TextView>(R.id.tvTitle)
        title.text = "LINES"

        val map = findViewById<LinesMapView>(R.id.linesMap)
        val pills = findViewById<View>(R.id.linePills)
        val pill1 = findViewById<TextView>(R.id.pillLrt1)
        val pill2 = findViewById<TextView>(R.id.pillLrt2)

        // Keep the map's visual centre above the pills.
        pills.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val lp = pills.layoutParams as android.view.ViewGroup.MarginLayoutParams
            map.bottomInset = (bottom - top) + lp.bottomMargin + lp.topMargin
        }

        // Pills are indicators: lit = line is active. With no selection both are lit.
        fun render(selected: MapLine?) {
            title.text = when (selected) {
                MapLine.LRT1 -> "LRT LINE 1"
                MapLine.LRT2 -> "LRT LINE 2"
                null -> "LINES"
            }
            fun apply(pill: TextView, line: MapLine) {
                val active = selected == null || selected == line
                pill.animate().alpha(if (active) 1f else 0.4f).setDuration(200).start()
                pill.isSelected = selected == line
            }
            apply(pill1, MapLine.LRT1)
            apply(pill2, MapLine.LRT2)
        }
        map.onSelectionChanged = ::render
        render(map.selectedLine)

        // Pills are also buttons: tap = select + focus; tap the active one again = clear + zoom out.
        fun onPill(line: MapLine) {
            if (map.selectedLine == line) {
                map.selectedLine = null
                map.resetView()
            } else {
                map.selectedLine = line
                map.focusLine(line)
            }
        }
        pill1.setOnClickListener { onPill(MapLine.LRT1) }
        pill2.setOnClickListener { onPill(MapLine.LRT2) }
    }
}
