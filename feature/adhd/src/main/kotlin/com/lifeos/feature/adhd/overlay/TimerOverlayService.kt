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
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Floats the Focus countdown ring over the whole OS (§Module 5). Started with
 * a deadline; draws the shrinking ring itself (no Compose in a window token).
 * A single tap toggles a close "X" that dismisses the overlay WITHOUT stopping
 * the timer — the deadline is absolute, so reopening shows the correct time.
 */
class TimerOverlayService : Service() {

    private var view: TimerOverlayView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            remove()
            stopSelf()
            return START_NOT_STICKY
        }
        val deadline = intent?.getLongExtra(EXTRA_DEADLINE_ELAPSED, 0L) ?: 0L
        val total = intent?.getLongExtra(EXTRA_TOTAL_MS, 0L) ?: 0L
        if (deadline <= 0L) { stopSelf(); return START_NOT_STICKY }
        show(deadline, total)
        return START_NOT_STICKY
    }

    private fun show(deadlineElapsed: Long, totalMs: Long) {
        remove()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlay = TimerOverlayView(this, deadlineElapsed, totalMs) { remove(); stopSelf() }
        val size = (resources.displayMetrics.density * 180).toInt()
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (resources.displayMetrics.density * 16).toInt()
            y = (resources.displayMetrics.density * 96).toInt()
        }
        windowManager.addView(overlay, params)
        view = overlay
    }

    private fun remove() {
        view?.let { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) }
        view = null
    }

    override fun onDestroy() {
        remove()
        super.onDestroy()
    }

    companion object {
        const val ACTION_HIDE = "com.lifeos.adhd.HIDE_TIMER_OVERLAY"
        private const val EXTRA_DEADLINE_ELAPSED = "deadline_elapsed"
        private const val EXTRA_TOTAL_MS = "total_ms"

        fun show(context: Context, deadlineElapsed: Long, totalMs: Long) {
            context.startService(
                Intent(context, TimerOverlayService::class.java)
                    .putExtra(EXTRA_DEADLINE_ELAPSED, deadlineElapsed)
                    .putExtra(EXTRA_TOTAL_MS, totalMs),
            )
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, TimerOverlayService::class.java).setAction(ACTION_HIDE),
            )
        }
    }
}

/** Self-drawing countdown ring. Tap once to toggle a close button. */
private class TimerOverlayView(
    context: Context,
    private val deadlineElapsed: Long,
    private val totalMs: Long,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    private var showClose = false
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
        strokeWidth = resources.displayMetrics.density * 10
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3A4048")
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 10
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#9FCBA6")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = resources.displayMetrics.density * 26
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14181C") }

    init {
        addView(
            closeButton,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END),
        )
        setOnClickListener {
            showClose = !showClose
            closeButton.visibility = if (showClose) View.VISIBLE else View.GONE
        }
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val remaining = (deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - trackPaint.strokeWidth
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
        if (remaining > 0) postInvalidateDelayed(250)
    }
}
