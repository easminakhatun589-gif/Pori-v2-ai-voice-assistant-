package com.jarvis.assistant.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.audio.PariRealVoiceSpeaker
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.ui.widget.WaveformOrbView
import com.jarvis.assistant.util.HapticFeedbackHelper
import com.jarvis.assistant.util.HapticFeedbackManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LiveVoiceFragment : Fragment() {

    private val viewModel: MainViewModel
        get() = (requireActivity() as MainActivity).viewModel
    private val preferences by lazy { JarvisApp.instance.preferences }

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvPersonaSubtitle: TextView
    private lateinit var orbView: WaveformOrbView
    private lateinit var tvLiveUser: TextView
    private lateinit var tvLiveAssistant: TextView
    private lateinit var btnPowerToggle: Button
    private lateinit var btnMuteToggle: Button
    private val personaButtons = mutableMapOf<String, TextView>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val scroll = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#0B1120"))
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }

        // 1. Header Row
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvHeaderTitle = TextView(ctx).apply {
            text = "🎙️ Live Voice Studio (লাইভ ভয়েস)"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        tvPersonaSubtitle = TextView(ctx).apply {
            text = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(ctx).modeBadge
            textSize = 12f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, dp(3), 0, 0)
        }

        titleCol.addView(tvHeaderTitle)
        titleCol.addView(tvPersonaSubtitle)

        tvStatusBadge = TextView(ctx).apply {
            text = "● READY"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#22C55E"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#22C55E"))
            }
        }

        headerRow.addView(titleCol)
        headerRow.addView(tvStatusBadge)
        root.addView(headerRow)

        // 2. Persona Selector Bar
        val personaRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
        }

        val personas = listOf(
            "Pari" to "🌸 Pari",
            "Jarvis" to "👑 Jarvis",
            "Kabir" to "⚡ Kabir",
            "TechGPT" to "💡 TechGPT"
        )

        for ((key, label) in personas) {
            val pill = TextView(ctx).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minHeight = dp(42)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                    marginStart = dp(4)
                }
                setOnClickListener {
                    HapticFeedbackHelper.performControlClick(it)
                    preferences.personality = key
                    if (key == "Pari") preferences.voice = "Aoede"
                    if (key == "Kabir") preferences.voice = "Fenrir"
                    if (key == "TechGPT") preferences.voice = "Puck"
                    updatePersonaSelectionUI()
                }
            }
            personaButtons[key] = pill
            personaRow.addView(pill)
        }
        root.addView(personaRow)

        // 3. Center Interactive Waveform Orb
        orbView = WaveformOrbView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(170), dp(170)).apply {
                topMargin = dp(20)
                bottomMargin = dp(12)
            }
            contentDescription = "Live Voice Orb"
            setOnClickListener {
                HapticFeedbackManager.triggerOrbPress(it)
                viewModel.toggleMicOrOrb(ctx)
            }
            setOnLongClickListener {
                HapticFeedbackManager.triggerPowerToggle(!viewModel.isSessionOn.value, it)
                viewModel.togglePowerSession(ctx)
                true
            }
        }
        root.addView(orbView)

        val tvOrbHint = TextView(ctx).apply {
            text = "Tap Orb to Speak / Mute • Hold Orb to Toggle Session"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }
        root.addView(tvOrbHint)

        // 4. Live Transcript & Assistant Reply Card
        val transcriptCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#334155"))
            }
        }

        tvLiveUser = TextView(ctx).apply {
            text = "🎙️ You: Tap 'Start Live Voice' and speak in Bangla, Banglish, Hindi, or English..."
            textSize = 13.5f
            setTextColor(Color.parseColor("#A7F3D0"))
        }

        tvLiveAssistant = TextView(ctx).apply {
            text = "🤖 Assistant: হ্যালো! আমি প্রস্তুত। আপনি কথা বললে বা নিচের বাটনে চাপ দিলে আমি সাথে সাথে উত্তর দেব।"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#BAE6FD"))
            setPadding(0, dp(10), 0, 0)
        }

        transcriptCard.addView(tvLiveUser)
        transcriptCard.addView(tvLiveAssistant)
        root.addView(transcriptCard)

        // 5. Primary Control Buttons (48dp touch targets)
        val controlsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
        }

        btnPowerToggle = Button(ctx).apply {
            text = "▶ Start Live Voice"
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginEnd = dp(6)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#16A34A"))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                viewModel.togglePowerSession(ctx)
            }
        }

        btnMuteToggle = Button(ctx).apply {
            text = "🎙️ Mic Active"
            isAllCaps = false
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                viewModel.toggleMicOrOrb(ctx)
            }
        }

        val btnTestVoice = Button(ctx).apply {
            text = "🔊 Test Voice"
            isAllCaps = false
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C3AED"))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                viewModel.speakLiveVoiceTestGreeting(ctx)
            }
        }

        controlsRow.addView(btnPowerToggle)
        controlsRow.addView(btnMuteToggle)
        controlsRow.addView(btnTestVoice)
        root.addView(controlsRow)

        // 6. Instant Voice Command Bar
        val quickVoiceBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
        }

        val etVoiceCommand = EditText(ctx).apply {
            hint = "ভয়েস কমান্ড টেস্ট করুন (e.g. হাই পরী কেমন আছো?)"
            textSize = 13.5f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#475569"))
            }
        }

        val btnSpeakCommand = Button(ctx).apply {
            text = "🎙️ Speak"
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7"))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                val cmd = etVoiceCommand.text?.toString()?.trim().orEmpty()
                if (cmd.isNotEmpty()) {
                    etVoiceCommand.setText("")
                    viewModel.simulateOrSendVoiceTurn(ctx, cmd)
                } else {
                    viewModel.speakLiveVoiceTestGreeting(ctx)
                }
            }
        }

        quickVoiceBar.addView(etVoiceCommand)
        quickVoiceBar.addView(btnSpeakCommand)
        root.addView(quickVoiceBar)

        scroll.addView(root)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updatePersonaSelectionUI()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.isSessionOn,
                        viewModel.isMicMuted,
                        viewModel.conversationState
                    ) { isOn, isMuted, state ->
                        Triple(isOn, isMuted, state)
                    }.collect { (isOn, isMuted, state) ->
                        if (!isOn) {
                            orbView.setState("idle")
                            tvStatusBadge.text = "● STANDBY"
                            tvStatusBadge.setTextColor(Color.parseColor("#38BDF8"))
                            btnPowerToggle.text = "▶ Start Live Voice"
                            btnPowerToggle.background = GradientDrawable().apply {
                                setColor(Color.parseColor("#16A34A"))
                                cornerRadius = (16 * resources.displayMetrics.density)
                            }
                            btnMuteToggle.text = "🎙️ Tap to Talk"
                        } else if (isMuted) {
                            orbView.setState("muted")
                            tvStatusBadge.text = "● MIC MUTED"
                            tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                            btnPowerToggle.text = "⏹ Stop Session"
                            btnPowerToggle.background = GradientDrawable().apply {
                                setColor(Color.parseColor("#DC2626"))
                                cornerRadius = (16 * resources.displayMetrics.density)
                            }
                            btnMuteToggle.text = "🔇 Unmute Mic"
                        } else {
                            val orbState = when (state) {
                                ConversationState.SPEAKING -> "speaking"
                                ConversationState.LISTENING, ConversationState.THINKING -> "listening"
                                ConversationState.IDLE -> "idle"
                            }
                            orbView.setState(orbState)
                            tvStatusBadge.text = when (state) {
                                ConversationState.SPEAKING -> "🔊 SPEAKING"
                                ConversationState.LISTENING, ConversationState.THINKING -> "🎙️ LISTENING"
                                ConversationState.IDLE -> "● LIVE"
                            }
                            tvStatusBadge.setTextColor(Color.parseColor("#22C55E"))
                            btnPowerToggle.text = "⏹ Stop Session"
                            btnPowerToggle.background = GradientDrawable().apply {
                                setColor(Color.parseColor("#DC2626"))
                                cornerRadius = (16 * resources.displayMetrics.density)
                            }
                            btnMuteToggle.text = "🎙️ Mute Mic"
                        }
                    }
                }

                launch {
                    viewModel.liveUserTranscript.collect { text ->
                        if (text.isNotBlank()) {
                            tvLiveUser.text = "🎙️ You: $text"
                        }
                    }
                }

                launch {
                    viewModel.liveAssistantResponse.collect { text ->
                        if (text.isNotBlank()) {
                            tvLiveAssistant.text = "🤖 Assistant: $text"
                        }
                    }
                }

                launch {
                    viewModel.audioFrequency.collect { freq ->
                        orbView.setExternalAudio(freq.amplitude, freq.bass, freq.high)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePersonaSelectionUI()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded) {
            updatePersonaSelectionUI()
        }
    }

    private fun updatePersonaSelectionUI() {
        val ctx = context ?: return
        val density = resources.displayMetrics.density
        val current = preferences.personality
        tvPersonaSubtitle.text = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(ctx).modeBadge

        for ((key, btn) in personaButtons) {
            val selected = current.contains(key, ignoreCase = true)
            btn.setTextColor(if (selected) Color.WHITE else Color.parseColor("#CBD5E1"))
            btn.background = GradientDrawable().apply {
                setColor(if (selected) Color.parseColor("#2563EB") else Color.parseColor("#1E293B"))
                cornerRadius = 14 * density
                setStroke((1 * density).toInt(), if (selected) Color.parseColor("#60A5FA") else Color.parseColor("#334155"))
            }
        }
    }
}
