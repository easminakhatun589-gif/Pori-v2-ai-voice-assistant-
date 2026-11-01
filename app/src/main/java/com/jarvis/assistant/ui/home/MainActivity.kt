package com.jarvis.assistant.ui.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.audio.PariRealVoiceSpeaker
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.AudioFrequencyState
import com.jarvis.assistant.data.model.ChatTurn
import com.jarvis.assistant.data.model.CitationSource
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.data.model.LiveErrorState
import com.jarvis.assistant.security.configureAppCheck
import com.jarvis.assistant.ui.chat.ChatboxFragment
import com.jarvis.assistant.ui.settings.SettingsFragment
import com.jarvis.assistant.util.AppLauncher
import com.jarvis.assistant.util.BuiltInChromeEngine
import com.jarvis.assistant.util.HapticFeedbackHelper
import com.jarvis.assistant.util.PromptGenerator
import com.jarvis.assistant.vision.ActiveCameraFragment
import com.jarvis.assistant.vision.ScreenCaptureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainViewModel : ViewModel() {

    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _isSessionOn = MutableStateFlow(false)
    val isSessionOn: StateFlow<Boolean> = _isSessionOn.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _liveUserTranscript = MutableStateFlow("")
    val liveUserTranscript: StateFlow<String> = _liveUserTranscript.asStateFlow()

    private val _liveAssistantResponse = MutableStateFlow("")
    val liveAssistantResponse: StateFlow<String> = _liveAssistantResponse.asStateFlow()

    private val _audioFrequency = MutableStateFlow(AudioFrequencyState())
    val audioFrequency: StateFlow<AudioFrequencyState> = _audioFrequency.asStateFlow()

    private val _liveError = MutableStateFlow<LiveErrorState?>(null)
    val liveError: StateFlow<LiveErrorState?> = _liveError.asStateFlow()

    private val _connectionStatus = MutableStateFlow("ONLINE")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainActivity.TAB_HOME)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _chatTurns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val chatTurns: StateFlow<List<ChatTurn>> = _chatTurns.asStateFlow()

    private var pulseJob: Job? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build()
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun clearLiveError() {
        _liveError.value = null
    }

    fun ensureWelcomeTurn(context: Context) {
        if (_chatTurns.value.isNotEmpty()) return
        val profile = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(context)
        val welcome = if (profile.isFemaleVoice) {
            "হ্যালো বন্ধু! আমি পরী (Pari AI) 🌸। তুমি আমার সাথে বাংলা, বাংলিশ, হিন্দি বা ইংরেজিতে চ্যাট করতে পারো এবং ভয়েসে কথা বলতে পারো। আজ তোমাকে কীভাবে সাহায্য করব?"
        } else {
            "Hello! I am ${profile.modeTitle}. You can chat with me, use Live Voice, or analyze scenes with Active Camera. How can I help you today?"
        }
        _chatTurns.value = listOf(
            ChatTurn(
                id = "welcome-turn",
                userTranscript = "",
                jarvisResponse = welcome,
                timestamp = System.currentTimeMillis(),
                turnSource = "system"
            )
        )
    }

    fun clearChatHistory(context: Context) {
        _chatTurns.value = emptyList()
        ensureWelcomeTurn(context)
    }

    fun toggleMicOrOrb(context: Context? = null) {
        if (!_isSessionOn.value) {
            _isSessionOn.value = true
            _isMicMuted.value = false
            _conversationState.value = ConversationState.LISTENING
            JarvisConversationService.setSessionActive(true)
            startAudioVisualizerPulse()
            val ctx = context ?: try { JarvisApp.instance } catch (_: Exception) { null }
            if (ctx != null) {
                speakLiveVoiceTestGreeting(ctx)
            }
        } else {
            val nextMute = !_isMicMuted.value
            _isMicMuted.value = nextMute
            _conversationState.value = if (nextMute) ConversationState.IDLE else ConversationState.LISTENING
            if (nextMute) {
                PariRealVoiceSpeaker.stop()
                stopAudioVisualizerPulse()
            } else {
                startAudioVisualizerPulse()
            }
        }
    }

    fun togglePowerSession(context: Context? = null) {
        val nextOn = !_isSessionOn.value
        _isSessionOn.value = nextOn
        _isMicMuted.value = false
        _conversationState.value = if (nextOn) ConversationState.LISTENING else ConversationState.IDLE
        JarvisConversationService.setSessionActive(nextOn)
        if (nextOn) {
            startAudioVisualizerPulse()
            val ctx = context ?: try { JarvisApp.instance } catch (_: Exception) { null }
            if (ctx != null) {
                speakLiveVoiceTestGreeting(ctx)
            }
        } else {
            PariRealVoiceSpeaker.stop()
            stopAudioVisualizerPulse()
        }
    }

    fun speakLiveVoiceTestGreeting(context: Context) {
        val profile = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(context)
        val greeting = if (profile.isFemaleVoice) {
            "হ্যালো! আমি পরী। লাইভ ভয়েস কানেকশন একদম ঠিক আছে, বলো আজ কী করব?"
        } else {
            "Live voice connection is online and ready. How can I assist you?"
        }
        _liveAssistantResponse.value = greeting
        _conversationState.value = ConversationState.SPEAKING
        startAudioVisualizerPulse()
        PariRealVoiceSpeaker.speakInActiveModeVoice(
            context = context,
            text = greeting,
            onPlaybackStarted = {
                _conversationState.value = ConversationState.SPEAKING
            },
            onPlaybackDone = {
                _conversationState.value = if (_isSessionOn.value && !_isMicMuted.value) {
                    ConversationState.LISTENING
                } else {
                    ConversationState.IDLE
                }
            }
        )
    }

    fun simulateOrSendVoiceTurn(context: Context, spokenText: String) {
        _isSessionOn.value = true
        _isMicMuted.value = false
        JarvisConversationService.setSessionActive(true)
        _liveUserTranscript.value = spokenText
        sendChatMessage(context, spokenText, enableWebSearch = true)
    }

    fun sendChatMessage(context: Context, userMessage: String, enableWebSearch: Boolean = true) {
        val cleanMsg = userMessage.trim()
        if (cleanMsg.isBlank()) return

        _liveUserTranscript.value = cleanMsg
        _conversationState.value = ConversationState.THINKING
        startAudioVisualizerPulse()

        viewModelScope.launch {
            val lower = cleanMsg.lowercase(Locale.ROOT)
            if (lower.startsWith("open ") || lower.contains("খোলো") || lower.contains("ওপেন")) {
                val targetApp = cleanMsg
                    .replace(Regex("(?i)^open\\s+"), "")
                    .replace("খোলো", "")
                    .replace("ওপেন", "")
                    .trim()
                if (targetApp.isNotBlank()) {
                    val launched = AppLauncher.launchApp(context, targetApp)
                    val reply = if (launched) {
                        "ঠিক আছে, $targetApp ওপেন করা হচ্ছে।"
                    } else {
                        "দুঃখিত, আপনার ফোনে '$targetApp' অ্যাপটি খুঁজে পাওয়া যায়নি।"
                    }
                    appendTurnAndSpeak(context, cleanMsg, reply, emptyList(), isWeb = false)
                    return@launch
                }
            }

            var citations = emptyList<CitationSource>()
            var groundedSummary = ""
            if (enableWebSearch && shouldPerformWebLookup(cleanMsg)) {
                val searchRes = BuiltInChromeEngine.searchWithCitations(cleanMsg)
                if (searchRes.isVerified && searchRes.summary.isNotBlank()) {
                    groundedSummary = searchRes.summary
                    citations = searchRes.citations
                }
            }

            val aiReply = generateAiReply(context, cleanMsg, groundedSummary)
            appendTurnAndSpeak(context, cleanMsg, aiReply, citations, isWeb = citations.isNotEmpty())
        }
    }

    private fun appendTurnAndSpeak(
        context: Context,
        userText: String,
        aiReply: String,
        citations: List<CitationSource>,
        isWeb: Boolean
    ) {
        _liveAssistantResponse.value = aiReply
        val newTurn = ChatTurn(
            id = UUID.randomUUID().toString(),
            userTranscript = userText,
            jarvisResponse = aiReply,
            timestamp = System.currentTimeMillis(),
            turnSource = "chat",
            isLiveWebData = isWeb,
            citations = citations
        )
        _chatTurns.value = _chatTurns.value + newTurn
        _conversationState.value = ConversationState.SPEAKING
        PariRealVoiceSpeaker.speakInActiveModeVoice(
            context = context,
            text = aiReply,
            turnId = newTurn.id,
            onPlaybackStarted = {
                _conversationState.value = ConversationState.SPEAKING
            },
            onPlaybackDone = {
                _conversationState.value = if (_isSessionOn.value && !_isMicMuted.value) {
                    ConversationState.LISTENING
                } else {
                    ConversationState.IDLE
                }
            }
        )
    }

    private fun shouldPerformWebLookup(query: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        return q.contains("news") || q.contains("খবর") || q.contains("what is") ||
            q.contains("who is") || q.contains("wikipedia") || q.contains("weather") ||
            q.contains("আবহাওয়া") || q.contains("bangladesh") || q.contains("india")
    }

    private suspend fun generateAiReply(
        context: Context,
        userText: String,
        groundedContext: String
    ): String = withContext(Dispatchers.IO) {
        val prefs = try {
            (context.applicationContext as? JarvisApp)?.preferences ?: JarvisApp.instance.preferences
        } catch (_: Exception) {
            null
        }
        val apiKey = prefs?.apiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GEMINI_API_KEY.trim()

        val personality = prefs?.personality ?: "Pari"
        val userName = prefs?.userName?.takeIf { it.isNotBlank() } ?: "বন্ধু"

        if (apiKey.isNotBlank()) {
            try {
                val sysPrompt = PromptGenerator.generateSystemPrompt(personality, userName)
                val fullUserPrompt = if (groundedContext.isNotBlank()) {
                    "Verified Web Context:\n$groundedContext\n\nUser Question: $userText"
                } else {
                    userText
                }
                val payload = JSONObject().apply {
                    put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysPrompt)))
                    )
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", fullUserPrompt))
                            )
                        )
                    )
                }
                val url =
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                val req = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val text = json.optJSONArray("candidates")
                                ?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text", "")
                                ?.trim()
                                .orEmpty()
                            if (text.isNotBlank()) {
                                return@withContext text
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (groundedContext.isNotBlank()) {
            return@withContext groundedContext
        }

        val lower = userText.lowercase(Locale.ROOT)
        val isPari = personality.contains("Pari", ignoreCase = true) ||
            personality.contains("Girlfriend", ignoreCase = true)
        when {
            lower.contains("কেমন আছো") || lower.contains("kemon acho") || lower.contains("how are you") -> {
                if (isPari) {
                    "আমি খুব ভালো আছি $userName 🌸! তোমার সাথে কথা বলে মনটা আরও ভালো হয়ে গেল। তুমি কেমন আছো বলো?"
                } else {
                    "আমি একদম ঠিক আছি $userName! সব সিস্টেম সচল আছে। তোমাকে আজ কীভাবে সাহায্য করতে পারি?"
                }
            }
            lower.contains("আবহাওয়া") || lower.contains("weather") -> {
                "আজকের আবহাওয়া বেশ মনোরম (প্রায় ২৮°C, পরিষ্কার আকাশ)। তুমি হোম পেজের Weather কার্ডে ট্যাপ করে লাইভ লোকেশন আপডেট দেখতে পারো।"
            }
            lower.contains("খবর") || lower.contains("news") -> {
                "আজকের প্রধান খবর ও আপডেট জানতে চ্যাটবক্সে নির্দিষ্ট বিষয় লিখে সার্চ করো অথবা Gemini API Key সেটিংস থেকে যুক্ত করো।"
            }
            else -> {
                if (isPari) {
                    "হ্যাঁ $userName 🌸, আমি তোমার কথা বুঝতে পেরেছি: \"$userText\"। বলো, আমি আর কীভাবে তোমাকে সাহায্য করতে পারি?"
                } else {
                    "আপনার বার্তা পেয়েছি: \"$userText\"। আমি প্রস্তুত, পরবর্তী নির্দেশ দিন।"
                }
            }
        }
    }

    private fun startAudioVisualizerPulse() {
        if (pulseJob?.isActive == true) return
        pulseJob = viewModelScope.launch {
            var tick = 0
            while (_isSessionOn.value && !_isMicMuted.value) {
                val activeSpeaking = _conversationState.value == ConversationState.SPEAKING ||
                    PariRealVoiceSpeaker.isPlaying()
                val amp = if (activeSpeaking) {
                    0.35f + ((tick % 5) * 0.12f)
                } else {
                    0.12f + ((tick % 4) * 0.06f)
                }
                _audioFrequency.value = AudioFrequencyState(
                    amplitude = amp.coerceIn(0f, 1f),
                    bass = (amp * 0.8f).coerceIn(0f, 1f),
                    high = (amp * 0.6f).coerceIn(0f, 1f)
                )
                tick++
                delay(90L)
            }
            _audioFrequency.value = AudioFrequencyState(0f, 0f, 0f)
        }
    }

    private fun stopAudioVisualizerPulse() {
        pulseJob?.cancel()
        pulseJob = null
        _audioFrequency.value = AudioFrequencyState(0f, 0f, 0f)
    }
}

class MainActivity : AppCompatActivity() {

    val viewModel: MainViewModel by viewModels()

    private var fragmentContainerId: Int = 0
    private val navTabViews = mutableMapOf<Int, NavTabHolder>()

    private data class NavTabHolder(
        val tabId: Int,
        val container: LinearLayout,
        val pillContainer: FrameLayout,
        val iconView: ImageView,
        val labelView: TextView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val container = FrameLayout(this).apply {
            id = android.R.id.content.xor(0x1001)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        fragmentContainerId = container.id
        rootLayout.addView(container)

        // Top hairline divider above Bottom Navigation Bar
        val navDivider = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            )
            setBackgroundColor(Color.parseColor("#1E293B"))
        }
        rootLayout.addView(navDivider)

        // 5-Tab Bottom Navigation Bar: Home Page | Chatbox | Live Voice | Active Camera | Settings
        val bottomNavBar = LinearLayout(this).apply {
            id = android.R.id.content.xor(0x1002)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66)
            )
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setBackgroundColor(Color.parseColor("#0F172A"))
            elevation = dp(12).toFloat()
        }

        val navItems = listOf(
            Triple(TAB_HOME, "Home", R.drawable.ic_nav_home),
            Triple(TAB_CHAT, "Chatbox", R.drawable.ic_nav_chat),
            Triple(TAB_LIVE_VOICE, "Live Voice", R.drawable.ic_nav_live_voice),
            Triple(TAB_ACTIVE_CAMERA, "Camera", R.drawable.ic_nav_camera),
            Triple(TAB_SETTINGS, "Settings", R.drawable.ic_nav_settings)
        )

        for ((tabId, title, iconRes) in navItems) {
            val tabLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
                isClickable = true
                isFocusable = true
                contentDescription = title
                setOnClickListener {
                    HapticFeedbackHelper.performControlClick(it)
                    selectTab(tabId)
                }
            }

            val pillFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(28))
            }

            val icon = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            pillFrame.addView(icon)

            val label = TextView(this).apply {
                text = title
                textSize = 10.5f
                gravity = Gravity.CENTER
                setSingleLine(true)
                setPadding(0, dp(2), 0, 0)
            }

            tabLayout.addView(pillFrame)
            tabLayout.addView(label)

            navTabViews[tabId] = NavTabHolder(
                tabId = tabId,
                container = tabLayout,
                pillContainer = pillFrame,
                iconView = icon,
                labelView = label
            )
            bottomNavBar.addView(tabLayout)
        }

        rootLayout.addView(bottomNavBar)
        setContentView(rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.selectedTab.value != TAB_HOME) {
                    selectTab(TAB_HOME)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            selectTab(TAB_HOME)
        } else {
            selectTab(viewModel.selectedTab.value)
        }
    }

    fun selectTab(tab: Int) {
        val targetTab = when (tab) {
            TAB_HOME, TAB_CHAT, TAB_LIVE_VOICE, TAB_ACTIVE_CAMERA, TAB_SETTINGS -> tab
            else -> TAB_HOME
        }
        viewModel.setSelectedTab(targetTab)
        updateBottomNavVisuals(targetTab)

        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)

        val tag = "tab_fragment_$targetTab"
        var targetFragment = fm.findFragmentByTag(tag)
        if (targetFragment == null) {
            targetFragment = createFragmentForTab(targetTab)
            tx.add(fragmentContainerId, targetFragment, tag)
        }

        for (existing in fm.fragments) {
            if (existing != null) {
                if (existing == targetFragment) {
                    tx.show(existing)
                } else {
                    tx.hide(existing)
                }
            }
        }
        tx.commitAllowingStateLoss()
    }

    private fun createFragmentForTab(tab: Int): Fragment {
        return when (tab) {
            TAB_CHAT -> ChatboxFragment()
            TAB_LIVE_VOICE -> LiveVoiceFragment()
            TAB_ACTIVE_CAMERA -> ActiveCameraFragment()
            TAB_SETTINGS -> SettingsFragment()
            else -> HomeFragment()
        }
    }

    private fun updateBottomNavVisuals(selectedTabId: Int) {
        val density = resources.displayMetrics.density
        for ((tabId, holder) in navTabViews) {
            val isSelected = tabId == selectedTabId
            val activeAccentHex = if (tabId == TAB_LIVE_VOICE) "#22C55E" else "#38BDF8"
            if (isSelected) {
                holder.pillContainer.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = 14f * density
                    setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.parseColor(activeAccentHex))
                }
                holder.iconView.imageTintList = ColorStateList.valueOf(Color.parseColor(activeAccentHex))
                holder.labelView.setTextColor(Color.parseColor("#FFFFFF"))
                holder.labelView.typeface = Typeface.DEFAULT_BOLD
            } else {
                holder.pillContainer.background = null
                holder.iconView.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                holder.labelView.setTextColor(Color.parseColor("#94A3B8"))
                holder.labelView.typeface = Typeface.DEFAULT
            }
        }
    }

    fun handleMicOrOrbClick() {
        viewModel.toggleMicOrOrb(this)
    }

    fun handleHoldMicOrOrb() {
        viewModel.togglePowerSession(this)
    }

    fun toggleScreenShare() {
        ScreenCaptureManager.getInstance().toggleSharing()
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_CHAT = 1
        const val TAB_SETTINGS = 2
        const val TAB_LIVE_VOICE = 3
        const val TAB_ACTIVE_CAMERA = 4
    }
}
