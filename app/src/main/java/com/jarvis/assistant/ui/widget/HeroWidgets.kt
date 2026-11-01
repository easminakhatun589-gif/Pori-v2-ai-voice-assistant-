package com.jarvis.assistant.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import com.jarvis.assistant.R
import com.jarvis.assistant.util.CompanionEmotionEngine
import kotlin.math.min

class WaveformOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentState: String = "idle"
    private var amplitude: Float = 0f
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#818CF8")
    }

    fun setState(state: String) {
        currentState = state
        orbPaint.color = when (state) {
            "listening" -> Color.parseColor("#22C55E")
            "speaking" -> Color.parseColor("#06B6D4")
            "muted" -> Color.parseColor("#EF4444")
            else -> Color.parseColor("#38BDF8")
        }
        invalidate()
    }

    fun setExternalAudio(amp: Float, bass: Float, high: Float) {
        amplitude = amp.coerceIn(0f, 1f)
        invalidate()
    }

    fun setCompanionEmotion(emotion: CompanionEmotionEngine.CompanionEmotion?) {
        if (emotion != null && currentState == "idle") {
            orbPaint.color = Color.parseColor("#EC4899")
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(width, height) * 0.36f
        val dynamicRadius = baseRadius * (1f + amplitude * 0.2f)
        orbPaint.alpha = 210
        canvas.drawCircle(cx, cy, dynamicRadius, orbPaint)
        ringPaint.alpha = 180
        canvas.drawCircle(cx, cy, dynamicRadius * 1.15f, ringPaint)
    }
}

class LiveHeroAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var avatarState: String = "idle"

    fun setAvatarState(state: String) {
        avatarState = state
        invalidate()
    }

    fun setExternalAudio(amp: Float, bass: Float, high: Float) {
        // Optional subtle visual pulse hook
    }

    fun setPersonaMode(maleJarvisPro: Boolean) {
        setImageResource(
            if (maleJarvisPro) R.drawable.bg_jarvis_pro_hero_stage
            else R.drawable.bg_angel_hero_stage
        )
    }
}

class CurvedHeroDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val deckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E8EEFF")
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val curveDepth = 44f * resources.displayMetrics.density
        path.reset()
        path.moveTo(0f, curveDepth)
        path.quadTo(w / 2f, 0f, w, curveDepth)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, deckPaint)
    }
}
