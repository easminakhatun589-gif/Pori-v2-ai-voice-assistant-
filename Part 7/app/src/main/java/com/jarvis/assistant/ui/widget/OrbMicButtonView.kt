package com.jarvis.assistant.ui.widget

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.jarvis.assistant.data.model.ConversationState
import kotlin.math.*

/**
 * 3D Animated Plasma Nebula Orb Microphone Button matching Screenshot_20261023_202226_Gallery:
 * - Polished metallic golden circular bezel rim (#FFE082, #F59E0B, #B45309, #D97706)
 * - Deep dark circular recessed socket well creating 3D depth into the navigation dock
 * - Spherical 3D plasma nebula orb with organic multi-lobe cyan/turquoise swirling clouds
 * - Volumetric 3D spherical limb darkening for authentic glass sphere depth
 * - High-intensity glass specular lens reflection highlight at upper-center
 * - Audio-reactive energy pulse and acceleration during listening/speaking
 */
class OrbMicButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paints
    private val ambientGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val socketWellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val goldBevelInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val goldBevelOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val sphereBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudFilamentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sphericalLimbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specularHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topGleamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rimBouncePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pulseRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Geometry & Paths
    private val sphereClipPath = Path()
    private val gleamRect = RectF()
    private val scratchRect = RectF()

    // State
    private var isSessionOn: Boolean = false
    private var isMicMuted: Boolean = false
    private var conversationState: ConversationState = ConversationState.IDLE

    // Hold-down Escalating Visual Ripple State
    private data class HoldRippleWave(
        val spawnTimeMs: Long,
        val stage: Int
    )
    private val activeHoldRipples = mutableListOf<HoldRippleWave>()
    private var isHoldingMic: Boolean = false
    private var holdStartTimeMs: Long = 0L
    private var lastRippleSpawnTimeMs: Long = 0L
    private var currentHoldStage: Int = 0
    private val holdRipplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val holdRippleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Audio & Animation Smoothing
    private var targetAudioAmp: Float = 0f
    private var smoothedAudioAmp: Float = 0f
    private var rotationAngle: Float = 0f
    private var swirlTime: Float = 0f
    private var lastFrameTimeMs: Long = SystemClock.uptimeMillis()
    private val startTimeMs: Long = SystemClock.uptimeMillis()

    // Cached Shaders & Dimensions
    private var cachedWidth: Float = 0f
    private var cachedHeight: Float = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun startHoldRipple() {
        val now = SystemClock.uptimeMillis()
        isHoldingMic = true
        holdStartTimeMs = now
        lastRippleSpawnTimeMs = now
        currentHoldStage = 1
        activeHoldRipples.add(HoldRippleWave(spawnTimeMs = now, stage = 1))
        postInvalidateOnAnimation()
    }

    fun stopHoldRipple() {
        isHoldingMic = false
        currentHoldStage = 0
        postInvalidateOnAnimation()
    }

    fun updateOrbMicState(isOn: Boolean, isMuted: Boolean, state: ConversationState) {
        val changed = (this.isSessionOn != isOn) ||
            (this.isMicMuted != isMuted) ||
            (this.conversationState != state)
        this.isSessionOn = isOn
        this.isMicMuted = isMuted
        this.conversationState = state
        if (changed) {
            postInvalidateOnAnimation()
        }
    }

    fun updateAudioFrequency(amp: Float, bass: Float, high: Float) {
        this.targetAudioAmp = (amp * 0.65f + bass * 0.25f + high * 0.10f).coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        cachedWidth = w.toFloat()
        cachedHeight = h.toFloat()
        buildStaticShaders(cachedWidth, cachedHeight)
    }

    private fun buildStaticShaders(w: Float, h: Float) {
        val cx = w * 0.5f
        val cy = h * 0.5f
        val maxR = min(w, h) * 0.5f

        // 1. Ambient dock drop shadow and golden-cyan halo
        val glowR = maxR * 0.98f
        ambientGlowPaint.shader = RadialGradient(
            cx, cy + dp(2f), glowR,
            intArrayOf(
                Color.argb(90, 245, 158, 11),  // Warm gold ambient
                Color.argb(55, 6, 182, 212),   // Cyan ambient
                Color.argb(20, 30, 27, 75),    // Deep violet
                Color.TRANSPARENT
            ),
            floatArrayOf(0.40f, 0.70f, 0.88f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // 2. Metallic Golden Bezel Rim (multi-stop sweep gradient for realistic metallic reflections)
        val goldColors = intArrayOf(
            Color.parseColor("#FFF3BF"), // Top-left creamy highlight
            Color.parseColor("#F59E0B"), // Amber gold
            Color.parseColor("#D97706"), // Rich gold
            Color.parseColor("#78350F"), // Deep bronze shadow on bottom-right
            Color.parseColor("#B45309"), // Warm bronze
            Color.parseColor("#FBBF24"), // Bright gold
            Color.parseColor("#FFF3BF")  // Loop back
        )
        val goldPositions = floatArrayOf(0.0f, 0.18f, 0.38f, 0.58f, 0.75f, 0.90f, 1.0f)
        val sweepMatrix = Matrix()
        sweepMatrix.postRotate(-45f, cx, cy) // Angle specular reflection to upper-left
        val goldSweep = SweepGradient(cx, cy, goldColors, goldPositions)
        goldSweep.setLocalMatrix(sweepMatrix)
        goldRimPaint.shader = goldSweep
        goldRimPaint.strokeWidth = dp(3.2f)

        goldBevelInnerPaint.strokeWidth = dp(0.8f)
        goldBevelInnerPaint.color = Color.argb(160, 69, 26, 3) // Dark bronze groove

        goldBevelOuterPaint.strokeWidth = dp(0.8f)
        goldBevelOuterPaint.color = Color.argb(180, 255, 243, 191) // Crisp light gold rim

        // 3. Socket Well: deep recessed dark crater
        val socketR = maxR * 0.88f
        socketWellPaint.shader = RadialGradient(
            cx, cy + dp(1f), socketR,
            intArrayOf(
                Color.parseColor("#12172A"),
                Color.parseColor("#090D18"),
                Color.parseColor("#03050C")
            ),
            floatArrayOf(0.0f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrameTimeMs) / 1000f).coerceIn(0.001f, 0.08f)
        lastFrameTimeMs = now
        val elapsedSec = (now - startTimeMs) / 1000f

        // Audio reactivity & animation speed factors
        val isSpeaking = conversationState == ConversationState.SPEAKING
        val isThinking = conversationState == ConversationState.THINKING
        val isListening = conversationState == ConversationState.LISTENING

        val syntheticAmp = when {
            !isSessionOn || isMicMuted -> 0.02f * abs(sin(elapsedSec * 1.8f))
            isSpeaking -> 0.28f + 0.24f * abs(sin(elapsedSec * 6.2f))
            isThinking -> 0.32f + 0.22f * abs(sin(elapsedSec * 7.8f))
            isListening -> 0.14f + 0.14f * abs(sin(elapsedSec * 4.0f))
            else -> 0.06f + 0.04f * abs(sin(elapsedSec * 2.2f))
        }
        val targetAmp = max(targetAudioAmp * 1.18f, syntheticAmp).coerceIn(0f, 1f)
        val smoothRate = if (targetAmp > smoothedAudioAmp) 0.28f else 0.16f
        smoothedAudioAmp += (targetAmp - smoothedAudioAmp) * smoothRate

        // Swirl speed accelerates dynamically with ongoing conversation volume and state
        val speedFactor = when {
            !isSessionOn -> 0.8f
            isMicMuted -> 0.5f
            isThinking -> 3.2f + smoothedAudioAmp * 2.2f
            isSpeaking -> 2.5f + smoothedAudioAmp * 2.4f
            isListening -> 1.8f + smoothedAudioAmp * 2.2f
            else -> 1.2f
        }
        swirlTime += dt * speedFactor
        rotationAngle = (rotationAngle + dt * 32f * speedFactor) % 360f

        val cx = w * 0.5f
        val cy = h * 0.5f
        val maxR = min(w, h) * 0.5f
        val pulseScale = (1.0f + 0.018f * sin(elapsedSec * 3.0f) + smoothedAudioAmp * 0.11f).coerceIn(0.95f, 1.16f)

        // ==========================================
        // 1. Ambient Drop Shadow & Golden-Cyan Halo
        // ==========================================
        val glowR = (maxR * (0.94f + smoothedAudioAmp * 0.06f)).coerceAtMost(maxR)
        ambientGlowPaint.alpha = (175 + (smoothedAudioAmp * 80f).toInt()).coerceIn(150, 255)
        canvas.drawCircle(cx, cy, glowR, ambientGlowPaint)

        // ==========================================
        // 2. Recessed Socket Well (Dark Crater)
        // ==========================================
        val socketR = maxR * 0.88f
        canvas.drawCircle(cx, cy, socketR, socketWellPaint)

        // ==========================================
        // 3. Polished Metallic Golden Bezel Rim
        // ==========================================
        val goldRimR = maxR * 0.89f
        canvas.drawCircle(cx, cy, goldRimR, goldRimPaint)
        // Outer specular crest line
        canvas.drawCircle(cx, cy, goldRimR + dp(1.4f), goldBevelOuterPaint)
        // Inner shadow groove line
        canvas.drawCircle(cx, cy, goldRimR - dp(1.4f), goldBevelInnerPaint)

        // ==========================================
        // 4. 3D Spherical Floating Plasma Nebula Orb
        // ==========================================
        val sphereR = (maxR * 0.74f * pulseScale).coerceAtMost(socketR * 0.96f)

        // Clip everything inside the sphere circle for perfect edge containment
        sphereClipPath.reset()
        sphereClipPath.addCircle(cx, cy, sphereR, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(sphereClipPath)

        // Palette definitions (Muted: crimson; Thinking: neural violet/magenta; Listening/Speaking: luminous cyan/emerald)
        val cDeepAbyss = when {
            isMicMuted && isSessionOn -> Color.parseColor("#280505")
            isThinking && isSessionOn -> Color.parseColor("#1E0936")
            else -> Color.parseColor("#021B22")
        }
        val cOceanTeal = when {
            isMicMuted && isSessionOn -> Color.parseColor("#7F1D1D")
            isThinking && isSessionOn -> Color.parseColor("#4C1D95")
            else -> Color.parseColor("#083344")
        }
        val cVividCyan = when {
            isMicMuted && isSessionOn -> Color.parseColor("#DC2626")
            isThinking && isSessionOn -> Color.parseColor("#A855F7")
            else -> Color.parseColor("#06B6D4")
        }
        val cElectricIce = when {
            isMicMuted && isSessionOn -> Color.parseColor("#F87171")
            isThinking && isSessionOn -> Color.parseColor("#E879F9")
            else -> Color.parseColor("#22D3EE")
        }
        val cBrightMint = when {
            isMicMuted && isSessionOn -> Color.parseColor("#FCA5A5")
            isThinking && isSessionOn -> Color.parseColor("#38BDF8")
            else -> Color.parseColor("#2DD4BF")
        }
        val cWhiteHighlight = Color.WHITE

        // A. Deep base cosmic background of the sphere
        sphereBasePaint.shader = RadialGradient(
            cx, cy, sphereR,
            intArrayOf(cOceanTeal, cDeepAbyss, Color.BLACK),
            floatArrayOf(0.0f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, sphereR, sphereBasePaint)

        // B. Dynamic Swirling Plasma Cloud Lobes (Creates the authentic marbled nebula look)
        // Lobe 1: Central Rotating Turquoise Plasma Mass
        val l1Angle = swirlTime * 0.85f
        val l1Dist = sphereR * 0.18f * (1f + 0.2f * sin(swirlTime * 1.4f))
        val l1x = cx + l1Dist * cos(l1Angle)
        val l1y = cy + l1Dist * sin(l1Angle)
        val l1Radius = sphereR * (0.68f + 0.10f * smoothedAudioAmp)
        cloudPaint.shader = RadialGradient(
            l1x, l1y, l1Radius,
            intArrayOf(
                Color.argb(220, Color.red(cElectricIce), Color.green(cElectricIce), Color.blue(cElectricIce)),
                Color.argb(160, Color.red(cVividCyan), Color.green(cVividCyan), Color.blue(cVividCyan)),
                Color.argb(40, Color.red(cOceanTeal), Color.green(cOceanTeal), Color.blue(cOceanTeal)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.40f, 0.78f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(l1x, l1y, l1Radius, cloudPaint)

        // Lobe 2: Secondary Counter-Swirling Mint/Aquamarine Arm
        val l2Angle = -swirlTime * 1.15f + 2.1f
        val l2Dist = sphereR * 0.38f
        val l2x = cx + l2Dist * cos(l2Angle)
        val l2y = cy + l2Dist * sin(l2Angle)
        val l2Radius = sphereR * (0.52f + 0.12f * smoothedAudioAmp)
        cloudPaint.shader = RadialGradient(
            l2x, l2y, l2Radius,
            intArrayOf(
                Color.argb(235, Color.red(cBrightMint), Color.green(cBrightMint), Color.blue(cBrightMint)),
                Color.argb(140, Color.red(cElectricIce), Color.green(cElectricIce), Color.blue(cElectricIce)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.50f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(l2x, l2y, l2Radius, cloudPaint)

        // Lobe 3: Dark Abyss Filament (Swirls through clouds creating the marbled cosmic grain)
        val l3Angle = swirlTime * 0.65f + 3.8f
        val l3Dist = sphereR * 0.32f
        val l3x = cx + l3Dist * cos(l3Angle)
        val l3y = cy + l3Dist * sin(l3Angle)
        val l3Radius = sphereR * 0.50f
        cloudFilamentPaint.shader = RadialGradient(
            l3x, l3y, l3Radius,
            intArrayOf(
                Color.argb(210, 2, 27, 34),
                Color.argb(130, 1, 14, 18),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.60f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(l3x, l3y, l3Radius, cloudFilamentPaint)

        // Lobe 4: High-Velocity Electric Ion Arc
        val l4Angle = swirlTime * 1.7f + 0.8f
        val l4Dist = sphereR * 0.28f * (1f + 0.15f * cos(swirlTime * 2.2f))
        val l4x = cx + l4Dist * cos(l4Angle)
        val l4y = cy + l4Dist * sin(l4Angle)
        val l4Radius = sphereR * (0.42f + 0.14f * smoothedAudioAmp)
        cloudCorePaint.shader = RadialGradient(
            l4x, l4y, l4Radius,
            intArrayOf(
                Color.argb(240, 224, 242, 254), // Luminous icy white-cyan
                Color.argb(180, Color.red(cElectricIce), Color.green(cElectricIce), Color.blue(cElectricIce)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.45f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(l4x, l4y, l4Radius, cloudCorePaint)

        // Lobe 5: Luminous Core Pulse in Center
        val corePulseR = sphereR * (0.35f + 0.25f * smoothedAudioAmp)
        val coreAlpha = (140 + (smoothedAudioAmp * 115f).toInt()).coerceIn(140, 255)
        cloudCorePaint.shader = RadialGradient(
            cx, cy, corePulseR,
            intArrayOf(
                Color.argb(coreAlpha, 224, 242, 254),
                Color.argb((coreAlpha * 0.6f).toInt(), Color.red(cVividCyan), Color.green(cVividCyan), Color.blue(cVividCyan)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.50f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, corePulseR, cloudCorePaint)

        // ==========================================
        // 5. 3D Spherical Limb Shading (Vignette)
        // ==========================================
        // Crucial: darkens the edges of the sphere, bending the 2D surface into a true 3D sphere!
        sphericalLimbPaint.shader = RadialGradient(
            cx, cy, sphereR,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(70, 2, 24, 30),
                Color.argb(175, 1, 14, 18),
                Color.argb(245, 0, 7, 9)
            ),
            floatArrayOf(0.0f, 0.48f, 0.72f, 0.88f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, sphereR, sphericalLimbPaint)

        // ==========================================
        // 6. 3D Specular Glass Lens Reflection & Glare
        // ==========================================
        // Distinctive bright white glare dot at upper-center (matching screenshot position)
        val hx = cx - sphereR * 0.13f
        val hy = cy - sphereR * 0.24f
        val hRadius = sphereR * 0.32f
        specularHighlightPaint.shader = RadialGradient(
            hx, hy, hRadius,
            intArrayOf(
                cWhiteHighlight,                                             // Pure white core
                Color.argb(230, 240, 249, 255),                              // White-cyan corona
                Color.argb(125, Color.red(cElectricIce), Color.green(cElectricIce), Color.blue(cElectricIce)),
                Color.argb(40, Color.red(cVividCyan), Color.green(cVividCyan), Color.blue(cVividCyan)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.20f, 0.48f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(hx, hy, hRadius, specularHighlightPaint)

        // Secondary subtle specular arc along top edge
        val gleamInset = dp(2.2f)
        gleamRect.set(cx - sphereR + gleamInset, cy - sphereR + gleamInset, cx + sphereR - gleamInset, cy + sphereR - gleamInset)
        topGleamPaint.strokeWidth = dp(1.8f)
        topGleamPaint.shader = LinearGradient(
            cx - sphereR * 0.4f, cy - sphereR,
            cx + sphereR * 0.4f, cy - sphereR * 0.6f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(120, 255, 255, 255),
                Color.argb(60, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawArc(gleamRect, 215f, 110f, false, topGleamPaint)

        // Secondary subtle rim bounce on bottom-right (Fresnel refraction inside glass)
        val bx = cx + sphereR * 0.45f
        val by = cy + sphereR * 0.45f
        rimBouncePaint.shader = RadialGradient(
            bx, by, sphereR * 0.42f,
            intArrayOf(
                Color.argb(45, 165, 243, 252),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(bx, by, sphereR * 0.42f, rimBouncePaint)

        // Restore canvas from sphere clipping
        canvas.restore()

        // ==========================================
        // 7. Dynamic Active Audio Wave Rings, Thinking Vortex Arcs & Live Bottom Thin-Line Audio Waveform
        // ==========================================
        if (isSessionOn && !isMicMuted && (isSpeaking || isListening || isThinking)) {
            if (isThinking) {
                val arcR = socketR + dp(2.2f)
                scratchRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
                pulseRingPaint.strokeWidth = dp(2.0f + smoothedAudioAmp * 1.2f)
                for (seg in 0 until 3) {
                    val startAng = (rotationAngle * 1.4f + seg * 120f) % 360f
                    pulseRingPaint.color = if (seg % 2 == 0) {
                        Color.argb(215, 192, 132, 252)
                    } else {
                        Color.argb(200, 56, 189, 248)
                    }
                    canvas.drawArc(scratchRect, startAng, 55f + smoothedAudioAmp * 25f, false, pulseRingPaint)
                }
            } else {
                val wavePhase = (elapsedSec * (1.4f + smoothedAudioAmp * 0.9f)) % 1f
                val waveR = socketR + (maxR - socketR) * wavePhase
                val waveAlpha = ((1f - wavePhase) * (105f + smoothedAudioAmp * 145f)).toInt().coerceIn(0, 225)
                pulseRingPaint.strokeWidth = dp(1.5f + smoothedAudioAmp * 1.4f)
                pulseRingPaint.color = Color.argb(waveAlpha, Color.red(cVividCyan), Color.green(cVividCyan), Color.blue(cVividCyan))
                canvas.drawCircle(cx, cy, waveR, pulseRingPaint)
            }
        }

        // Live Thin-Line Audio Waveform in the bottom area when listening or speaking
        if (isSessionOn && !isMicMuted && (isListening || isSpeaking)) {
            val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1.25f)
                strokeCap = Paint.Cap.ROUND
                color = Color.argb(220, 0, 229, 255) // Luminous cyan thin line
            }
            val numBars = 19
            val waveformWidth = maxR * 1.2f
            val startX = cx - waveformWidth * 0.5f
            val stepX = waveformWidth / (numBars - 1)
            val baselineY = cy + sphereR * 0.72f
            val maxBarHeight = sphereR * 0.30f * (0.4f + 0.6f * smoothedAudioAmp)

            val path = Path()
            for (i in 0 until numBars) {
                val x = startX + i * stepX
                val freqFactor = sin(i * 0.65f + elapsedSec * (if (isSpeaking) 8.5f else 5.2f))
                val barH = maxBarHeight * abs(freqFactor) * (0.3f + 0.7f * sin((i.toFloat() / (numBars - 1)) * PI.toFloat()))
                val py = baselineY - barH
                if (i == 0) {
                    path.moveTo(x, py)
                } else {
                    path.lineTo(x, py)
                }
            }
            canvas.drawPath(path, wavePaint)
        }

        // ==========================================
        // 8. Brief Escalating Hold-Down Visual Ripple Effect
        // ==========================================
        if (isHoldingMic && now - lastRippleSpawnTimeMs >= 145L) {
            lastRippleSpawnTimeMs = now
            currentHoldStage = (currentHoldStage + 1).coerceAtMost(4)
            activeHoldRipples.add(HoldRippleWave(spawnTimeMs = now, stage = currentHoldStage))
        }

        if (activeHoldRipples.isNotEmpty()) {
            val iterator = activeHoldRipples.iterator()
            while (iterator.hasNext()) {
                val wave = iterator.next()
                val durationMs = 460f + wave.stage * 55f
                val progress = ((now - wave.spawnTimeMs) / durationMs).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    iterator.remove()
                    continue
                }
                val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
                val startR = sphereR * 0.88f
                val maxExpandR = maxR * (1.02f + wave.stage * 0.14f)
                val rippleR = startR + (maxExpandR - startR) * eased
                val fadeAlpha = ((1f - progress) * (175f + wave.stage * 20f)).toInt().coerceIn(0, 255)

                holdRippleFillPaint.color = when (wave.stage) {
                    1 -> Color.argb((fadeAlpha * 0.22f).toInt(), 34, 211, 238)
                    2 -> Color.argb((fadeAlpha * 0.28f).toInt(), 45, 212, 191)
                    3 -> Color.argb((fadeAlpha * 0.32f).toInt(), 251, 191, 36)
                    else -> Color.argb((fadeAlpha * 0.38f).toInt(), 255, 255, 255)
                }
                canvas.drawCircle(cx, cy, rippleR, holdRippleFillPaint)

                holdRipplePaint.strokeWidth = dp(1.8f + wave.stage * 0.55f) * (1f - progress * 0.35f)
                holdRipplePaint.color = when (wave.stage) {
                    1 -> Color.argb(fadeAlpha, 34, 211, 238)
                    2 -> Color.argb(fadeAlpha, 165, 243, 252)
                    3 -> Color.argb(fadeAlpha, 253, 224, 71)
                    else -> Color.argb(fadeAlpha, 255, 255, 255)
                }
                canvas.drawCircle(cx, cy, rippleR, holdRipplePaint)
            }
        }

        // Continuously animate at 60fps
        if (isAttachedToWindow && visibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }
}
