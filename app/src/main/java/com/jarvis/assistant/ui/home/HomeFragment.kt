package com.jarvis.assistant.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.agent.AgentTaskHistoryRepository.StepStatus
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.databinding.FragmentHomeBinding
import com.jarvis.assistant.ui.memory.MemoryActivity
import com.jarvis.assistant.ui.settings.PersonaActivity
import com.jarvis.assistant.util.LocationWeatherHelper
import com.jarvis.assistant.util.PermissionHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel get() = (requireActivity() as MainActivity).viewModel
    private val preferences by lazy { JarvisApp.instance.preferences }

    private var isPageLoaded = false
    private var pendingOrbState: String? = null
    private var pendingThemeKey: String? = null
    private var pendingCustomHex: String? = null
    private var lastAudioPushTime = 0L
    private var taskFallbackPanel: android.widget.LinearLayout? = null
    private var taskFallbackText: android.widget.TextView? = null
    private var lastAutoOpenedPlanId: String? = null
    private var lastShownApprovalId: String? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        updateCountryBadgeDisplay()
        if (fineGranted || coarseGranted) {
            fetchLiveWeather()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.jarvis.assistant.automation.agent.AgentTaskHistoryRepository.init(requireContext())
        createTaskFallbackPanel()
        setupHudTaskDashboardAndApprovalDialog()

        // Top Navigation Buttons (Chat & Settings icons hidden in header per user request)
        binding.btnTopChat.visibility = View.GONE
        binding.btnTopSettings.visibility = View.GONE

        binding.btnTopChat.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            (activity as? MainActivity)?.selectTab(MainActivity.TAB_CHAT)
        }

        binding.btnTopPro.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            startActivity(Intent(requireContext(), PersonaActivity::class.java))
        }

        binding.btnTopProfile.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            openUserProfileOption()
        }

        binding.btnTopSettings.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            (activity as? MainActivity)?.selectTab(MainActivity.TAB_SETTINGS)
        }

        binding.btnOnlineProfileOption.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            openUserProfileOption()
        }

        binding.layoutOnlineCountryBadge.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            if (!PermissionHelper.hasLocationPermission(requireContext())) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                fetchLiveWeather()
                openUserProfileOption()
            }
        }

        binding.layoutOnlineBadge.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackManager.triggerOrbPress(it)
            (activity as? MainActivity)?.handleMicOrOrbClick()
        }

        binding.cardSpeechBubble.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackManager.triggerOrbPress(it)
            (activity as? MainActivity)?.handleMicOrOrbClick()
        }

        binding.layoutStatusIndicator.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackManager.triggerOrbPress(it)
            (activity as? MainActivity)?.handleMicOrOrbClick()
        }

        binding.layoutStatusIndicator.setOnLongClickListener {
            val willPowerOn = !com.jarvis.assistant.background.JarvisConversationService.isSessionOn.value
            com.jarvis.assistant.util.HapticFeedbackManager.triggerPowerToggle(isPowerOn = willPowerOn, view = it)
            (activity as? MainActivity)?.handleHoldMicOrOrb()
            true
        }

        binding.layoutTitle.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            com.jarvis.assistant.ui.settings.VoiceCommandsGuideActivity.showMobileControlPopup(requireContext())
        }
        binding.layoutTitle.setOnLongClickListener {
            startActivity(Intent(requireContext(), com.jarvis.assistant.ui.agent.AgentDashboardActivity::class.java))
            true
        }

        binding.cardExploreBanner.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Master AI Agent & Creation Studio")
                .setItems(
                    arrayOf(
                        "🤖 Master AI Agent & Multi-Task Dashboard (Light Theme)",
                        "📱 Jarvic AI Mobile Control (A to Z Guide)",
                        "📊 Quotex & Market Chart AI (Real Buy/Sell & S&R Scanner)",
                        "🛠️ Voice Builder & Code Editor (Apps & 3D Games)",
                        "💬 AI Chat Assistant"
                    )
                ) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(requireContext(), com.jarvis.assistant.ui.agent.AgentDashboardActivity::class.java))
                        1 -> startActivity(Intent(requireContext(), com.jarvis.assistant.ui.settings.VoiceCommandsGuideActivity::class.java))
                        2 -> startActivity(Intent(requireContext(), com.jarvis.assistant.trading.ui.TradingAnalyzerActivity::class.java))
                        3 -> startActivity(Intent(requireContext(), com.jarvis.assistant.builder.VoiceBuilderActivity::class.java))
                        4 -> (activity as? MainActivity)?.selectTab(MainActivity.TAB_CHAT)
                    }
                }
                .show()
        }

        binding.btnDismissLiveError.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            hideLiveErrorOverlay {
                viewModel.clearLiveError()
            }
        }

        setupOrbInteractionAndWebView()

        // Quick Action Cards
        binding.btnScreenShareQuick.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            (activity as? MainActivity)?.toggleScreenShare()
        }

        binding.btnSavedMemories.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            startActivity(Intent(requireContext(), MemoryActivity::class.java))
        }

        binding.btnConversationLanguageQuick.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            showLanguageDialog()
        }

        binding.btnClockQuick.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            startActivity(Intent(requireContext(), com.jarvis.assistant.alarm.ui.ClockActivity::class.java))
        }

        // Weather Card Click -> Refresh Real-Time Weather & Request Location if needed
        binding.cardWeather.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            binding.tvWeatherCondition.text = "Updating..."
            fetchLiveWeather()
            if (!PermissionHelper.hasLocationPermission(requireContext())) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        updateHomeHeaderAndVoiceStatus()
        observeViewModel()
        checkLocationAndFetchWeather()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupOrbInteractionAndWebView() {
        val orbGestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                com.jarvis.assistant.util.HapticFeedbackManager.triggerSubtleTick(binding.waveformOrbView)
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                com.jarvis.assistant.util.HapticFeedbackManager.triggerOrbPress(binding.waveformOrbView)
                (activity as? MainActivity)?.handleMicOrOrbClick()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val willPowerOn = !com.jarvis.assistant.background.JarvisConversationService.isSessionOn.value
                com.jarvis.assistant.util.HapticFeedbackManager.triggerPowerToggle(
                    isPowerOn = willPowerOn,
                    view = binding.waveformOrbView
                )
                (activity as? MainActivity)?.handleHoldMicOrOrb()
            }
        })

        binding.waveformOrbView.setOnTouchListener { _, event ->
            orbGestureDetector.onTouchEvent(event)
            true
        }

        val currentTheme = pendingThemeKey ?: preferences.appTheme
        val customHex = pendingCustomHex ?: preferences.customThemeColor
        setOrbTheme(currentTheme, customHex)

        val state = pendingOrbState ?: when (viewModel.conversationState.value) {
            ConversationState.LISTENING, ConversationState.THINKING -> "listening"
            ConversationState.SPEAKING -> "speaking"
            ConversationState.IDLE -> "idle"
        }
        setOrbState(state)

        com.jarvis.assistant.background.BatteryMonitorService.start(requireContext())
    }

    fun updateBatteryState(percent: Int, isCharging: Boolean) {
        if (_binding == null) return
        val clamped = percent.coerceIn(0, 100)
        // Native battery state tracking
        isPageLoaded = clamped >= 0 || isCharging
    }

    fun setOrbState(state: String) {
        pendingOrbState = state
        if (_binding != null) {
            binding.waveformOrbView.setState(state)
            binding.ivHeroBackground.setAvatarState(state)
        }
    }

    fun setOrbTheme(themeKey: String, customHex: String?) {
        pendingThemeKey = themeKey
        pendingCustomHex = customHex
    }

    private fun pushExternalAudio(amp: Float, bass: Float, high: Float) {
        _binding?.waveformOrbView?.setExternalAudio(amp, bass, high)
        _binding?.ivHeroBackground?.setExternalAudio(amp, bass, high)
        // Subtle dynamic scale cue on the status glow dot based on active voice volume
        _binding?.dotStatusGlow?.let { dot ->
            val dotScale = 1f + (amp * 0.45f).coerceIn(0f, 0.45f)
            dot.scaleX = dotScale
            dot.scaleY = dotScale
        }
        val now = System.currentTimeMillis()
        if (now - lastAudioPushTime < 22L) return
        lastAudioPushTime = now
    }

    override fun onResume() {
        super.onResume()
        renderTaskFallback(com.jarvis.assistant.automation.agent.AgentTaskEngine.taskState.value)

        setOrbTheme(preferences.appTheme, preferences.customThemeColor)
        updateHomeHeaderAndVoiceStatus()
        fetchLiveWeather()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            binding.root.visibility = View.VISIBLE
            setOrbTheme(preferences.appTheme, preferences.customThemeColor)
            updateHomeHeaderAndVoiceStatus()
            fetchLiveWeather()
        } else if (hidden && _binding != null) {
            binding.root.visibility = View.GONE
        }
    }

    private fun checkLocationAndFetchWeather() {
        updateCountryBadgeDisplay()
        // Immediately fetch real-time weather (uses GPS if permitted, or IP/timezone fallback immediately)
        fetchLiveWeather()
        if (!PermissionHelper.hasLocationPermission(requireContext())) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLiveWeather() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Immediately resolve and display real Country Flag Logo & Country Name from device location
            if (PermissionHelper.hasLocationPermission(requireContext())) {
                val countryIdentity = LocationWeatherHelper.fetchDeviceCountryIdentity(requireContext())
                if (_binding != null && countryIdentity != null) {
                    updateCountryBadgeDisplay()
                }
            }

            // 2. Fetch live weather + reverse-geocoded city/country details
            val weather = LocationWeatherHelper.fetchWeather(requireContext())
            if (_binding != null) {
                if (weather != null) {
                    binding.tvTemperature.text = weather.temperature
                    binding.tvWeatherCondition.text = weather.condition
                    val countrySuffix = if (weather.countryName.isNotBlank()) ", ${weather.countryName}" else ""
                    binding.tvWeatherCity.text = "📍 ${weather.cityName}$countrySuffix"
                    binding.tvWeatherDetails.text = weather.details
                    binding.ivWeatherIcon.setImageResource(weather.iconResId)
                    preferences.userLocation = weather.cityName
                    if (weather.countryName.isNotBlank()) preferences.userCountryName = weather.countryName
                    if (weather.countryCode.isNotBlank()) preferences.userCountryCode = weather.countryCode
                } else if (!PermissionHelper.hasLocationPermission(requireContext())) {
                    binding.tvWeatherCity.text = "📍 Location needed"
                    binding.tvWeatherCondition.text = "Tap to allow"
                    binding.tvWeatherDetails.text = "Tap card to grant location & see live weather"
                } else {
                    val saved = preferences.userLocation.trim()
                    binding.tvWeatherCity.text = if (saved.isNotBlank()) "📍 $saved" else "📍 My Location"
                    binding.tvWeatherCondition.text = "Tap to retry"
                    binding.tvWeatherDetails.text = "Tap to refresh current local weather"
                }
                updateCountryBadgeDisplay()
            }
        }
    }

    private fun updateCountryBadgeDisplay() {
        if (_binding == null) return
        val ctx = context ?: return
        val hasLocPerm = PermissionHelper.hasLocationPermission(ctx)

        if (hasLocPerm) {
            val (normName, normCode) = com.jarvis.assistant.util.CountryFlagHelper.normalizeCountry(
                preferences.userCountryName,
                preferences.userCountryCode
            )
            if (normName.isNotBlank() || normCode.isNotBlank()) {
                binding.ivOnlineCountryFlag.imageTintList = null
                binding.ivOnlineCountryFlag.setImageDrawable(
                    com.jarvis.assistant.util.CountryFlagHelper.createCountryFlagDrawable(
                        ctx,
                        normCode.ifBlank { normName },
                        widthDp = 22,
                        heightDp = 15
                    )
                )
                binding.tvOnlineCountryName.text = normName.ifBlank { normCode }
                binding.tvOnlineCountryName.setTextColor(Color.parseColor("#F8FAFC"))
                binding.layoutOnlineCountryBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge_green)
            } else {
                binding.ivOnlineCountryFlag.setImageResource(R.drawable.ic_settings_language)
                binding.ivOnlineCountryFlag.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                binding.tvOnlineCountryName.text = "Detecting..."
                binding.tvOnlineCountryName.setTextColor(Color.parseColor("#BAE6FD"))
                binding.layoutOnlineCountryBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
            }
        } else {
            binding.ivOnlineCountryFlag.setImageResource(R.drawable.ic_settings_language)
            binding.ivOnlineCountryFlag.imageTintList = ColorStateList.valueOf(Color.parseColor("#FACC15"))
            binding.tvOnlineCountryName.text = "Location"
            binding.tvOnlineCountryName.setTextColor(Color.parseColor("#FACC15"))
            binding.layoutOnlineCountryBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
        }
    }

    private fun openUserProfileOption() {
        val ctx = context ?: return
        com.jarvis.assistant.util.UserProfileDialogHelper.showProfileDialog(
            context = ctx,
            onRequestLocationPermission = {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onRefreshLocation = {
                fetchLiveWeather()
            },
            onOpenFullSettings = {
                (activity as? MainActivity)?.selectTab(MainActivity.TAB_SETTINGS)
            },
            onProfileUpdated = {
                updateHomeHeaderAndVoiceStatus()
            }
        )
    }

    private fun updateHomeHeaderAndVoiceStatus() {
        if (_binding == null) return
        updateCountryBadgeDisplay()

        // Conditionally display ADMIN badge in HUD Header if signed in user email matches authorized admin list
        val currentUserEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
        val isAuthorizedAdmin = com.jarvis.assistant.data.repository.UserAdminRepository.isOwnerEmail(currentUserEmail) || preferences.isAdminModeActive
        binding.tvHeaderAdminBadge.visibility = if (isAuthorizedAdmin) View.VISIBLE else View.GONE

        val personality = preferences.personality
        val rawName = preferences.userName.trim()
        val displayName = if (rawName.isNotBlank()) rawName else "Friend"

        when {
            personality.contains("Girlfriend", ignoreCase = true) || personality.equals("Pari", ignoreCase = true) -> {
                binding.ivHeroBackground.setPersonaMode(maleJarvisPro = false)
                binding.tvBrandName.text = "Pari AI"
                binding.tvBrandSubtitle.text = "Your Caring Companion"
                binding.tvGreeting.text = "Always With You 🌸"
                binding.tvSpeechTitle.text = "Hi $displayName! I'm Pari 🌸"
                binding.tvSpeechSubtitle.text = "Kemon acho? Bolo ajke ki korbo? 💕"
            }
            personality.contains("Boyfriend", ignoreCase = true) || personality.equals("Kabir", ignoreCase = true) -> {
                binding.ivHeroBackground.setPersonaMode(maleJarvisPro = true)
                binding.tvBrandName.text = "Kabir AI"
                binding.tvBrandSubtitle.text = "Your Loyal Companion"
                binding.tvGreeting.text = "Always By Your Side ⚡"
                binding.tvSpeechTitle.text = "Hey $displayName! I'm Kabir ⚡"
                binding.tvSpeechSubtitle.text = "What's on your mind bro? I'm here! 👊"
            }
            personality.contains("TechGPT", ignoreCase = true) -> {
                binding.ivHeroBackground.setPersonaMode(maleJarvisPro = true)
                binding.tvBrandName.text = "TechGPT X"
                binding.tvBrandSubtitle.text = "Advanced Technical Intelligence"
                binding.tvGreeting.text = "Ready to Engineer 💡"
                binding.tvSpeechTitle.text = "TechGPT X Active 💡"
                binding.tvSpeechSubtitle.text = "Architecture, Code & Logic ready."
            }
            else -> {
                binding.ivHeroBackground.setPersonaMode(maleJarvisPro = true)
                binding.tvBrandName.text = "JARVIC AI"
                binding.tvBrandSubtitle.text = "Your Smart AI Assistant"
                binding.tvGreeting.text = "Always With You 💙"
                binding.tvSpeechTitle.text = "Hi $displayName! I'm Jarvic AI 👑"
                binding.tvSpeechSubtitle.text = "How can I help you today?"
            }
        }

        val currentLang = preferences.language.ifBlank { "Auto" }
        binding.tvQuickLanguageStatus.text = if (currentLang.contains("Auto", ignoreCase = true)) "Auto" else currentLang

        val isCompanion = com.jarvis.assistant.util.CompanionEmotionEngine.isCompanionPersona(preferences.personality)
        if (isCompanion) {
            applyCompanionEmotionToOrb(com.jarvis.assistant.util.CompanionEmotionEngine.currentEmotion.value)
        } else {
            binding.waveformOrbView.setCompanionEmotion(null)
        }
    }

    private fun applyCompanionEmotionToOrb(emotion: com.jarvis.assistant.util.CompanionEmotionEngine.CompanionEmotion) {
        if (_binding == null) return
        val isCompanion = com.jarvis.assistant.util.CompanionEmotionEngine.isCompanionPersona(preferences.personality)
        if (!isCompanion) {
            binding.waveformOrbView.setCompanionEmotion(null)
            return
        }
        binding.waveformOrbView.setCompanionEmotion(emotion)
    }

    private fun showLanguageDialog() {
        val languages = com.jarvis.assistant.data.model.GeminiConstants.SUPPORTED_LANGUAGES.toTypedArray()
        val currentLang = preferences.language
        val currentIndex = languages.indexOfFirst { it.equals(currentLang, ignoreCase = true) }.let { if (it >= 0) it else 0 }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Conversation Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val chosen = languages[which]
                preferences.language = chosen
                binding.tvQuickLanguageStatus.text = if (chosen.contains("Auto", ignoreCase = true)) "Auto" else chosen
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        kotlinx.coroutines.delay(180_000L)
                        fetchLiveWeather()
                    }
                }
                launch {
                    com.jarvis.assistant.background.BatteryMonitorService.batteryState.collect { battery ->
                        updateBatteryState(battery.percent, battery.isCharging)
                    }
                }
                launch {
                    val alarmRepo = com.jarvis.assistant.alarm.data.AlarmRepository.getInstance(requireContext())
                    alarmRepo.allAlarms.collect { alarms ->
                        val nextAlarm = alarms.filter { it.enabled && it.nextTriggerEpochMillis > 0L }
                            .minByOrNull { it.nextTriggerEpochMillis }
                        if (nextAlarm != null) {
                            val (timeStr, amPm) = com.jarvis.assistant.alarm.scheduler.AlarmTriggerCalculator
                                .formatTime12Or24(nextAlarm.hour, nextAlarm.minute, is24Hour = false)
                            binding.tvQuickClockStatus.text = "$timeStr $amPm".trim()
                            binding.tvQuickClockStatus.setTextColor(Color.parseColor("#0EA5E9"))
                        } else {
                            binding.tvQuickClockStatus.text = "Alarms"
                            binding.tvQuickClockStatus.setTextColor(Color.parseColor("#64748B"))
                        }
                    }
                }
                launch {
                    val alarmRepo = com.jarvis.assistant.alarm.data.AlarmRepository.getInstance(requireContext())
                    alarmRepo.lastVoiceAction.collect { notice ->
                        if (notice != null && _binding != null) {
                            val snack = com.google.android.material.snackbar.Snackbar.make(
                                binding.root,
                                "⏰ ${notice.summaryText}",
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            )
                            if (notice.actionType == "DELETED") {
                                snack.setAction("UNDO") {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        alarmRepo.undoLastDelete()
                                        alarmRepo.clearVoiceActionNotice()
                                    }
                                }
                            } else {
                                snack.setAction("EDIT") {
                                    alarmRepo.clearVoiceActionNotice()
                                    startActivity(Intent(requireContext(), com.jarvis.assistant.alarm.ui.ClockActivity::class.java))
                                }
                            }
                            snack.show()
                            alarmRepo.clearVoiceActionNotice()
                        }
                    }
                }
                launch {
                    com.jarvis.assistant.util.CompanionEmotionEngine.currentEmotion.collect { emotion ->
                        applyCompanionEmotionToOrb(emotion)
                    }
                }
                launch {
                    com.jarvis.assistant.automation.agent.AgentTaskEngine.taskState.collect { snapshot ->
                        renderTaskFallback(snapshot)
                    }
                }
                launch {
                    com.jarvis.assistant.automation.agent.MasterAutonomousAgent.currentPlan.collect { plan ->
                        renderHudTaskExecutionDashboard(plan)
                    }
                }
                launch {
                    combine(
                        com.jarvis.assistant.automation.agent.MasterAutonomousAgent.pendingApprovalDialog,
                        com.jarvis.assistant.social.SocialAutomationEngine.pendingApproval
                    ) { hudDialog, socialPending ->
                        hudDialog to socialPending
                    }.collect { (hudDialog, socialPending) ->
                        renderHudApprovalDialog(hudDialog, socialPending)
                    }
                }
                launch {
                    val screenCapture = com.jarvis.assistant.vision.ScreenCaptureManager.getInstance()
                    val visionModule = com.jarvis.assistant.vision.VisionAnalysisModule.getInstance(requireContext())
                    combine(
                        screenCapture.isSharing,
                        screenCapture.hasReceivedFrame,
                        visionModule.latestScene
                    ) { sharing, hasFrame, scene ->
                        Triple(sharing, hasFrame, scene)
                    }.collect { (sharing, hasFrame, scene) ->
                        binding.tvQuickScreenShareStatus.text = when {
                            !sharing -> "Share screen"
                            !hasFrame -> "Starting…"
                            scene != null -> "Live: ${scene.sceneCategory.name.take(10)}"
                            else -> "Stop sharing"
                        }
                        binding.tvQuickScreenShareStatus.setTextColor(
                            if (sharing && hasFrame) Color.parseColor("#10B981")
                            else Color.parseColor("#64748B")
                        )
                        if (sharing && scene != null && !viewModel.isSessionOn.value) {
                            binding.tvListeningStatus.maxLines = 4
                            binding.tvListeningStatus.text = "👁️ Scene: ${scene.summary}"
                            binding.tvListeningStatus.setTextColor(Color.parseColor("#38BDF8"))
                        }
                    }
                }

                launch {
                    combine(
                        viewModel.isSessionOn,
                        viewModel.isMicMuted,
                        viewModel.conversationState,
                        com.jarvis.assistant.background.JarvisConversationService.isStandby
                    ) { isOn, isMuted, state, isStandby ->
                        HomeStatusState(isOn, isMuted, state, isStandby)
                    }.collect { status ->
                        if (!status.isOn) {
                            setOrbState("idle")
                            binding.tvOnlineTitle.text = "Online"
                            binding.tvListeningStatus.text = "Ready to help"
                            binding.tvListeningStatus.setTextColor(Color.parseColor("#E2E8F0"))
                            binding.tvHoldHint.text = "Tap or Hold to Talk"
                            binding.tvHoldHint.visibility = View.VISIBLE
                            binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                Color.parseColor("#22C55E")
                            )
                        } else if (status.isMuted) {
                            setOrbState("muted")
                            binding.tvOnlineTitle.text = "Mic Muted"
                            binding.tvListeningStatus.text = "Tap to unmute"
                            binding.tvListeningStatus.setTextColor(Color.parseColor("#FCA5A5"))
                            binding.tvHoldHint.text = "Tap or Hold to Talk"
                            binding.tvHoldHint.visibility = View.VISIBLE
                            binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                Color.parseColor("#EF4444")
                            )
                        } else if (status.isStandby) {
                            setOrbState("idle")
                            binding.tvOnlineTitle.text = "Standby"
                            binding.tvListeningStatus.text = "Say 'Hey Jarvis'"
                            binding.tvListeningStatus.setTextColor(Color.parseColor("#BAE6FD"))
                            binding.tvHoldHint.text = "Tap or Hold to Talk"
                            binding.tvHoldHint.visibility = View.VISIBLE
                            binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                Color.parseColor("#38BDF8")
                            )
                        } else {
                            binding.tvHoldHint.visibility = View.VISIBLE
                            val currentUserText = viewModel.liveUserTranscript.value.trim()
                            val currentJarvisText = viewModel.liveAssistantResponse.value.trim()
                            when (status.state) {
                                ConversationState.LISTENING, ConversationState.THINKING -> {
                                    setOrbState("listening")
                                    binding.tvOnlineTitle.text = "Listening"
                                    binding.tvListeningStatus.maxLines = 4
                                    binding.tvHoldHint.maxLines = 4
                                    if (currentUserText.isNotEmpty()) {
                                        binding.tvListeningStatus.text = "🎙️ You: $currentUserText"
                                    } else {
                                        binding.tvListeningStatus.text = "Speak now..."
                                    }
                                    binding.tvListeningStatus.setTextColor(Color.parseColor("#A7F3D0"))
                                    binding.tvHoldHint.text = if (currentJarvisText.isNotEmpty()) {
                                        "🤖 Jarvis: $currentJarvisText"
                                    } else {
                                        "Listening • Tap to Mute"
                                    }
                                    binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                        Color.parseColor("#22C55E")
                                    )
                                }
                                ConversationState.SPEAKING -> {
                                    setOrbState("speaking")
                                    binding.tvOnlineTitle.text = "Speaking"
                                    binding.tvListeningStatus.maxLines = 4
                                    binding.tvHoldHint.maxLines = 4
                                    if (currentJarvisText.isNotEmpty()) {
                                        binding.tvListeningStatus.text = "🤖 Jarvis: $currentJarvisText"
                                    } else {
                                        binding.tvListeningStatus.text = "Tap to interrupt"
                                    }
                                    binding.tvListeningStatus.setTextColor(Color.parseColor("#A5F3FC"))
                                    binding.tvHoldHint.text = if (currentUserText.isNotEmpty()) {
                                        "🎙️ You: $currentUserText"
                                    } else {
                                        "Speaking • Tap to Interrupt"
                                    }
                                    binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                        Color.parseColor("#06B6D4")
                                    )
                                }
                                ConversationState.IDLE -> {
                                    setOrbState("idle")
                                    binding.tvOnlineTitle.text = "Online"
                                    binding.tvListeningStatus.maxLines = 4
                                    binding.tvHoldHint.maxLines = 4
                                    if (currentJarvisText.isNotEmpty() || currentUserText.isNotEmpty()) {
                                        binding.tvListeningStatus.text = if (currentJarvisText.isNotEmpty()) {
                                            "🤖 Jarvis: $currentJarvisText"
                                        } else {
                                            "🎙️ You: $currentUserText"
                                        }
                                        binding.tvHoldHint.text = if (currentUserText.isNotEmpty() && currentJarvisText.isNotEmpty()) {
                                            "🎙️ You: $currentUserText"
                                        } else {
                                            "Tap or Hold to Talk"
                                        }
                                    } else {
                                        binding.tvListeningStatus.text = "Ready to help"
                                        binding.tvHoldHint.text = "Tap or Hold to Talk"
                                    }
                                    binding.tvListeningStatus.setTextColor(Color.parseColor("#E2E8F0"))
                                    binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                        Color.parseColor("#22C55E")
                                    )
                                }
                            }
                        }
                    }
                }

                // Live real-time streaming text of everything the user says and everything Jarvis says on the app screen
                launch {
                    combine(
                        viewModel.liveUserTranscript,
                        viewModel.liveAssistantResponse
                    ) { userTxt, jarvisTxt ->
                        Pair(userTxt.trim(), jarvisTxt.trim())
                    }.collect { (userTxt, jarvisTxt) ->
                        if (userTxt.isEmpty() && jarvisTxt.isEmpty()) return@collect
                        binding.tvListeningStatus.maxLines = 4
                        binding.tvHoldHint.maxLines = 4
                        binding.tvHoldHint.visibility = View.VISIBLE
                        val state = viewModel.conversationState.value
                        if (state == ConversationState.SPEAKING && jarvisTxt.isNotEmpty()) {
                            binding.tvListeningStatus.text = "🤖 Jarvis: $jarvisTxt"
                            binding.tvListeningStatus.setTextColor(Color.parseColor("#A5F3FC"))
                            if (userTxt.isNotEmpty()) {
                                binding.tvHoldHint.text = "🎙️ You: $userTxt"
                            }
                        } else if (userTxt.isNotEmpty()) {
                            binding.tvListeningStatus.text = "🎙️ You: $userTxt"
                            binding.tvListeningStatus.setTextColor(Color.parseColor("#A7F3D0"))
                            if (jarvisTxt.isNotEmpty()) {
                                binding.tvHoldHint.text = "🤖 Jarvis: $jarvisTxt"
                            }
                        } else if (jarvisTxt.isNotEmpty()) {
                            binding.tvListeningStatus.text = "🤖 Jarvis: $jarvisTxt"
                            binding.tvListeningStatus.setTextColor(Color.parseColor("#A5F3FC"))
                        }
                    }
                }

                launch {
                    viewModel.audioFrequency.collect { freq ->
                        val amp = freq.amplitude
                        val bass = freq.bass
                        val high = freq.high
                        pushExternalAudio(amp, bass, high)
                    }
                }

                launch {
                    viewModel.liveError.collect { error ->
                        if (error == null) {
                            hideLiveErrorOverlay()
                        } else {
                            showLiveErrorOverlay()
                            binding.tvLiveErrorTitle.text = error.title
                            binding.tvLiveErrorMessage.text = error.message
                            binding.btnFixLiveError.text = error.actionText

                            when (error.errorCode) {
                                "API_KEY_INVALID", "API_KEY_MISSING" -> {
                                    binding.ivLiveErrorIcon.setImageResource(R.drawable.ic_warning)
                                    binding.ivLiveErrorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                                    binding.tvLiveErrorTitle.setTextColor(Color.parseColor("#EF4444"))
                                    binding.btnFixLiveError.setOnClickListener {
                                        startActivity(Intent(requireContext(), com.jarvis.assistant.ui.settings.ApiConnectionActivity::class.java))
                                    }
                                }
                                "ACCESSIBILITY_DISABLED" -> {
                                    binding.ivLiveErrorIcon.setImageResource(R.drawable.ic_settings_shield)
                                    binding.ivLiveErrorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
                                    binding.tvLiveErrorTitle.setTextColor(Color.parseColor("#F59E0B"))
                                    binding.btnFixLiveError.setOnClickListener {
                                        PermissionHelper.openAccessibilitySettings(requireContext())
                                    }
                                }
                                "PERMISSIONS_MISSING" -> {
                                    binding.ivLiveErrorIcon.setImageResource(R.drawable.ic_settings_shield)
                                    binding.ivLiveErrorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
                                    binding.tvLiveErrorTitle.setTextColor(Color.parseColor("#F59E0B"))
                                    binding.btnFixLiveError.setOnClickListener {
                                        startActivity(Intent(requireContext(), com.jarvis.assistant.ui.permission.PermissionActivity::class.java))
                                    }
                                }
                                else -> {
                                    binding.ivLiveErrorIcon.setImageResource(R.drawable.ic_warning)
                                    binding.ivLiveErrorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                                    binding.tvLiveErrorTitle.setTextColor(Color.parseColor("#EF4444"))
                                    binding.btnFixLiveError.setOnClickListener {
                                        (activity as? MainActivity)?.handleMicOrOrbClick()
                                    }
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.connectionStatus.collect { status ->
                        if (status.startsWith("ERROR")) {
                            binding.tvListeningStatus.text = status
                            binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                Color.parseColor("#EF4444")
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showLiveErrorOverlay() {
        if (_binding == null) return
        val card = binding.cardLiveError
        if (card.visibility == View.VISIBLE && card.alpha == 1f) return
        card.alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.visibility = View.VISIBLE
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.15f))
            .start()
    }

    private fun hideLiveErrorOverlay(onEnd: (() -> Unit)? = null) {
        if (_binding == null) {
            onEnd?.invoke()
            return
        }
        val card = binding.cardLiveError
        if (card.visibility != View.VISIBLE) {
            onEnd?.invoke()
            return
        }
        card.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(180)
            .withEndAction {
                if (_binding != null) {
                    card.visibility = View.GONE
                }
                onEnd?.invoke()
            }
            .start()
    }

    private fun createTaskFallbackPanel() {
        val root = binding.root as? androidx.constraintlayout.widget.ConstraintLayout ?: return
        val panel = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(12, 20, 35))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), android.graphics.Color.rgb(34, 211, 238))
            }
            elevation = dp(8).toFloat()
            visibility = View.GONE
        }
        panel.addView(android.widget.TextView(requireContext()).apply {
            text = "JARVIS AGENT PROGRESS"
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.rgb(103, 232, 249))
        })
        val details = android.widget.TextView(requireContext()).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            maxLines = 4
            setPadding(0, dp(5), 0, dp(2))
        }
        panel.addView(details)
        panel.addView(android.widget.Button(requireContext()).apply {
            text = "Enable floating overlay"
            isAllCaps = false
            setOnClickListener {
                try {
                    startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${requireContext().packageName}")
                    ))
                } catch (_: Exception) {
                    android.widget.Toast.makeText(requireContext(), "Open Android Settings → Apps → JARVIS → Display over other apps.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }, android.widget.LinearLayout.LayoutParams(-2, dp(44)))
        val params = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, -2).apply {
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = dp(70)
            marginStart = dp(16)
            marginEnd = dp(16)
        }
        root.addView(panel, params)
        taskFallbackPanel = panel
        taskFallbackText = details
    }

    private fun renderTaskFallback(snapshot: com.jarvis.assistant.automation.agent.AgentTaskSnapshot?) {
        val panel = taskFallbackPanel ?: return
        val show = snapshot != null && !android.provider.Settings.canDrawOverlays(requireContext())
        panel.visibility = if (show) View.VISIBLE else View.GONE
        if (!show || snapshot == null) return
        val activeStep = snapshot.steps.firstOrNull { it.stepId == snapshot.currentStepId }?.title ?: snapshot.currentStatus
        val checklist = snapshot.steps.take(4).joinToString("\n") { step ->
            val mark = if (step.status == com.jarvis.assistant.automation.agent.TaskStepStatus.COMPLETED) "✓" else "○"
            "$mark ${step.title}"
        }
        taskFallbackText?.text = "${snapshot.originalGoal}\n${snapshot.progressPercentage}% · ${snapshot.currentStatus.replace('_', ' ')}\nCurrent: $activeStep\n$checklist"
    }

    private fun setupHudTaskDashboardAndApprovalDialog() {
        binding.btnHudAgentQueueToggle.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            if (binding.cardHudTaskDashboard.visibility == View.VISIBLE) {
                hideHudTaskDashboard()
            } else {
                showHudTaskDashboard()
                renderHudTaskExecutionDashboard(com.jarvis.assistant.automation.agent.MasterAutonomousAgent.currentPlan.value)
            }
        }

        binding.btnCloseHudTaskDashboard.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            hideHudTaskDashboard()
        }

        binding.btnHudLoadDemoQueue.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            showHudMultiTaskQueuePickerDialog()
        }

        binding.btnHudResumeQueue.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            com.jarvis.assistant.automation.agent.MasterAutonomousAgent.resumeFromLastCompletedTask(requireContext()) { spoken ->
                com.jarvis.assistant.background.JarvisConversationService.speakText(spoken)
            }
        }

        binding.btnHudStopQueue.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            com.jarvis.assistant.automation.agent.MasterAutonomousAgent.cancelActivePlan(requireContext()) { spoken ->
                com.jarvis.assistant.background.JarvisConversationService.speakText(spoken)
            }
        }

        binding.btnHudOpenAgentFull.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            startActivity(Intent(requireContext(), com.jarvis.assistant.ui.agent.AgentDashboardActivity::class.java))
        }

        binding.btnHudApproveConfirm.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performCorePulse(it)
            hideHudApprovalDialog {
                com.jarvis.assistant.automation.agent.MasterAutonomousAgent.approveWaitingStepAndContinue(requireContext()) { spoken ->
                    com.jarvis.assistant.background.JarvisConversationService.speakText(spoken)
                }
            }
        }

        binding.btnHudRejectCancel.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            hideHudApprovalDialog {
                com.jarvis.assistant.automation.agent.MasterAutonomousAgent.rejectWaitingStep(requireContext()) { spoken ->
                    com.jarvis.assistant.background.JarvisConversationService.speakText(spoken)
                }
            }
        }

        binding.btnDismissHudApproval.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            hideHudApprovalDialog()
        }

        // Initial render of current queue state
        renderHudTaskExecutionDashboard(com.jarvis.assistant.automation.agent.MasterAutonomousAgent.currentPlan.value)
    }

    private fun showHudMultiTaskQueuePickerDialog() {
        val ctx = context ?: return
        val presets = arrayOf(
            "📋 5-Step Social + WhatsApp Queue (Facebook Post + Instagram + WhatsApp Attachment)",
            "💬 High-Risk Action: Publish Comment on Facebook (Requires Approval)",
            "🗑️ High-Risk Action: Delete Selected WhatsApp Message (Requires Approval)",
            "📷 High-Risk Action: Send Photo Attachment on WhatsApp (Requires Approval)",
            "✍️ Enter Custom Multi-Task Voice / Text Plan..."
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("HUD Multi-Task Queue & Approval Runner")
            .setItems(presets) { _, which ->
                when (which) {
                    0 -> {
                        val cmd = "Jarvic, open Facebook, read its caption, write a relevant comment, then open Instagram and check my latest post, after that open WhatsApp and send a photo to Rahul."
                        showHudTaskDashboard()
                        com.jarvis.assistant.automation.agent.MasterAutonomousAgent.executeVoiceCommandPlan(ctx, cmd) { spoken ->
                            com.jarvis.assistant.background.JarvisConversationService.speakText(spoken)
                        }
                    }
                    1 -> {
                        val cmd = "Jarvic, open Facebook, read this post, and write a meaningful comment."
                        showHudTaskDashboard()
                        com.jarvis.assistant.automation.agent.MasterAutonomousAgent.executeVoiceCommandPlan(ctx, cmd) { spoken ->
                            com.jarvis.assistant.background.JarvisConversationService.speakText(spoken)
                        }
                    }
                    2 -> {
                        val cmd = "Jarvic, open WhatsApp and delete my selected message."
                        showHudTaskDashboard()
                        com.jarvis.assistant.automation.agent.MasterAutonomousAgent.executeVoiceCommandPlan(ctx, cmd) { spoken ->
                            com.jarvis.assistant.background.JarvisConversationService.speakText(spoken)
                        }
                    }
                    3 -> {
                        val cmd = "Jarvic, open WhatsApp and send a beautiful nature photo to Rahul."
                        showHudTaskDashboard()
                        com.jarvis.assistant.automation.agent.MasterAutonomousAgent.executeVoiceCommandPlan(ctx, cmd) { spoken ->
                            com.jarvis.assistant.background.JarvisConversationService.speakText(spoken)
                        }
                    }
                    4 -> {
                        val input = android.widget.EditText(ctx).apply {
                            hint = "e.g., Open Facebook, read post, write comment, then open WhatsApp and send photo to Rahul"
                            minLines = 3
                            setPadding(dp(16), dp(12), dp(16), dp(12))
                        }
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                            .setTitle("Custom Multi-Task Queue")
                            .setView(input)
                            .setPositiveButton("Execute Queue") { _, _ ->
                                val text = input.text?.toString()?.trim().orEmpty()
                                if (text.isNotEmpty()) {
                                    showHudTaskDashboard()
                                    com.jarvis.assistant.automation.agent.MasterAutonomousAgent.executeVoiceCommandPlan(ctx, text) { spoken ->
                                        com.jarvis.assistant.background.JarvisConversationService.speakText(spoken)
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun renderHudTaskExecutionDashboard(
        plan: com.jarvis.assistant.automation.agent.AgentTaskHistoryRepository.MultiTaskPlanRecord?
    ) {
        if (_binding == null) return
        val ctx = context ?: return

        if (plan == null || plan.steps.isEmpty()) {
            binding.tvHudAgentQueueBadge.text = "Queue"
            binding.dotHudQueueIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            binding.tvHudTaskQueueSubtitle.text = "Multi-Task Execution Queue • Ready"
            binding.tvHudBadgeCompleted.text = "✓ 0 Done"
            binding.tvHudBadgeRunning.text = "● 0 Running"
            binding.tvHudBadgeWaiting.text = "⚠ 0 Approval"
            binding.tvHudBadgePending.text = "○ 0 Pending"
            binding.progressHudTaskQueue.progress = 0
            binding.containerHudTaskSteps.removeAllViews()

            val emptyHint = android.widget.TextView(ctx).apply {
                text = "No active multi-task queue. Speak a multi-step command to Jarvic or tap '▶ Queue 5' below to visualize live task execution."
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(dp(4), dp(6), dp(4), dp(6))
            }
            binding.containerHudTaskSteps.addView(emptyHint)
            return
        }

        val total = plan.steps.size
        val completedCount = plan.steps.count { it.status == StepStatus.COMPLETED }
        val runningCount = plan.steps.count { it.status == StepStatus.RUNNING }
        val waitingCount = plan.steps.count { it.status == StepStatus.WAITING_FOR_APPROVAL }
        val pendingCount = plan.steps.count { it.status == StepStatus.PENDING }
        val progressPct = if (total > 0) (completedCount * 100) / total else 0

        // Auto-open HUD Task Execution Dashboard when a new plan starts running or awaits approval
        if (plan.planId != lastAutoOpenedPlanId &&
            (plan.overallStatus == "RUNNING" || plan.overallStatus == "PAUSED_FOR_APPROVAL")
        ) {
            lastAutoOpenedPlanId = plan.planId
            showHudTaskDashboard()
        }

        // Top HUD Header Queue Pill
        binding.tvHudAgentQueueBadge.text = "Tasks $completedCount/$total"
        val headerDotHex = when {
            waitingCount > 0 -> "#F59E0B"
            runningCount > 0 -> "#38BDF8"
            completedCount == total -> "#22C55E"
            else -> "#94A3B8"
        }
        binding.dotHudQueueIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor(headerDotHex))
        binding.dotHudDashboardActive.backgroundTintList = ColorStateList.valueOf(Color.parseColor(headerDotHex))

        val activeStep = plan.steps.firstOrNull {
            it.status == StepStatus.RUNNING || it.status == StepStatus.WAITING_FOR_APPROVAL
        }
        binding.tvHudTaskQueueSubtitle.text = if (activeStep != null) {
            "Task ${activeStep.stepIndex}/$total – ${activeStep.status.label} (${activeStep.targetApp})"
        } else {
            "Queue Status: ${plan.overallStatus} • $completedCount/$total Completed ($progressPct%)"
        }

        // Update 4 Status Counter Badges
        binding.tvHudBadgeCompleted.text = "✓ $completedCount Done"
        binding.tvHudBadgeRunning.text = "● $runningCount Running"
        binding.tvHudBadgeWaiting.text = "⚠ $waitingCount Approval"
        binding.tvHudBadgePending.text = "○ $pendingCount Pending"
        binding.progressHudTaskQueue.progress = progressPct

        // Populate individual task rows in the HUD queue
        binding.containerHudTaskSteps.removeAllViews()
        for (step in plan.steps) {
            val (bgHex, borderHex, badgeHex, statusSymbol) = when (step.status) {
                StepStatus.COMPLETED -> Quadruple("#F0FDF4", "#86EFAC", "#15803D", "✓")
                StepStatus.RUNNING -> Quadruple("#EFF6FF", "#93C5FD", "#1D4ED8", "●")
                StepStatus.WAITING_FOR_APPROVAL -> Quadruple("#FFFBEB", "#FCD34D", "#B45309", "⚠")
                StepStatus.FAILED -> Quadruple("#FEF2F2", "#FCA5A5", "#B91C1C", "✕")
                StepStatus.SKIPPED -> Quadruple("#F1F5F9", "#CBD5E1", "#64748B", "–")
                StepStatus.PENDING -> Quadruple("#FFFFFF", "#E2E8F0", "#475569", "○")
            }

            val rowLayout = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor(borderHex))
                }
                val lp = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(6)
                layoutParams = lp
                if (step.status == StepStatus.WAITING_FOR_APPROVAL) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val approval = com.jarvis.assistant.automation.agent.MasterAutonomousAgent.pendingApprovalDialog.value
                            ?: com.jarvis.assistant.automation.agent.MasterAutonomousAgent.buildHudApprovalDialogState(
                                step,
                                step.approvalPrompt ?: step.formattedDashboardLine()
                            )
                        renderHudApprovalDialog(approval, com.jarvis.assistant.social.SocialAutomationEngine.pendingApproval.value, forceShow = true)
                    }
                }
            }

            val topRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val stepTitleTv = android.widget.TextView(ctx).apply {
                text = "$statusSymbol Task ${step.stepIndex}/${step.totalSteps}: ${step.title}"
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val statusBadgeTv = android.widget.TextView(ctx).apply {
                text = step.status.label
                textSize = 10.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(badgeHex))
                setPadding(dp(6), dp(2), dp(6), dp(2))
            }

            topRow.addView(stepTitleTv)
            topRow.addView(statusBadgeTv)
            rowLayout.addView(topRow)

            if (step.verificationNote.isNotBlank()) {
                val subTv = android.widget.TextView(ctx).apply {
                    text = step.verificationNote
                    textSize = 11f
                    setTextColor(Color.parseColor("#475569"))
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(2), 0, 0)
                }
                rowLayout.addView(subTv)
            }
            binding.containerHudTaskSteps.addView(rowLayout)
        }
    }

    private fun renderHudApprovalDialog(
        hudDialog: com.jarvis.assistant.automation.agent.MasterAutonomousAgent.HudApprovalDialogState?,
        socialPending: com.jarvis.assistant.social.SocialAutomationEngine.PendingSocialApproval?,
        forceShow: Boolean = false
    ) {
        if (_binding == null) return

        val effectiveApproval = hudDialog ?: if (socialPending != null) {
            com.jarvis.assistant.automation.agent.MasterAutonomousAgent.HudApprovalDialogState(
                approvalId = socialPending.id,
                stepIndex = 1,
                totalSteps = 1,
                riskType = if (socialPending.actionType == com.jarvis.assistant.social.SocialAutomationEngine.PendingSocialActionType.DELETE_OWN_POST) {
                    com.jarvis.assistant.automation.agent.MasterAutonomousAgent.HighRiskActionType.MESSAGE_OR_POST_DELETION
                } else {
                    com.jarvis.assistant.automation.agent.MasterAutonomousAgent.HighRiskActionType.SOCIAL_MEDIA_POST
                },
                targetApp = socialPending.platform.displayName,
                actionTitle = "${socialPending.actionType.name.replace('_', ' ')} on ${socialPending.platform.displayName}",
                description = socialPending.summaryForUser,
                contentPreview = "Platform: ${socialPending.platform.displayName}\nContent: \"${socialPending.proposedTextOrCaption}\""
            )
        } else {
            null
        }

        if (effectiveApproval == null) {
            hideHudApprovalDialog()
            return
        }

        val badgeColor = Color.parseColor(effectiveApproval.riskType.badgeColorHex)
        binding.cardHudApprovalDialog.strokeColor = badgeColor
        binding.ivHudApprovalIcon.imageTintList = ColorStateList.valueOf(badgeColor)
        binding.tvHudApprovalRiskCategory.text = effectiveApproval.riskType.badgeLabel
        binding.tvHudApprovalRiskCategory.setTextColor(badgeColor)
        binding.tvHudApprovalStepBadge.text = "Task ${effectiveApproval.stepIndex}/${effectiveApproval.totalSteps} • Waiting for approval (${effectiveApproval.targetApp})"
        binding.tvHudApprovalTitle.text = effectiveApproval.actionTitle
        binding.tvHudApprovalDescription.text = effectiveApproval.description
        binding.tvHudApprovalPreview.text = effectiveApproval.contentPreview
        binding.tvHudApprovalVoiceHint.text = "🎙️ ${effectiveApproval.voiceHint}"

        if (forceShow || effectiveApproval.approvalId != lastShownApprovalId || binding.cardHudApprovalDialog.visibility != View.VISIBLE) {
            lastShownApprovalId = effectiveApproval.approvalId
            showHudApprovalDialog()
        }
    }

    private fun showHudTaskDashboard() {
        if (_binding == null) return
        val card = binding.cardHudTaskDashboard
        if (card.visibility == View.VISIBLE && card.alpha == 1f) return
        card.alpha = 0f
        card.translationY = -dp(16).toFloat()
        card.visibility = View.VISIBLE
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun hideHudTaskDashboard() {
        if (_binding == null) return
        val card = binding.cardHudTaskDashboard
        if (card.visibility != View.VISIBLE) return
        card.animate()
            .alpha(0f)
            .translationY(-dp(16).toFloat())
            .setDuration(180)
            .withEndAction {
                if (_binding != null) {
                    card.visibility = View.GONE
                }
            }
            .start()
    }

    private fun showHudApprovalDialog() {
        if (_binding == null) return
        val card = binding.cardHudApprovalDialog
        if (card.visibility == View.VISIBLE && card.alpha == 1f) return
        card.alpha = 0f
        card.scaleX = 0.88f
        card.scaleY = 0.88f
        card.visibility = View.VISIBLE
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.12f))
            .start()
    }

    private fun hideHudApprovalDialog(onEnd: (() -> Unit)? = null) {
        if (_binding == null) {
            onEnd?.invoke()
            return
        }
        val card = binding.cardHudApprovalDialog
        if (card.visibility != View.VISIBLE) {
            onEnd?.invoke()
            return
        }
        card.animate()
            .alpha(0f)
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(180)
            .withEndAction {
                if (_binding != null) {
                    card.visibility = View.GONE
                }
                onEnd?.invoke()
            }
            .start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class HomeStatusState(
    val isOn: Boolean,
    val isMuted: Boolean,
    val state: ConversationState,
    val isStandby: Boolean
)
