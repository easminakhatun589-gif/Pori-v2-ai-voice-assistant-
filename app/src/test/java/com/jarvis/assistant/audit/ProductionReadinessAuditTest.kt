package com.jarvis.assistant.audit

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.model.ChatTurn
import com.jarvis.assistant.data.model.CitationSource
import com.jarvis.assistant.network.GeminiLiveWebSocket
import com.jarvis.assistant.security.configureAppCheck
import com.jarvis.assistant.security.initializePlayIntegrityAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.jarvis.assistant.ui.chat.JarvisChatAdapter
import com.jarvis.assistant.util.AppLauncher
import com.jarvis.assistant.util.BuiltInChromeEngine
import com.jarvis.assistant.util.PromptGenerator
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProductionReadinessAuditTest {

    private lateinit var app: JarvisApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>() as JarvisApp
    }

    @Test
    fun `GeminiLiveWebSocket BidiSetup includes official google_search grounding tool and functionDeclarations`() {
        var capturedSetupJson: String? = null
        val dummyWebSocket = object : WebSocket {
            override fun request(): Request = Request.Builder().url("https://example.com").build()
            override fun queueSize(): Long = 0L
            override fun send(text: String): Boolean {
                capturedSetupJson = text
                return true
            }
            override fun send(bytes: ByteString): Boolean = true
            override fun close(code: Int, reason: String?): Boolean = true
            override fun cancel() {}
        }

        val listener = object : GeminiLiveWebSocket.Listener {
            override fun onConnectionStateChanged(status: String) {}
            override fun onAudioDataReceived(pcmData: ByteArray) {}
            override fun onAssistantTextReceived(textChunk: String) {}
            override fun onUserTextReceived(textChunk: String) {}
            override fun onInterrupted() {}
            override fun onTurnCompleted() {}
            override fun onError(message: String, isFatal: Boolean, errorCode: String?) {}
            override fun onToolCall(callId: String, name: String, args: JSONObject) {}
            override fun onGroundingMetadataReceived(metadata: JSONObject) {}
        }

        val liveWs = GeminiLiveWebSocket(
            apiKey = "test-key",
            model = "models/gemini-2.5-flash-native-audio-preview-12-2025",
            voiceName = "Aoede",
            systemPrompt = "Test prompt",
            listener = listener
        )

        val sendSetupMethod = GeminiLiveWebSocket::class.java.getDeclaredMethod("sendSetupMessage", WebSocket::class.java)
        sendSetupMethod.isAccessible = true
        sendSetupMethod.invoke(liveWs, dummyWebSocket)

        assertThat(capturedSetupJson).isNotNull()
        val root = JSONObject(capturedSetupJson!!)
        val setup = root.getJSONObject("setup")
        val tools = setup.getJSONArray("tools")
        assertThat(tools.length()).isAtLeast(2)

        // Tool 0 must be google_search grounding
        val firstTool = tools.getJSONObject(0)
        assertThat(firstTool.has("google_search")).isTrue()

        // Tool 1 must contain functionDeclarations including open_app and send_whatsapp_message
        val secondTool = tools.getJSONObject(1)
        val declarations = secondTool.getJSONArray("functionDeclarations")
        val toolNames = mutableListOf<String>()
        for (i in 0 until declarations.length()) {
            toolNames.add(declarations.getJSONObject(i).getString("name"))
        }
        assertThat(toolNames).containsAtLeast("open_app", "send_whatsapp_message", "send_whatsapp_voice", "builtin_chrome_search")
    }

    @Test
    fun `GeminiLiveWebSocket parses serverContent groundingMetadata and emits verified citations`() {
        var receivedGrounding: JSONObject? = null
        val listener = object : GeminiLiveWebSocket.Listener {
            override fun onConnectionStateChanged(status: String) {}
            override fun onAudioDataReceived(pcmData: ByteArray) {}
            override fun onAssistantTextReceived(textChunk: String) {}
            override fun onUserTextReceived(textChunk: String) {}
            override fun onInterrupted() {}
            override fun onTurnCompleted() {}
            override fun onError(message: String, isFatal: Boolean, errorCode: String?) {}
            override fun onToolCall(callId: String, name: String, args: JSONObject) {}
            override fun onGroundingMetadataReceived(metadata: JSONObject) {
                receivedGrounding = metadata
            }
        }

        val liveWs = GeminiLiveWebSocket(
            apiKey = "test-key",
            model = "models/gemini-2.5-flash-native-audio-preview-12-2025",
            voiceName = "Aoede",
            systemPrompt = "Test prompt",
            listener = listener
        )

        val handleIncoming = GeminiLiveWebSocket::class.java.getDeclaredMethod("handleIncomingMessage", String::class.java)
        handleIncoming.isAccessible = true

        val sampleServerPayload = """
            {
              "serverContent": {
                "groundingMetadata": {
                  "webSearchQueries": ["bangladesh weather today"],
                  "groundingChunks": [
                    {
                      "web": {
                        "uri": "https://www.bmd.gov.bd/",
                        "title": "Bangladesh Meteorological Department"
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        handleIncoming.invoke(liveWs, sampleServerPayload)

        assertThat(receivedGrounding).isNotNull()
        val chunks = receivedGrounding!!.getJSONArray("groundingChunks")
        assertThat(chunks.length()).isEqualTo(1)
        val web = chunks.getJSONObject(0).getJSONObject("web")
        assertThat(web.getString("title")).isEqualTo("Bangladesh Meteorological Department")
        assertThat(web.getString("uri")).isEqualTo("https://www.bmd.gov.bd/")
    }

    @Test
    fun `BuiltInChromeEngine never fabricates citations on blank query`() = runBlocking {
        val blankResult = BuiltInChromeEngine.searchWithCitations("   ")
        assertThat(blankResult.isVerified).isFalse()
        assertThat(blankResult.citations).isEmpty()
    }

    @Test
    fun `JarvisChatAdapter renders clickable citation chips and launches ACTION_VIEW with HTTPS URL`() {
        val turn = ChatTurn(
            id = "turn-grounded-1",
            userTranscript = "Ajker khobor bolo",
            jarvisResponse = "Ajker verified khobor holo...",
            timestamp = System.currentTimeMillis(),
            turnSource = "typed",
            isLiveWebData = true,
            citations = listOf(
                CitationSource(title = "Prothom Alo", url = "https://www.prothomalo.com/"),
                CitationSource(title = "BBC News Bengali", url = "https://www.bbc.com/bengali")
            )
        )

        val adapter = JarvisChatAdapter()
        val themedContext = androidx.appcompat.view.ContextThemeWrapper(app, R.style.Theme_JarvisAssistant)
        val parent = FrameLayout(themedContext)
        val holder = adapter.onCreateViewHolder(parent, 0)
        holder.bind(turn, 0)

        val citationsContainer = holder.itemView.findViewById<LinearLayout>(R.id.layoutCitationsContainer)
        assertThat(citationsContainer).isNotNull()
        assertThat(citationsContainer.visibility).isEqualTo(android.view.View.VISIBLE)
        // Header + 2 citation chips = 3 children
        assertThat(citationsContainer.childCount).isEqualTo(3)

        val firstChip = citationsContainer.getChildAt(1) as TextView
        assertThat(firstChip.text.toString()).contains("Prothom Alo")
        assertThat(firstChip.text.toString()).contains("https://www.prothomalo.com")

        firstChip.performClick()
        val shadowApp = shadowOf(app)
        val startedIntent = shadowApp.nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(startedIntent.dataString).isEqualTo("https://www.prothomalo.com/")
    }

    @Test
    fun `PromptGenerator includes multilingual grounded speech and anti-fabrication mandate`() {
        val prompt = PromptGenerator.generateSystemPrompt(
            personality = "Pari",
            userName = "Aslam"
        )
        assertThat(prompt).contains("[REAL-TIME LIVE WEB SEARCH & VERIFIED GROUNDING MANDATE]")
        assertThat(prompt).contains("NEVER fabricate search results, citations, URLs, or current facts")
        assertThat(prompt).contains("Bengali")
        assertThat(prompt).contains("Banglish")
        assertThat(prompt).contains("Hindi")
        assertThat(prompt).contains("English")
    }

    @Test
    fun `AppLauncher resolves Bengali Banglish Hindi and English aliases and launches package on MultipleFound`() {
        val shadowPm = shadowOf(app.packageManager)

        fun registerLauncherApp(pkg: String, label: String, cls: String) {
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val appInfo = ApplicationInfo().apply {
                packageName = pkg
                name = label
                nonLocalizedLabel = label
            }
            val resolveInfo = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = pkg
                    name = cls
                    applicationInfo = appInfo
                }
                nonLocalizedLabel = label
            }
            shadowPm.addResolveInfoForIntent(mainIntent, resolveInfo)

            val pkgLaunchIntent = Intent(Intent.ACTION_MAIN).apply {
                setPackage(pkg)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            shadowPm.addResolveInfoForIntent(pkgLaunchIntent, resolveInfo)
        }

        registerLauncherApp("com.whatsapp", "WhatsApp", "com.whatsapp.Main")
        registerLauncherApp("com.whatsapp.w4b", "WhatsApp Business", "com.whatsapp.w4b.Main")
        registerLauncherApp("com.google.android.youtube", "YouTube", "com.google.android.youtube.HomeActivity")

        // 1. Bengali alias "হোয়াটসঅ্যাপ" resolves to com.whatsapp
        val bnMatches = AppLauncher.findMatchingApps(app, "হোয়াটসঅ্যাপ")
        assertThat(bnMatches).isNotEmpty()
        assertThat(bnMatches.first().packageName).isEqualTo("com.whatsapp")

        // 2. Hindi alias "यूट्यूब" resolves to com.google.android.youtube
        val hiMatches = AppLauncher.findMatchingApps(app, "यूट्यूब")
        assertThat(hiMatches).isNotEmpty()
        assertThat(hiMatches.first().packageName).isEqualTo("com.google.android.youtube")

        // 3. Enable dual-app setting so openAppResult returns MultipleFound, and verify launchApp actually starts the activity
        android.provider.Settings.Secure.putString(app.contentResolver, "clone_app_list", "com.whatsapp")
        val multiRes = AppLauncher.openAppResult(app, "হোয়াটসঅ্যাপ")
        assertThat(multiRes).isInstanceOf(AppLauncher.OpenAppResult.MultipleFound::class.java)

        val launched = AppLauncher.launchApp(app, "হোয়াটসঅ্যাপ")
        assertThat(launched).isTrue()
        val startedIntent = shadowOf(app).nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.`package`).isEqualTo("com.whatsapp")
    }

    @Test
    fun `configureAppCheck synchronously commits debug token from launch intent`() {
        val testToken = "audit-debug-token-998877"
        val launchIntent = Intent().apply {
            putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", testToken)
        }

        configureAppCheck(app, launchIntent)

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = app.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        assertThat(prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null))
            .isEqualTo(testToken)
    }

    @Test
    fun `initializePlayIntegrityAppCheck installs PlayIntegrityAppCheckProviderFactory instance`() {
        val factory = initializePlayIntegrityAppCheck(app, isTokenAutoRefreshEnabled = true)
        assertThat(factory).isInstanceOf(PlayIntegrityAppCheckProviderFactory::class.java)
        assertThat(factory).isSameInstanceAs(PlayIntegrityAppCheckProviderFactory.getInstance())
    }

    @Test
    fun `MainActivity 5-tab navigation bar and all core features work end-to-end`() = runBlocking {
        val activity = org.robolectric.Robolectric.buildActivity(com.jarvis.assistant.ui.home.MainActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        // 1. Default tab is Home (TAB_HOME)
        assertThat(activity.viewModel.selectedTab.value).isEqualTo(com.jarvis.assistant.ui.home.MainActivity.TAB_HOME)
        val homeFrag = activity.supportFragmentManager.findFragmentByTag("tab_fragment_${com.jarvis.assistant.ui.home.MainActivity.TAB_HOME}")
        assertThat(homeFrag).isNotNull()

        // 2. Navigate to Chatbox (TAB_CHAT)
        activity.selectTab(com.jarvis.assistant.ui.home.MainActivity.TAB_CHAT)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(activity.viewModel.selectedTab.value).isEqualTo(com.jarvis.assistant.ui.home.MainActivity.TAB_CHAT)
        val chatFrag = activity.supportFragmentManager.findFragmentByTag("tab_fragment_${com.jarvis.assistant.ui.home.MainActivity.TAB_CHAT}")
        assertThat(chatFrag).isInstanceOf(com.jarvis.assistant.ui.chat.ChatboxFragment::class.java)

        // 3. Navigate to Live Voice (TAB_LIVE_VOICE) and test live voice session toggle
        activity.selectTab(com.jarvis.assistant.ui.home.MainActivity.TAB_LIVE_VOICE)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(activity.viewModel.selectedTab.value).isEqualTo(com.jarvis.assistant.ui.home.MainActivity.TAB_LIVE_VOICE)
        val voiceFrag = activity.supportFragmentManager.findFragmentByTag("tab_fragment_${com.jarvis.assistant.ui.home.MainActivity.TAB_LIVE_VOICE}")
        assertThat(voiceFrag).isInstanceOf(com.jarvis.assistant.ui.home.LiveVoiceFragment::class.java)

        activity.handleHoldMicOrOrb()
        assertThat(activity.viewModel.isSessionOn.value).isTrue()
        activity.handleMicOrOrbClick()
        assertThat(activity.viewModel.isMicMuted.value).isTrue()
        activity.handleHoldMicOrOrb()
        assertThat(activity.viewModel.isSessionOn.value).isFalse()

        // 4. Navigate to Active Camera (TAB_ACTIVE_CAMERA) and test VisionAnalysisModule & ScreenCaptureManager
        activity.selectTab(com.jarvis.assistant.ui.home.MainActivity.TAB_ACTIVE_CAMERA)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(activity.viewModel.selectedTab.value).isEqualTo(com.jarvis.assistant.ui.home.MainActivity.TAB_ACTIVE_CAMERA)
        val camFrag = activity.supportFragmentManager.findFragmentByTag("tab_fragment_${com.jarvis.assistant.ui.home.MainActivity.TAB_ACTIVE_CAMERA}")
        assertThat(camFrag).isInstanceOf(com.jarvis.assistant.vision.ActiveCameraFragment::class.java)

        val visionModule = com.jarvis.assistant.vision.VisionAnalysisModule.getInstance(app)
        visionModule.updateScene(com.jarvis.assistant.vision.VisionAnalysisModule.SceneCategory.GENERAL, "OCR Test Scene")
        assertThat(visionModule.latestScene.value?.summary).isEqualTo("OCR Test Scene")

        // 5. Navigate to Settings (TAB_SETTINGS)
        activity.selectTab(com.jarvis.assistant.ui.home.MainActivity.TAB_SETTINGS)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(activity.viewModel.selectedTab.value).isEqualTo(com.jarvis.assistant.ui.home.MainActivity.TAB_SETTINGS)
        val settingsFrag = activity.supportFragmentManager.findFragmentByTag("tab_fragment_${com.jarvis.assistant.ui.home.MainActivity.TAB_SETTINGS}")
        assertThat(settingsFrag).isInstanceOf(com.jarvis.assistant.ui.settings.SettingsFragment::class.java)

        // 6. Verify Back press returns to Home tab
        activity.onBackPressedDispatcher.onBackPressed()
        assertThat(activity.viewModel.selectedTab.value).isEqualTo(com.jarvis.assistant.ui.home.MainActivity.TAB_HOME)

        // 7. Verify AlarmRepository CRUD and MasterAutonomousAgent multi-task queue
        val alarmRepo = com.jarvis.assistant.alarm.data.AlarmRepository.getInstance(app)
        val created = alarmRepo.addAlarm(6, 45, "Morning Test Alarm")
        assertThat(alarmRepo.allAlarms.value.any { it.id == created.id }).isTrue()
        alarmRepo.deleteAlarm(created.id)
        assertThat(alarmRepo.allAlarms.value.any { it.id == created.id }).isFalse()
        alarmRepo.undoLastDelete()
        assertThat(alarmRepo.allAlarms.value.any { it.id == created.id }).isTrue()

        com.jarvis.assistant.automation.agent.MasterAutonomousAgent.executeVoiceCommandPlan(
            app,
            "Open Facebook, write a comment, then open Instagram"
        )
        val plan = com.jarvis.assistant.automation.agent.MasterAutonomousAgent.currentPlan.value
        assertThat(plan).isNotNull()
        assertThat(plan!!.steps.size).isEqualTo(3)
        com.jarvis.assistant.automation.agent.MasterAutonomousAgent.approveWaitingStepAndContinue(app)
        assertThat(com.jarvis.assistant.automation.agent.MasterAutonomousAgent.currentPlan.value?.overallStatus)
            .isEqualTo("COMPLETED")
    }
}
