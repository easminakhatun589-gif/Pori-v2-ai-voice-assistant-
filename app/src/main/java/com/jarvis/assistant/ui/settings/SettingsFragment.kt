package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.alarm.ui.ClockActivity
import com.jarvis.assistant.audio.PariRealVoiceSpeaker
import com.jarvis.assistant.builder.VoiceBuilderActivity
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.trading.ui.TradingAnalyzerActivity
import com.jarvis.assistant.ui.agent.AgentDashboardActivity
import com.jarvis.assistant.ui.memory.MemoryActivity
import com.jarvis.assistant.ui.permission.PermissionActivity
import com.jarvis.assistant.util.HapticFeedbackHelper

class SettingsFragment : Fragment() {

    private val preferences by lazy { JarvisApp.instance.preferences }

    private lateinit var tvActiveProfileBadge: TextView
    private lateinit var tvSelectedLanguage: TextView
    private lateinit var tvSelectedVoice: TextView
    private lateinit var etUserName: EditText
    private lateinit var etUserLocation: EditText
    private lateinit var etApiKey: EditText
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
            setBackgroundColor(Color.parseColor("#EEF2FF"))
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }

        // 1. Header Card
        val headerCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(20).toFloat()
            }
        }

        val tvHeaderTitle = TextView(ctx).apply {
            text = "⚙️ Settings & AI Studio (সেটিংস)"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        tvActiveProfileBadge = TextView(ctx).apply {
            text = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(ctx).modeBadge
            textSize = 12.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, dp(4), 0, 0)
        }

        headerCard.addView(tvHeaderTitle)
        headerCard.addView(tvActiveProfileBadge)
        root.addView(headerCard)

        // 2. Persona & Companion Mode Card
        val personaCard = createSectionCard(ctx, "🎭 AI Persona & Companion Mode")

        val personaRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }

        val personas = listOf(
            "Pari" to "🌸 Pari AI",
            "Jarvis" to "👑 Jarvis",
            "Kabir" to "⚡ Kabir",
            "TechGPT" to "💡 TechGPT"
        )

        for ((key, label) in personas) {
            val btn = TextView(ctx).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minHeight = dp(44)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                }
                setOnClickListener {
                    HapticFeedbackHelper.performControlClick(it)
                    preferences.personality = key
                    when (key) {
                        "Pari" -> preferences.voice = "Aoede"
                        "Kabir" -> preferences.voice = "Fenrir"
                        "TechGPT" -> preferences.voice = "Puck"
                    }
                    refreshSettingsUI()
                }
            }
            personaButtons[key] = btn
            personaRow.addView(btn)
        }
        personaCard.addView(personaRow)
        root.addView(personaCard)

        // 3. Real Voice & Language Card
        val voiceCard = createSectionCard(ctx, "🔊 Real Voice, Tone & Language")

        tvSelectedVoice = TextView(ctx).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, dp(6), 0, dp(6))
        }
        voiceCard.addView(tvSelectedVoice)

        val voiceBtnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnChangeVoice = createActionButton(ctx, "🎙️ Select Voice", "#2563EB").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                showVoicePickerDialog()
            }
        }

        val btnTestVoice = createActionButton(ctx, "🔊 Test Voice", "#7C3AED").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                val profile = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(ctx)
                val sample = if (profile.isFemaleVoice) {
                    "হ্যালো বন্ধু! আমি পরী। তোমার সাথে কথা বলতে আমার খুব ভালো লাগে।"
                } else {
                    "Hello! I am ${profile.modeTitle}. All voice systems are online and ready."
                }
                PariRealVoiceSpeaker.speakInActiveModeVoice(ctx, sample)
            }
        }

        voiceBtnRow.addView(btnChangeVoice)
        voiceBtnRow.addView(btnTestVoice)
        voiceCard.addView(voiceBtnRow)

        tvSelectedLanguage = TextView(ctx).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, dp(12), 0, dp(6))
        }
        voiceCard.addView(tvSelectedLanguage)

        val btnChangeLanguage = createActionButton(ctx, "🌐 Change Conversation Language", "#0284C7").apply {
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                showLanguagePickerDialog()
            }
        }
        voiceCard.addView(btnChangeLanguage)
        root.addView(voiceCard)

        // 4. User Profile & Location Card
        val profileCard = createSectionCard(ctx, "👤 User Profile & Location")

        etUserName = EditText(ctx).apply {
            hint = "Your Name (আপনার নাম)"
            setText(preferences.userName)
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createInputBackground(ctx)
        }

        etUserLocation = EditText(ctx).apply {
            hint = "Your City / Location (e.g., Dhaka)"
            setText(preferences.userLocation)
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            background = createInputBackground(ctx)
        }

        val btnSaveProfile = createActionButton(ctx, "💾 Save Profile & Location", "#16A34A").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                preferences.userName = etUserName.text?.toString()?.trim().orEmpty()
                preferences.userLocation = etUserLocation.text?.toString()?.trim().orEmpty()
                Toast.makeText(ctx, "Profile saved!", Toast.LENGTH_SHORT).show()
                refreshSettingsUI()
            }
        }

        profileCard.addView(etUserName)
        profileCard.addView(etUserLocation)
        profileCard.addView(btnSaveProfile)
        root.addView(profileCard)

        // 5. Gemini API Key & Cloud Connection Card
        val apiCard = createSectionCard(ctx, "🔑 Gemini API Key & Cloud Connection")

        etApiKey = EditText(ctx).apply {
            hint = "Paste Gemini API Key (AIzaSy...)"
            setText(preferences.apiKey)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 13.5f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createInputBackground(ctx)
        }

        val btnSaveApi = createActionButton(ctx, "✓ Save Gemini API Key", "#2563EB").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                preferences.apiKey = etApiKey.text?.toString()?.trim().orEmpty()
                Toast.makeText(ctx, "API Key updated!", Toast.LENGTH_SHORT).show()
            }
        }

        apiCard.addView(etApiKey)
        apiCard.addView(btnSaveApi)
        root.addView(apiCard)

        // 6. Advanced Modules & System Tools Card
        val toolsCard = createSectionCard(ctx, "🚀 Advanced AI Modules & System Tools")

        val modules = listOf(
            "🧠 Saved Memories & Recall Core" to MemoryActivity::class.java,
            "🤖 Master Autonomous Agent Dashboard" to AgentDashboardActivity::class.java,
            "⏰ Clock & Voice Alarms" to ClockActivity::class.java,
            "📊 Quotex & Market Chart AI Scanner" to TradingAnalyzerActivity::class.java,
            "🛠️ Voice Builder & Code Studio" to VoiceBuilderActivity::class.java,
            "🛡️ System Permissions & Accessibility" to PermissionActivity::class.java,
            "📖 Mobile Voice Commands Guide (A-Z)" to VoiceCommandsGuideActivity::class.java
        )

        for ((label, activityClass) in modules) {
            val btn = createActionButton(ctx, label, "#1E293B").apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
                setOnClickListener {
                    HapticFeedbackHelper.performControlClick(it)
                    startActivity(Intent(ctx, activityClass))
                }
            }
            toolsCard.addView(btn)
        }

        root.addView(toolsCard)
        scroll.addView(root)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshSettingsUI()
    }

    override fun onResume() {
        super.onResume()
        refreshSettingsUI()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded) {
            refreshSettingsUI()
        }
    }

    private fun refreshSettingsUI() {
        val ctx = context ?: return
        val density = resources.displayMetrics.density
        val profile = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(ctx)
        tvActiveProfileBadge.text = profile.modeBadge
        tvSelectedVoice.text = "Active Voice: ${profile.cloudVoiceName} (${profile.modeTitle}) • Tone: ${preferences.personaVoiceTone}"
        tvSelectedLanguage.text = "Conversation Language: ${preferences.language.ifBlank { "Auto" }}"

        val currentPersona = preferences.personality
        for ((key, btn) in personaButtons) {
            val selected = currentPersona.contains(key, ignoreCase = true)
            btn.setTextColor(if (selected) Color.WHITE else Color.parseColor("#1E293B"))
            btn.background = GradientDrawable().apply {
                setColor(if (selected) Color.parseColor("#2563EB") else Color.parseColor("#E2E8F0"))
                cornerRadius = 12 * density
            }
        }
    }

    private fun showVoicePickerDialog() {
        val ctx = context ?: return
        val voices = arrayOf(
            "Aoede (Pari Sweet Female Voice 🌸)",
            "Kore (Warm Female Voice 🌸)",
            "Leda (Calm Female Voice 🌸)",
            "Fenrir (Kabir Deep Male Voice ⚡)",
            "Charon (Jarvis Pro Male Voice 👑)",
            "Puck (TechGPT Energetic Voice 💡)"
        )
        val rawNames = arrayOf("Aoede", "Kore", "Leda", "Fenrir", "Charon", "Puck")
        val currentIdx = rawNames.indexOfFirst { it.equals(preferences.voice, ignoreCase = true) }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Select Real Voice Model")
            .setSingleChoiceItems(voices, currentIdx) { dialog, which ->
                preferences.voice = rawNames[which]
                refreshSettingsUI()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguagePickerDialog() {
        val ctx = context ?: return
        val languages = GeminiConstants.SUPPORTED_LANGUAGES.toTypedArray()
        val currentIdx = languages.indexOfFirst { it.equals(preferences.language, ignoreCase = true) }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Select Conversation Language")
            .setSingleChoiceItems(languages, currentIdx) { dialog, which ->
                preferences.language = languages[which]
                refreshSettingsUI()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createSectionCard(ctx: android.content.Context, title: String): LinearLayout {
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#CBD5E1"))
            }
            addView(
                TextView(ctx).apply {
                    text = title
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#0F172A"))
                    setPadding(0, 0, 0, dp(6))
                }
            )
        }
    }

    private fun createActionButton(ctx: android.content.Context, label: String, colorHex: String): Button {
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        return Button(ctx).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
                cornerRadius = dp(14).toFloat()
            }
        }
    }

    private fun createInputBackground(ctx: android.content.Context): GradientDrawable {
        val density = ctx.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(Color.parseColor("#F8FAFC"))
            cornerRadius = 12 * density
            setStroke((1 * density).toInt(), Color.parseColor("#CBD5E1"))
        }
    }
}
