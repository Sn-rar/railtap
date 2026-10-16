package com.example.railtap

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import kotlin.math.abs

// Lines tab (map)
class LinesFragment : TabFragment(R.layout.fragment_lines) {

    private lateinit var map: LinesMapView
    private lateinit var card: View
    private lateinit var panelHandle: View

    // 1st tap = origin, 2nd tap = destination
    private var first: MapStation? = null
    private var second: MapStation? = null

    private var panelHidden = false

    private lateinit var tvFrom: TextView
    private lateinit var tvTo: TextView
    private lateinit var dotFrom: View
    private lateinit var dotTo: View
    private lateinit var timeGroup: View
    private lateinit var tvTimeValue: TextView
    private lateinit var tvTimeUnit: TextView
    private lateinit var neighborGroup: View
    private lateinit var tvPrevTo: TextView
    private lateinit var tvPrevDir: TextView
    private lateinit var tvPrevValue: TextView
    private lateinit var tvPrevUnit: TextView
    private lateinit var tvNextTo: TextView
    private lateinit var tvNextDir: TextView
    private lateinit var tvNextValue: TextView
    private lateinit var tvNextUnit: TextView
    private lateinit var tvHint: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        first = null; second = null; panelHidden = false

        // title changes with the selected line
        val title = findViewById<TextView>(R.id.tvTitle)
        title.text = "LINES"

        map = findViewById(R.id.linesMap)
        card = findViewById(R.id.bottomCard)
        panelHandle = findViewById(R.id.panelHandle)
        val pill1 = findViewById<TextView>(R.id.pillLrt1)
        val pill2 = findViewById<TextView>(R.id.pillLrt2)

        tvFrom = findViewById(R.id.tvFrom)
        tvTo = findViewById(R.id.tvTo)
        dotFrom = findViewById(R.id.dotFrom)
        dotTo = findViewById(R.id.dotTo)
        timeGroup = findViewById(R.id.timeGroup)
        tvTimeValue = findViewById(R.id.tvTimeValue)
        tvTimeUnit = findViewById(R.id.tvTimeUnit)
        neighborGroup = findViewById(R.id.neighborGroup)
        tvPrevTo = findViewById(R.id.tvPrevTo)
        tvPrevDir = findViewById(R.id.tvPrevDir)
        tvPrevValue = findViewById(R.id.tvPrevValue)
        tvPrevUnit = findViewById(R.id.tvPrevUnit)
        tvNextTo = findViewById(R.id.tvNextTo)
        tvNextDir = findViewById(R.id.tvNextDir)
        tvNextValue = findViewById(R.id.tvNextValue)
        tvNextUnit = findViewById(R.id.tvNextUnit)
        tvHint = findViewById(R.id.tvHint)

        // keep the map centered above the bottom card
        card.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            map.bottomInset = bottom - top
        }

        // highlight the active line pill
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
        map.onSelectionChanged = { sel ->
            render(sel)
            // changing line clears the picked stations
            if (first != null && sel != first?.line) clearStations()
        }
        render(map.selectedLine)

        map.onStationTapped = ::onStationTapped

        // tap pill = select line, tap again = clear
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

        setupPanelDrag()
        slidePanelIn()
        renderTravel()
    }

    // replay the slide up when coming back to this tab
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null && ::card.isInitialized) slidePanelIn()
    }

    // ---------------------------------------------------------------- bottom panel
    // handle stays visible when hidden
    private fun hideDistance() = (card.height - panelHandle.height).toFloat().coerceAtLeast(0f)

    // slide the card up
    private fun slidePanelIn() {
        panelHidden = false
        card.animate().cancel()
        card.doOnLayout {
            card.translationY = card.height.toFloat()
            card.animate().translationY(0f)
                .setStartDelay(150).setDuration(450)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }

    private fun setPanelHidden(hidden: Boolean, animate: Boolean = true) {
        panelHidden = hidden
        val target = if (hidden) hideDistance() else 0f
        card.animate().cancel()
        if (!animate) { card.translationY = target; return }
        card.animate().translationY(target)
            .setStartDelay(0).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    // drag handle down to hide, up to show, tap to toggle
    @SuppressLint("ClickableViewAccessibility")
    private fun setupPanelDrag() {
        val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        var downY = 0f
        var startTranslation = 0f
        var dragging = false
        var tracker: VelocityTracker? = null

        panelHandle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    card.animate().cancel()
                    downY = e.rawY; startTranslation = card.translationY; dragging = false
                    tracker = VelocityTracker.obtain().also { it.addMovement(e) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(e)
                    val dy = e.rawY - downY
                    if (!dragging && abs(dy) > slop) dragging = true
                    if (dragging) card.translationY = (startTranslation + dy).coerceIn(0f, hideDistance())
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(e)
                    tracker?.computeCurrentVelocity(1000)
                    val vy = tracker?.yVelocity ?: 0f
                    tracker?.recycle(); tracker = null
                    when {
                        !dragging && e.actionMasked == MotionEvent.ACTION_UP -> setPanelHidden(!panelHidden)
                        abs(vy) > 800f -> setPanelHidden(vy > 0) // flick
                        else -> setPanelHidden(card.translationY > hideDistance() / 2f)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ---------------------------------------------------------------- stations / travel time
    private fun onStationTapped(s: MapStation) {
        val a = first
        when {
            // start a new pick
            a == null || second != null || s.line != a.line -> { first = s; second = null }
            // tap origin again to clear
            s.name == a.name -> { first = null; second = null }
            else -> second = s
        }
        first?.let { map.selectedLine = it.line }
        if (panelHidden) setPanelHidden(false) // show the result
        renderTravel()
    }

    private fun clearStations() {
        first = null; second = null
        renderTravel()
    }

    private fun renderTravel() {
        val a = first
        val b = second
        map.highlighted = listOfNotNull(a, b)

        tvFrom.text = a?.name ?: getString(R.string.travel_origin)
        tvTo.text = b?.name ?: getString(R.string.travel_destination)
        tint(dotFrom, a)
        tint(dotTo, b)

        timeGroup.isVisible = false
        neighborGroup.isVisible = false
        tvHint.isVisible = false

        when {
            a == null -> tvHint.isVisible = true
            b == null -> {
                val (prev, next) = TravelTimes.neighbors(a)
                // LRT-1 = north/south, LRT-2 = west/east
                val (dirPrev, dirNext) =
                    if (a.line == MapLine.LRT1) "Northbound" to "Southbound" else "Westbound" to "Eastbound"
                bindHop(prev, dirPrev, "Start of line", tvPrevTo, tvPrevDir, tvPrevValue, tvPrevUnit)
                bindHop(next, dirNext, "End of line", tvNextTo, tvNextDir, tvNextValue, tvNextUnit)
                neighborGroup.isVisible = true
            }
            else -> {
                val total = TravelTimes.between(a, b)
                if (total != null) {
                    tvTimeValue.text = total.toString()
                    tvTimeUnit.text = unit(total)
                    timeGroup.isVisible = true
                }
            }
        }
    }

    private fun unit(min: Int) = if (min == 1) "min" else "mins"

    // fill one neighbor card
    private fun bindHop(
        hop: Hop?, direction: String, emptyLabel: String,
        to: TextView, dir: TextView, value: TextView, unitView: TextView
    ) {
        dir.text = direction
        if (hop != null) {
            to.text = "To ${hop.station.name}"
            value.text = hop.minutes.toString()
            unitView.isVisible = true
        } else {
            to.text = emptyLabel
            value.text = "—"
            unitView.isVisible = false
        }
    }

    private fun tint(dot: View, s: MapStation?) {
        val color = when (s?.line) {
            MapLine.LRT1 -> ContextCompat.getColor(requireContext(), R.color.green_line)
            MapLine.LRT2 -> ContextCompat.getColor(requireContext(), R.color.brand)
            null -> Color.parseColor("#CBD5E1")
        }
        ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(color))
    }
}