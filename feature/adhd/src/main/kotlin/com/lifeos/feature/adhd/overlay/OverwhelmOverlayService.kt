package com.lifeos.feature.adhd.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.lifeos.core.database.todo.TodoDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Overwhelm mode (§Module 5): a single floating "What's next?" card over
 * whatever the user is doing — one task, nothing else. Requires the
 * "Display over other apps" grant (requested from the Focus screen).
 */
@AndroidEntryPoint
class OverwhelmOverlayService : Service() {

    @Inject
    lateinit var todoDao: TodoDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlayView: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            val next = withContext(Dispatchers.IO) { todoDao.nextOpenTask() }
            showOverlay(next?.title ?: "Nothing urgent. Breathe.", next?.id)
        }
        return START_NOT_STICKY
    }

    private fun showOverlay(title: String, taskId: Long?) {
        removeOverlay()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val ctx = this
        fun pillButton(label: String, filled: Boolean, onClick: () -> Unit) = Button(ctx).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(if (filled) SURFACE else PRIMARY)
            stateListAnimator = null
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                if (filled) setColor(PRIMARY) else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), OUTLINE)
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = dp(10) }
            layoutParams = lp
            setOnClickListener { onClick() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(SURFACE)
                setStroke(dp(1), OUTLINE)
            }
            addView(
                TextView(context).apply {
                    text = "What's next?"
                    setTextColor(PRIMARY)
                    textSize = 13f
                },
            )
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(ON_SURFACE)
                    textSize = 20f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, dp(6), 0, dp(14))
                },
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    if (taskId != null) {
                        addView(
                            pillButton("Done", filled = true) {
                                scope.launch {
                                    withContext(Dispatchers.IO) { todoDao.setDone(taskId, true) }
                                    val next = withContext(Dispatchers.IO) { todoDao.nextOpenTask() }
                                    showOverlay(next?.title ?: "All clear. Breathe.", next?.id)
                                }
                            },
                        )
                    }
                    addView(pillButton("Close", filled = false) { stopSelf() })
                },
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(64)
        }
        windowManager.addView(card, params)
        overlayView = card
    }

    private fun removeOverlay() {
        overlayView?.let {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_HIDE = "com.lifeos.adhd.HIDE_OVERLAY"

        // Match the app's dark M3 pastel theme (core:designsystem) so the overlay
        // reads as part of LifeOS, not a stray blue card.
        private val SURFACE = Color.parseColor("#14181C")
        private val ON_SURFACE = Color.parseColor("#E2E3DE")
        private val OUTLINE = Color.parseColor("#3A4048")
        private val PRIMARY = Color.parseColor("#9FCBA6")

        fun show(context: Context) {
            context.startService(Intent(context, OverwhelmOverlayService::class.java))
        }
    }
}
