package com.example.railtap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

// Zoomable map with 3 layers (base, LRT-1, LRT-2).
// Tap a line to select it, tap again or tap empty space to clear.
class LinesMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // called when the selection changes
    var onSelectionChanged: ((MapLine?) -> Unit)? = null

    // null = both lines
    var selectedLine: MapLine? = null
        set(value) {
            if (field == value) return
            field = value
            animateDim()
            onSelectionChanged?.invoke(value)
        }

    var dimmedAlpha = 0.25f

    // space for the pills at the bottom
    var bottomInset = 0
        set(value) { field = value; if (width > 0) resetView(false) }

    var drawLabels = true
        set(value) { field = value; invalidate() }

    // ---------------------------------------------------------------- layers
    private val baseLayer: Drawable?
    private val lineLayers: Map<MapLine, Drawable>
    private val mapW: Float
    private val mapH: Float
    private val labelScale: Float
    private val density = resources.displayMetrics.density

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.LinesMapView)
        fun layer(index: Int, fallback: Int): Drawable =
            (a.getDrawable(index) ?: requireNotNull(ContextCompat.getDrawable(context, fallback))).mutate()
        baseLayer = a.getDrawable(R.styleable.LinesMapView_baseDrawable)?.mutate()
            ?: ContextCompat.getDrawable(context, R.drawable.lines_map_base)?.mutate()
        lineLayers = mapOf(
            MapLine.LRT1 to layer(R.styleable.LinesMapView_lrt1Drawable, R.drawable.lines_map_lrt1),
            MapLine.LRT2 to layer(R.styleable.LinesMapView_lrt2Drawable, R.drawable.lines_map_lrt2)
        )
        drawLabels = a.getBoolean(R.styleable.LinesMapView_drawLabels, true)
        a.recycle()

        val ref = lineLayers.getValue(MapLine.LRT1)
        mapW = ref.intrinsicWidth.toFloat().also { require(it > 0) { "Layers need an intrinsic size" } }
        mapH = ref.intrinsicHeight.toFloat()
        labelScale = mapW / LinesMapData.WIDTH
        baseLayer?.setBounds(0, 0, mapW.toInt(), mapH.toInt())
        lineLayers.values.forEach { it.setBounds(0, 0, mapW.toInt(), mapH.toInt()) }
    }

    // ---------------------------------------------------------------- paints
    private val textColor: Int = run {
        val tv = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
        } else Color.BLACK
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = textColor }

    // ---------------------------------------------------------------- dim animation
    private val lineAlpha = floatArrayOf(1f, 1f)           // index = MapLine.ordinal
    private var dimAnimator: ValueAnimator? = null

    private fun animateDim() {
        dimAnimator?.cancel()
        val start = lineAlpha.copyOf()
        val end = FloatArray(2) { i -> if (selectedLine == null || selectedLine!!.ordinal == i) 1f else dimmedAlpha }
        dimAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            addUpdateListener {
                val t = it.animatedValue as Float
                for (i in 0..1) lineAlpha[i] = start[i] + (end[i] - start[i]) * t
                invalidate()
            }
            start()
        }
    }

    // ---------------------------------------------------------------- drawing
    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.concat(mapMatrix)
        baseLayer?.draw(canvas)

        // draw selected line last so it's on top
        for (line in MapLine.values().sortedBy { if (it == selectedLine) 1 else 0 }) {
            val a = lineAlpha[line.ordinal]
            lineLayers.getValue(line).apply { alpha = (255 * a).toInt(); draw(canvas) }
        }

        if (drawLabels) {
            canvas.save()
            canvas.scale(labelScale, labelScale)
            for (s in LinesMapData.stations) {
                labelPaint.alpha = (255 * lineAlpha[s.line.ordinal] * (if (s.isOpen) 1f else 0.6f)).toInt()
                if (s.labelMode == 0) {                       // beside a mostly vertical track
                    labelPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(s.name, s.x - 24f, s.y + 10f, labelPaint)
                } else {                                      // above a mostly horizontal track
                    labelPaint.textAlign = Paint.Align.LEFT
                    canvas.save()
                    canvas.translate(s.x - 6f, s.y - 24f)
                    canvas.rotate(-45f)
                    canvas.drawText(s.name, 0f, 0f, labelPaint)
                    canvas.restore()
                }
            }
            canvas.restore()
        }
        canvas.restore()
    }

    // ---------------------------------------------------------------- camera
    private val mapMatrix = Matrix()
    private val inverse = Matrix()
    private val values = FloatArray(9)
    private var fitScale = 1f
    private var animator: ValueAnimator? = null
    private val maxZoom = 8f

    private fun currentScale(): Float { mapMatrix.getValues(values); return values[Matrix.MSCALE_X] }
    private fun viewH() = (height - bottomInset).toFloat()

    private fun clamp() {
        val r = RectF(0f, 0f, mapW, mapH); mapMatrix.mapRect(r)
        var dx = 0f; var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2f - r.left
        else if (r.left > 0) dx = -r.left else if (r.right < width) dx = width - r.right
        if (r.height() <= viewH()) dy = (viewH() - r.height()) / 2f - r.top
        else if (r.top > 0) dy = -r.top else if (r.bottom < viewH()) dy = viewH() - r.bottom
        mapMatrix.postTranslate(dx, dy)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        fitScale = min(w / mapW, viewH() / mapH)
        resetView(false)
    }

    fun resetView(animate: Boolean = true) {
        fitScale = min(width / mapW, viewH() / mapH)
        fitRect(RectF(0f, 0f, mapW, mapH), animate, 0f)
    }

    // zoom so r fills the screen
    fun fitRect(r: RectF, animate: Boolean = true, padPx: Float = 40f * density) {
        val s = min((width - 2 * padPx) / r.width(), (viewH() - 2 * padPx) / r.height())
            .coerceIn(fitScale, fitScale * maxZoom)
        val target = Matrix().apply {
            postScale(s, s)
            postTranslate(width / 2f - r.centerX() * s, viewH() / 2f - r.centerY() * s)
        }
        animateTo(target, animate)
    }

    // zoom to a line
    fun focusLine(line: MapLine, animate: Boolean = true) =
        fitRect(lineBounds(line), animate, padPx = 56f * density)

    private fun animateTo(target: Matrix, animate: Boolean) {
        animator?.cancel()
        if (!animate) { mapMatrix.set(target); clamp(); invalidate(); return }
        val from = FloatArray(9).also { mapMatrix.getValues(it) }
        val to = FloatArray(9).also { target.getValues(it) }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320; interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                mapMatrix.setValues(FloatArray(9) { i -> from[i] + (to[i] - from[i]) * t })
                clamp(); invalidate()
            }
            start()
        }
    }

    // ---------------------------------------------------------------- hit testing (pixel based)
    private val hitScale = (1024f / maxOf(mapW, mapH)).coerceAtMost(1f)
    private val hitBitmaps = HashMap<MapLine, Bitmap>()
    private val boundsCache = HashMap<MapLine, RectF>()

    private fun hitBitmap(line: MapLine): Bitmap = hitBitmaps.getOrPut(line) {
        val bw = (mapW * hitScale).toInt().coerceAtLeast(1)
        val bh = (mapH * hitScale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val src = lineLayers.getValue(line)
        val d = src.constantState?.newDrawable(resources)?.mutate() ?: src
        val oldBounds = Rect(src.bounds)
        d.setBounds(0, 0, bw, bh)
        d.alpha = 255
        d.draw(Canvas(bmp))
        if (d === src) src.bounds = oldBounds
        bmp
    }

    private fun lineBounds(line: MapLine): RectF = boundsCache.getOrPut(line) {
        val bmp = hitBitmap(line); val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 40) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < x0) RectF(0f, 0f, mapW, mapH)
        else RectF(x0 / hitScale, y0 / hitScale, (x1 + 1) / hitScale, (y1 + 1) / hitScale)
    }

    // distance to the closest pixel of the line, or null
    private fun distanceToLine(line: MapLine, mx: Float, my: Float, r: Float): Float? {
        val bmp = hitBitmap(line)
        val cx = mx * hitScale; val cy = my * hitScale; val rr = r * hitScale
        var best = Float.MAX_VALUE
        for (y in floor(cy - rr).toInt()..ceil(cy + rr).toInt()) {
            if (y < 0 || y >= bmp.height) continue
            for (x in floor(cx - rr).toInt()..ceil(cx + rr).toInt()) {
                if (x < 0 || x >= bmp.width) continue
                if ((bmp.getPixel(x, y) ushr 24) > 80) best = min(best, hypot(x - cx, y - cy))
            }
        }
        return if (best <= rr) best else null
    }

    private fun lineAt(px: Float, py: Float): MapLine? {
        mapMatrix.invert(inverse)
        val p = floatArrayOf(px, py); inverse.mapPoints(p)
        val radius = (22f * density / currentScale()).coerceAtMost(150f)
        return MapLine.values()
            .mapNotNull { l -> distanceToLine(l, p[0], p[1], radius)?.let { l to it } }
            .minByOrNull { it.second }?.first
    }

    // ---------------------------------------------------------------- gestures
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                animator?.cancel()
                val cur = currentScale()
                val f = (cur * d.scaleFactor).coerceIn(fitScale, fitScale * maxZoom) / cur
                mapMatrix.postScale(f, f, d.focusX, d.focusY)
                clamp(); invalidate(); return true
            }
        })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaleDetector.isInProgress) return false
            animator?.cancel()
            mapMatrix.postTranslate(-dx, -dy); clamp(); invalidate(); return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val hit = lineAt(e.x, e.y)
            selectedLine = if (hit == null || hit == selectedLine) null else hit
            performClick(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val cur = currentScale()
            val f = if (cur < fitScale * 2f) 2.5f else fitScale / cur
            animateTo(Matrix(mapMatrix).apply { postScale(f, f, e.x, e.y) }, true)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
