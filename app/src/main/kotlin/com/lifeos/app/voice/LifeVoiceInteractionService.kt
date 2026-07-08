package com.lifeos.app.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lifeos.core.common.result.LifeResult
import com.lifeos.feature.capture.data.VoskTranscriber
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Registers LifeOS as a digital-assistant app (§Module 10). Long-press
 * home/power-assist opens a Gemini-style overlay OVER the current app —
 * glowing screen border, auto-listening mic, text input — without ever
 * leaving what you were doing.
 */
class LifeVoiceInteractionService : VoiceInteractionService()

@AndroidEntryPoint
class LifeVoiceSessionService : VoiceInteractionSessionService() {

    @Inject
    lateinit var brain: AssistantBrain

    @Inject
    lateinit var transcriber: VoskTranscriber

    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        LifeAssistantSession(this, brain, transcriber)
}

/**
 * The assist overlay session: content view floats above the current app
 * (that's what VoiceInteractionSession windows are), edged with an animated
 * glow (Apple-Intelligence style). Speech starts immediately via the offline
 * Vosk engine; a text field covers quiet places.
 */
class LifeAssistantSession(
    context: Context,
    private val brain: AssistantBrain,
    private val transcriber: VoskTranscriber,
) : VoiceInteractionSession(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listenJob: Job? = null

    private lateinit var glow: GlowBorderView
    private lateinit var status: TextView
    private lateinit var response: TextView
    private lateinit var input: EditText

    override fun onCreateContentView(): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = FrameLayout(context)

        glow = GlowBorderView(context)
        root.addView(
            glow,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        // Tap anywhere outside the panel to dismiss — like Gemini.
        root.setOnClickListener { hide() }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(24))
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(),
                    0f, 0f, 0f, 0f,
                )
                setColor(Color.parseColor("#F0121821"))
            }
            isClickable = true // don't let panel taps fall through to dismiss

            addView(
                TextView(context).apply {
                    text = "LifeOS"
                    setTextColor(Color.parseColor("#8AB4F8"))
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                },
            )
            status = TextView(context).apply {
                text = "Listening…"
                setTextColor(Color.parseColor("#9AA0A6"))
                textSize = 14f
                setPadding(0, dp(2), 0, dp(8))
            }
            addView(status)

            response = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 17f
                setPadding(0, 0, 0, dp(10))
                visibility = View.GONE
            }
            addView(
                ScrollView(context).apply { addView(response) },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(160),
                ).apply { bottomMargin = dp(4) },
            )

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    input = EditText(context).apply {
                        hint = "Ask or command…"
                        setHintTextColor(Color.parseColor("#5F6368"))
                        setTextColor(Color.WHITE)
                        textSize = 16f
                        maxLines = 2
                        imeOptions = EditorInfo.IME_ACTION_SEND
                        inputType = android.text.InputType.TYPE_CLASS_TEXT
                        background = GradientDrawable().apply {
                            cornerRadius = dp(22).toFloat()
                            setColor(Color.parseColor("#1E2530"))
                        }
                        setPadding(dp(18), dp(10), dp(18), dp(10))
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_SEND) {
                                submit(text.toString())
                                setText("")
                                true
                            } else {
                                false
                            }
                        }
                    }
                    addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(
                        ImageButton(context).apply {
                            setImageResource(android.R.drawable.ic_menu_send)
                            background = GradientDrawable().apply {
                                cornerRadius = dp(22).toFloat()
                                setColor(Color.parseColor("#8AB4F8"))
                            }
                            setOnClickListener {
                                submit(input.text.toString())
                                input.setText("")
                            }
                        },
                        LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                            leftMargin = dp(8)
                            gravity = Gravity.CENTER_VERTICAL
                        },
                    )
                },
            )
        }

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        return root
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        response.visibility = View.GONE
        response.text = ""
        glow.start()
        startListening()
    }

    override fun onHide() {
        listenJob?.cancel()
        transcriber.stopListening()
        glow.stop()
        scope.coroutineContext.cancelChildren()
        super.onHide()
    }

    private fun startListening() {
        if (!transcriber.isModelReady()) {
            status.text = "Type below — or download the offline voice model in the app"
            return
        }
        status.text = "Listening…"
        listenJob?.cancel()
        listenJob = scope.launch {
            when (val result = transcriber.listen()) {
                is LifeResult.Success -> submit(result.value)
                is LifeResult.Failure -> status.text = "Didn't catch that — type below or tap send"
            }
        }
    }

    private fun submit(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        transcriber.stopListening()
        status.text = "“$text”"
        response.visibility = View.VISIBLE
        response.text = "Thinking…"
        scope.launch {
            response.text = brain.handle(text)
        }
    }
}

/**
 * Apple-Intelligence-style animated glow hugging all four screen edges:
 * a rotating sweep gradient clipped to a thick rounded-rect stroke,
 * breathing via an alpha pulse.
 */
private class GlowBorderView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun start() = animator.start()
    fun stop() = animator.cancel()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val strokeWidth = 14f * resources.displayMetrics.density
        paint.strokeWidth = strokeWidth
        // Rotating multi-hue sweep — blue → purple → pink → cyan.
        val colors = intArrayOf(
            Color.parseColor("#4285F4"),
            Color.parseColor("#A142F4"),
            Color.parseColor("#F44292"),
            Color.parseColor("#42D4F4"),
            Color.parseColor("#4285F4"),
        )
        paint.shader = SweepGradient(width / 2f, height / 2f, colors, null).also { shader ->
            val matrix = android.graphics.Matrix()
            matrix.postRotate(phase * 360f, width / 2f, height / 2f)
            shader.setLocalMatrix(matrix)
        }
        // Breathe between 55% and 100% opacity.
        paint.alpha = (140 + 115 * kotlin.math.sin(phase * 2 * Math.PI).toFloat().let { (it + 1) / 2 }).toInt()
        val inset = strokeWidth / 2f
        val radius = 38f * resources.displayMetrics.density
        canvas.drawRoundRect(
            RectF(inset, inset, width - inset, height - inset),
            radius,
            radius,
            paint,
        )
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
