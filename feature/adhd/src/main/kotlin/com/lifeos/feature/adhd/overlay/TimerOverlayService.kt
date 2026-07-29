package com.lifeos.feature.adhd.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Whether the floating ring is on screen, and the only way to ask for it.
 *
 * The service publishes here, so the in-app "Overlay"/"Hide overlay" button
 * still tells the truth after the overlay's own X is tapped.
 */
object TimerOverlayState {

    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()

    /** Place and size the user dragged the ring to; survives hide and show. */
    internal var sizePx: Int = 0
    internal var posX: Int = Int.MIN_VALUE
    internal var posY: Int = Int.MIN_VALUE

    internal fun publish(visible: Boolean) {
        _visible.value = visible
    }

    fun show(context: Context, deadlineElapsed: Long, totalMs: Long, running: Boolean) {
        context.startService(
            Intent(context, TimerOverlayService::class.java)
                .putExtra(TimerOverlayService.EXTRA_DEADLINE_ELAPSED, deadlineElapsed)
                .putExtra(TimerOverlayService.EXTRA_TOTAL_MS, totalMs)
                .putExtra(TimerOverlayService.EXTRA_RUNNING, running),
        )
    }

    fun hide(context: Context) {
        context.startService(
            Intent(context, TimerOverlayService::class.java).setAction(TimerOverlayService.ACTION_HIDE),
        )
    }
}

/**
 * Floats the Focus countdown ring over the whole OS (§Module 5). Started with
 * an absolute deadline; draws the shrinking ring itself (no Compose in a window
 * token). Drag it anywhere, pinch it bigger or smaller, and a single tap toggles
 * a close "X" that dismisses the ring WITHOUT stopping the timer.
 */
class TimerOverlayService : Service() {

    private var view: TimerOverlayView? = null
    private var params: WindowManager.LayoutParams? = null

    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            remove()
            TimerOverlayState.publish(false)
            stopSelf()
            return START_NOT_STICKY
        }
        val deadline = intent?.getLongExtra(EXTRA_DEADLINE_ELAPSED, 0L) ?: 0L
        val total = intent?.getLongExtra(EXTRA_TOTAL_MS, 0L) ?: 0L
        val running = intent?.getBooleanExtra(EXTRA_RUNNING, true) ?: true
        if (deadline <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        val existing = view
        if (existing != null) {
            // Already up: retarget it, keeping the user's place and size.
            existing.retarget(deadline, total, running)
        } else {
            show(deadline, total, running)
        }
        TimerOverlayState.publish(true)
        return START_NOT_STICKY
    }

    private fun show(deadlineElapsed: Long, totalMs: Long, running: Boolean) {
        remove()
        val density = resources.displayMetrics.density
        val size = TimerOverlayState.sizePx.takeIf { it > 0 } ?: (density * 180).toInt()
        val layout = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = TimerOverlayState.posX.takeIf { it != Int.MIN_VALUE }
                ?: (resources.displayMetrics.widthPixels - size - (density * 16).toInt())
            y = TimerOverlayState.posY.takeIf { it != Int.MIN_VALUE } ?: (density * 96).toInt()
        }
        val overlay = TimerOverlayView(
            context = this,
            deadlineElapsed = deadlineElapsed,
            totalMs = totalMs,
            running = running,
            minSizePx = (density * 96).toInt(),
            maxSizePx = (density * 320).toInt(),
            onClose = {
                remove()
                TimerOverlayState.publish(false)
                stopSelf()
            },
            onMove = { dx, dy ->
                val current = params
                if (current != null) {
                    current.x = (current.x + dx)
                        .coerceIn(0, (resources.displayMetrics.widthPixels - current.width).coerceAtLeast(0))
                    current.y = (current.y + dy)
                        .coerceIn(0, (resources.displayMetrics.heightPixels - current.height).coerceAtLeast(0))
                    TimerOverlayState.posX = current.x
                    TimerOverlayState.posY = current.y
                    runCatching { windowManager.updateViewLayout(view, current) }
                }
            },
            onResize = { newSize ->
                val current = params
                if (current != null) {
                    current.width = newSize
                    current.height = newSize
                    TimerOverlayState.sizePx = newSize
                    runCatching { windowManager.updateViewLayout(view, current) }
                }
            },
        )
        windowManager.addView(overlay, layout)
        view = overlay
        params = layout
    }

    private fun remove() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        params = null
    }

    override fun onDestroy() {
        remove()
        TimerOverlayState.publish(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_HIDE = "com.lifeos.adhd.HIDE_TIMER_OVERLAY"
        internal const val EXTRA_DEADLINE_ELAPSED = "deadline_elapsed"
        internal const val EXTRA_TOTAL_MS = "total_ms"
        internal const val EXTRA_RUNNING = "running"
    }
}

/**
 * Self-drawing countdown ring: drag to move, pinch to resize, tap to reveal the
 * close button.
 */
private class TimerOverlayView(
    context: Context,
    deadlineElapsed: Long,
    totalMs: Long,
    running: Boolean,
    private val minSizePx: Int,
    private val maxSizePx: Int,
    private val onClose: () -> Unit,
    private val onMove: (dx: Int, dy: Int) -> Unit,
    private val onResize: (sizePx: Int) -> Unit,
) : FrameLayout(context) {

    private var deadlineElapsed = deadlineElapsed
    private var totalMs = totalMs
    private var running = running

    private var showClose = false
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pinchStart = 0f
    private var pinchStartSize = 0

    private val closeButton = TextView(context).apply {
        text = "X"
        setTextColor(Color.WHITE)
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        val pad = (resources.displayMetrics.density * 6).toInt()
        setPadding(pad, 0, pad, 0)
        visibility = View.GONE
        setOnClickListener { onClose() }
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3A4048")
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#9FCBA6")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14181C") }

    init {
        addView(
            closeButton,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END),
        )
        setWillNotDraw(false)
    }

    fun retarget(deadlineElapsed: Long, totalMs: Long, running: Boolean) {
        this.deadlineElapsed = deadlineElapsed
        this.totalMs = totalMs
        this.running = running
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
                dragging = false
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    pinchStart = spacing(event)
                    pinchStartSize = width
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && pinchStart > 0f) {
                    val scale = spacing(event) / pinchStart
                    onResize((pinchStartSize * scale).toInt().coerceIn(minSizePx, maxSizePx))
                    return true
                }
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                if (!dragging && hypot(event.rawX - downX, event.rawY - downY) > TOUCH_SLOP) dragging = true
                if (dragging) {
                    lastX = event.rawX
                    lastY = event.rawY
                    onMove(dx.toInt(), dy.toInt())
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                pinchStart = 0f
                return true
            }

            MotionEvent.ACTION_UP -> {
                // A tap (not a drag) toggles the close button.
                if (!dragging && abs(event.rawX - downX) < TOUCH_SLOP && abs(event.rawY - downY) < TOUCH_SLOP) {
                    showClose = !showClose
                    closeButton.visibility = if (showClose) View.VISIBLE else View.GONE
                }
                dragging = false
                pinchStart = 0f
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun spacing(event: MotionEvent): Float =
        hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))

    override fun onDraw(canvas: Canvas) {
        val remaining = (deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val cx = width / 2f
        val cy = height / 2f
        // Stroke and label scale with the window, so resizing stays readable.
        val stroke = (minOf(width, height) * 0.055f).coerceAtLeast(4f)
        trackPaint.strokeWidth = stroke
        barPaint.strokeWidth = stroke
        textPaint.textSize = minOf(width, height) * 0.2f
        val radius = minOf(width, height) / 2f - stroke
        canvas.drawCircle(cx, cy, radius, bgPaint)
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)
        val progress = if (totalMs <= 0) 0f else remaining / totalMs.toFloat()
        canvas.drawArc(rect, -90f, 360f * progress, false, barPaint)
        val totalSeconds = remaining / 1000
        val label = if (totalSeconds >= 3600) {
            "%d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
        } else {
            "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        }
        val ty = cy - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, cx, ty, textPaint)
        if (running && remaining > 0) postInvalidateDelayed(250)
    }

    private companion object {
        const val TOUCH_SLOP = 16f
    }
}
