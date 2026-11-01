package com.jarvis.assistant.ui.home

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.ui.chat.ChatFragment
import com.jarvis.assistant.ui.settings.SettingsFragment
import com.jarvis.assistant.util.PermissionHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAB_HOME = 0
        const val TAB_CHAT = 1
        const val TAB_SETTINGS = 2
        const val TAB_CAMERA = 3
        const val TAB_UPDATES = 3
    }

    private lateinit var binding: ActivityMainBinding
    val viewModel: MainViewModel by viewModels()
    private var activeSnackbar: Snackbar? = null

    private var currentTab = TAB_HOME
    private val homeFragment by lazy { HomeFragment() }
    private val chatFragment by lazy { ChatFragment() }
    private val settingsFragment by lazy { SettingsFragment() }
    private val cameraFragment by lazy { com.jarvis.assistant.camera.CameraAnalysisFragment() }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkPermissionsAndStart()
        } else {
            Toast.makeText(
                this,
                "Microphone permission is required for voice conversation.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkPermissionsAndStart()
        } else {
            Toast.makeText(
                this,
                "Notification permission is required to run in the background.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
    }

    val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                com.jarvis.assistant.vision.ScreenCaptureService.start(
                    this,
                    result.resultCode,
                    result.data!!
                )
                Toast.makeText(this, "Starting MediaProjection & real-time scene recognition…", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    delay(2200L)
                    val captureManager = com.jarvis.assistant.vision.ScreenCaptureManager.getInstance()
                    if (!captureManager.isSharing.value) return@launch
                    if (captureManager.hasReceivedFrame.value) {
                        Toast.makeText(this@MainActivity, "Screen capture & real-time scene recognition active.", Toast.LENGTH_LONG).show()
                    } else {
                        com.jarvis.assistant.vision.ScreenCaptureService.stop(this@MainActivity)
                        JarvisConversationService.stopScreenCapture()
                        Toast.makeText(this@MainActivity, "No screen frames arrived, so sharing was stopped. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error starting MediaProjection: ${e.message}", e)
                com.jarvis.assistant.vision.ScreenCaptureService.stop(this)
                JarvisConversationService.demoteFromMediaProjection()
                Toast.makeText(this, "Screen capture failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private var appliedThemeKey: String = ""
    private var appliedCustomHex: String = ""

    fun toggleScreenShare() {
        if (com.jarvis.assistant.vision.ScreenCaptureManager.getInstance().isSharing.value) {
            com.jarvis.assistant.vision.ScreenCaptureService.stop(this)
            JarvisConversationService.stopScreenCapture()
            Toast.makeText(this, "Screen capture & scene recognition stopped.", Toast.LENGTH_SHORT).show()
        } else {
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        // STEP 1: Configure Firebase App Check with launch intent before any UI, ViewModels, or Firebase operations
        com.jarvis.assistant.security.configureAppCheck(this, intent)

        val prefs = com.jarvis.assistant.JarvisApp.instance.preferences
        appliedThemeKey = prefs.appTheme
        appliedCustomHex = prefs.customThemeColor
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAuthAndBlockStatus()
        com.jarvis.assistant.update.ApkUpdateManager(this).checkForUpdatesOnLaunchIfEligible(notifyIfAvailable = true)
        com.jarvis.assistant.update.JarvisFirebaseMessagingService.subscribeToUpdateTopicIfPermitted(this)

        if (!com.jarvis.assistant.security.AppIdentityVerifier.isTrustedRelease(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("App integrity check failed")
                .setMessage("This JARVIS build is not signed with the configured official release certificate. Sensitive features have been blocked.")
                .setCancelable(false)
                .setPositiveButton("Close") { _, _ -> finish() }
                .show()
            return
        }

        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.rootMainLayout)

        currentTab = savedInstanceState?.getInt("CURRENT_TAB", TAB_HOME) ?: TAB_HOME

        // Clear any old restored fragments to prevent ghost overlapping
        val existingFragments = supportFragmentManager.fragments
        if (existingFragments.isNotEmpty()) {
            val trans = supportFragmentManager.beginTransaction()
            for (fragment in existingFragments) {
                trans.remove(fragment)
            }
            trans.commitNowAllowingStateLoss()
        }

        // Add all 4 fragments fresh into container
        val trans = supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, homeFragment, "TAB_0")
            .add(R.id.fragmentContainer, chatFragment, "TAB_1")
            .add(R.id.fragmentContainer, settingsFragment, "TAB_2")
            .add(R.id.fragmentContainer, cameraFragment, "TAB_3")

        val allTabs = listOf(
            TAB_HOME to homeFragment,
            TAB_CHAT to chatFragment,
            TAB_SETTINGS to settingsFragment,
            TAB_CAMERA to cameraFragment
        )

        allTabs.forEach { (tabId, frag) ->
            if (tabId == currentTab) {
                trans.show(frag)
            } else {
                trans.hide(frag)
            }
        }
        trans.commitNow()

        setupNavigationDock()
        selectTab(currentTab)
        handleIncomingIntent(intent)
        observeViewModel()
        com.jarvis.assistant.ui.settings.VoiceCommandsGuideActivity.showStartupMobileControlPopupIfNeeded(this)
        com.jarvis.assistant.notification.AppAnnouncementManager.checkAndDisplayStartupPopup(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab != TAB_HOME) {
                    selectTab(TAB_HOME)
                } else {
                    // Preserve Android's normal root behavior (return to launcher) only
                    // after the user is already on JARVIS Home.
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val targetTab = intent.getIntExtra(com.jarvis.assistant.notification.NotificationConfig.EXTRA_NAV_TAB, -1)
        if (targetTab in 0..3) {
            selectTab(targetTab)
        }
        val chatContext = intent.getStringExtra(com.jarvis.assistant.notification.NotificationConfig.EXTRA_CHAT_CONTEXT)
        if (!chatContext.isNullOrBlank()) {
            chatFragment.applyContextGreeting(chatContext)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("CURRENT_TAB", currentTab)
    }

    override fun onResume() {
        super.onResume()
        checkAuthAndBlockStatus()
        com.jarvis.assistant.ui.overlay.SiriEdgeLightOverlay.getInstance(this).setAppForegroundState(true)
        viewModel.refreshSettings()
        val prefs = com.jarvis.assistant.JarvisApp.instance.preferences
        if (appliedThemeKey != prefs.appTheme || appliedCustomHex != prefs.customThemeColor) {
            appliedThemeKey = prefs.appTheme
            appliedCustomHex = prefs.customThemeColor
            recreate()
        }
    }

    private fun checkAuthAndBlockStatus() {
        val prefs = com.jarvis.assistant.JarvisApp.instance.preferences
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            if (!prefs.isGuestMode) {
                val intent = Intent(this, com.jarvis.assistant.ui.auth.AuthActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            return
        }

        lifecycleScope.launch {
            val repo = com.jarvis.assistant.data.repository.UserAdminRepository(this@MainActivity)
            if (repo.checkIsUserBlocked(user.uid)) {
                val intent = Intent(this@MainActivity, com.jarvis.assistant.ui.admin.BlockedUserActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return@launch
            }

            // Sync user session (lastActiveAt once per day)
            repo.syncCurrentUserSession(this@MainActivity, user)
        }
    }

    override fun onPause() {
        com.jarvis.assistant.ui.overlay.SiriEdgeLightOverlay.getInstance(this).setAppForegroundState(false)
        super.onPause()
    }

    private fun setupNavigationDock() {
        binding.tabHome.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            selectTab(TAB_HOME)
        }
        binding.tabChat.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            selectTab(TAB_CHAT)
        }
        binding.tabSettings.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            selectTab(TAB_SETTINGS)
        }
        binding.tabCamera.setOnClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            if (currentTab == TAB_CAMERA) {
                val isActive = viewModel.toggleVisionStream()
                val msg = if (isActive) "👁️ Vision Stream Connected & Active" else "👁️ Vision Stream Processing Disabled"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            } else {
                selectTab(TAB_CAMERA)
                val isActive = viewModel.isVisionStreamActive.value
                val msg = if (isActive) "👁️ Vision Stream Connected & Active" else "👁️ Vision Stream Processing Disabled (Tap Vision to enable)"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        binding.tabCamera.setOnLongClickListener {
            com.jarvis.assistant.util.HapticFeedbackHelper.performControlClick(it)
            val isActive = viewModel.toggleVisionStream()
            val msg = if (isActive) "👁️ Vision Stream Connected & Active" else "👁️ Vision Stream Processing Disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            true
        }

        binding.tabMic.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    binding.orbMicView.startHoldRipple()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    binding.orbMicView.stopHoldRipple()
                }
            }
            false
        }

        binding.tabMic.setOnClickListener {
            val willActivateMic = !JarvisConversationService.isSessionOn.value || viewModel.isMicMuted.value
            com.jarvis.assistant.util.HapticFeedbackManager.triggerMicActivation(
                activated = willActivateMic,
                view = it
            )
            handleMicOrOrbClick()
        }

        binding.tabMic.setOnLongClickListener {
            handleHoldMicOrOrb()
            true
        }
    }

    fun selectTab(tab: Int) {
        val previousTab = currentTab
        currentTab = tab

        val tabs = listOf(
            TAB_HOME to homeFragment,
            TAB_CHAT to chatFragment,
            TAB_SETTINGS to settingsFragment,
            TAB_CAMERA to cameraFragment
        )

        val trans = supportFragmentManager.beginTransaction()
        tabs.forEach { (tabId, frag) ->
            if (frag.isAdded) {
                if (tabId == tab) {
                    trans.show(frag)
                } else {
                    trans.hide(frag)
                }
            }
        }
        trans.commitNowAllowingStateLoss()

        tabs.forEach { (tabId, frag) ->
            val v = frag.view
            if (v != null) {
                if (tabId == tab) {
                    v.visibility = View.VISIBLE
                    if (tabId != TAB_HOME && tabId != TAB_CAMERA) {
                        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(v)
                    }
                    if (previousTab != tab) {
                        v.alpha = 0f
                        v.translationX = if (tab > previousTab) 18f else -18f
                        v.animate().cancel()
                        v.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(190L)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    } else {
                        v.alpha = 1f
                        v.translationX = 0f
                    }
                } else {
                    v.animate().cancel()
                    v.visibility = View.GONE
                }
            }
        }

        val isFullScreenCamera = (tab == TAB_CAMERA)
        binding.bottomDock.visibility = if (isFullScreenCamera) View.GONE else View.VISIBLE
        binding.dockCurvedLine.visibility = if (isFullScreenCamera) View.GONE else View.VISIBLE

        updateDockUI(tab)
    }

    private fun updateDockUI(tab: Int) {
        val activeColor = android.graphics.Color.parseColor("#F59E0B")
        val inactiveColor = android.graphics.Color.parseColor("#94A3B8")

        val navItems = listOf(
            Triple(TAB_HOME, binding.indicatorTabHome, Pair(binding.ivTabHome, binding.tvTabHome)),
            Triple(TAB_CHAT, binding.indicatorTabChat, Pair(binding.ivTabChat, binding.tvTabChat)),
            Triple(TAB_CAMERA, binding.indicatorTabCamera, Pair(binding.ivTabCamera, binding.tvTabCamera)),
            Triple(TAB_SETTINGS, binding.indicatorTabSettings, Pair(binding.ivTabSettings, binding.tvTabSettings))
        )

        navItems.forEach { (tabId, indicator, views) ->
            val isSelected = tabId == tab
            val (iv, tv) = views
            if (isSelected) {
                iv.imageTintList = ColorStateList.valueOf(activeColor)
                tv.setTextColor(activeColor)
                tv.alpha = 1f
                indicator.visibility = View.VISIBLE
            } else {
                iv.imageTintList = ColorStateList.valueOf(inactiveColor)
                tv.setTextColor(inactiveColor)
                tv.alpha = 0.85f
                indicator.visibility = View.GONE
            }
        }
    }

    fun handleMicOrOrbClick() {
        if (!JarvisConversationService.isSessionOn.value) {
            checkPermissionsAndStart()
        } else if (JarvisConversationService.conversationState.value == ConversationState.SPEAKING) {
            JarvisConversationService.interruptPlayback()
        } else {
            viewModel.toggleMicMute()
        }
    }

    fun handleHoldMicOrOrb() {
        val willPowerOn = !JarvisConversationService.isSessionOn.value
        com.jarvis.assistant.util.HapticFeedbackManager.triggerPowerToggle(
            isPowerOn = willPowerOn,
            view = binding.tabMic
        )
        if (JarvisConversationService.isSessionOn.value) {
            viewModel.turnOffSession()
        } else {
            checkPermissionsAndStart()
        }
    }

    fun handleTurnOff() {
        handleHoldMicOrOrb()
    }

    fun handlePowerClick() {
        handleHoldMicOrOrb()
    }

    private fun checkPermissionsAndStart() {
        if (!PermissionHelper.hasRecordAudioPermission(this)) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (!PermissionHelper.hasNotificationPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        if (!PermissionHelper.hasBatteryOptimizationPermission(this)) {
            showBatteryPermissionDialog()
            return
        }

        viewModel.toggleSession()
    }

    private fun showBatteryPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unrestricted Battery Required")
            .setMessage("Jarvis requires Unrestricted Battery access so it can stay active in the background and respond to wake words.\n\nPlease allow it or open Settings.")
            .setPositiveButton("ALLOW") { _, _ ->
                PermissionHelper.requestBatteryOptimization(this)
            }
            .setNeutralButton("OPEN SETTINGS") { _, _ ->
                selectTab(TAB_SETTINGS)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.isMicMuted, viewModel.conversationState, viewModel.isSessionOn) { isMuted, state, isOn ->
                        Triple(isMuted, state, isOn)
                    }.collect { (isMuted, state, isOn) ->
                        binding.orbMicView.updateOrbMicState(isOn, isMuted, state)
                        binding.pillTabMic.background = null
                        binding.pillTabMic.alpha = 1f
                        if (isOn && !isMuted && (state == ConversationState.LISTENING || state == ConversationState.THINKING || state == ConversationState.SPEAKING)) {
                            binding.pillTabMic.animate()
                                .scaleX(1.06f)
                                .scaleY(1.06f)
                                .setDuration(280)
                                .start()
                        } else {
                            binding.pillTabMic.animate().cancel()
                            binding.pillTabMic.scaleX = 1f
                            binding.pillTabMic.scaleY = 1f
                        }
                    }
                }

                launch {
                    viewModel.audioFrequency.collect { freq ->
                        binding.orbMicView.updateAudioFrequency(freq.amplitude, freq.bass, freq.high)
                        val isOn = viewModel.isSessionOn.value
                        val isMuted = viewModel.isMicMuted.value
                        val state = viewModel.conversationState.value
                        if (isOn && !isMuted && (state == ConversationState.LISTENING || state == ConversationState.THINKING || state == ConversationState.SPEAKING)) {
                            val dynamicScale = 1.03f + (freq.amplitude * 0.09f + freq.bass * 0.04f).coerceIn(0f, 0.12f)
                            binding.pillTabMic.scaleX = dynamicScale
                            binding.pillTabMic.scaleY = dynamicScale
                        }
                    }
                }

                launch {
                    viewModel.eventFlow.collect { message ->
                        activeSnackbar?.dismiss()
                        activeSnackbar = Snackbar.make(binding.rootMainLayout, message, Snackbar.LENGTH_SHORT).apply {
                            setAnchorView(binding.bottomDock)
                            show()
                        }
                    }
                }

                launch {
                    viewModel.isVisionStreamActive.collect { active ->
                        if (active) {
                            binding.badgeCamera.visibility = View.VISIBLE
                            binding.badgeCamera.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF"))
                            binding.tvTabCamera.text = "Vision"
                            if (currentTab == TAB_CAMERA) {
                                binding.ivTabCamera.imageTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF"))
                            }
                        } else {
                            binding.badgeCamera.visibility = View.GONE
                            binding.tvTabCamera.text = "Vision OFF"
                            binding.ivTabCamera.imageTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#64748B"))
                        }
                    }
                }
            }
        }
    }
}
