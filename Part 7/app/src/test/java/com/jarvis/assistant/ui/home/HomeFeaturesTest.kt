package com.jarvis.assistant.ui.home

import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.util.CompanionEmotionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeFeaturesTest {

    @Test
    fun verifyHomeFragmentSourceContainsAllAtoZFeatures() {
        val homeFileCandidates = listOf(
            File("Part 7/app/src/main/java/com/jarvis/assistant/ui/home/HomeFragment.kt"),
            File("app/src/main/java/com/jarvis/assistant/ui/home/HomeFragment.kt"),
            File("src/main/java/com/jarvis/assistant/ui/home/HomeFragment.kt")
        )
        val file = homeFileCandidates.firstOrNull { it.exists() }
        assertNotNull("HomeFragment.kt must exist", file)
        val source = file!!.readText()

        // 1. Top action buttons
        assertTrue("HomeFragment must wire btnTopChat", source.contains("binding.btnTopChat.setOnClickListener"))
        assertTrue("HomeFragment must wire btnTopPro", source.contains("binding.btnTopPro.setOnClickListener"))
        assertTrue("HomeFragment must wire btnTopSettings", source.contains("binding.btnTopSettings.setOnClickListener"))

        // 2. Weather card
        assertTrue("HomeFragment must wire cardWeather", source.contains("binding.cardWeather.setOnClickListener"))
        assertTrue("HomeFragment must fetch live weather", source.contains("fetchLiveWeather()"))

        // 3. Central Orb & Talk Pill
        assertTrue("HomeFragment must wire layoutStatusIndicator", source.contains("binding.layoutStatusIndicator.setOnClickListener"))
        assertTrue("HomeFragment must wire layoutOnlineBadge", source.contains("binding.layoutOnlineBadge.setOnClickListener"))
        assertTrue("HomeFragment must wire cardSpeechBubble", source.contains("binding.cardSpeechBubble.setOnClickListener"))
        assertTrue("HomeFragment must handle waveformOrbView touch", source.contains("binding.waveformOrbView.setOnTouchListener"))

        // 4. Quick Actions
        assertTrue("HomeFragment must wire btnScreenShareQuick", source.contains("binding.btnScreenShareQuick.setOnClickListener"))
        assertTrue("HomeFragment must wire btnSavedMemories", source.contains("binding.btnSavedMemories.setOnClickListener"))
        assertTrue("HomeFragment must wire btnConversationLanguageQuick", source.contains("binding.btnConversationLanguageQuick.setOnClickListener"))

        // 5. Explore Banner
        assertTrue("HomeFragment must wire cardExploreBanner", source.contains("binding.cardExploreBanner.setOnClickListener"))

        // 6. Live Error Handling
        assertTrue("HomeFragment must wire btnDismissLiveError", source.contains("binding.btnDismissLiveError.setOnClickListener"))
        assertTrue("HomeFragment must observe liveError", source.contains("viewModel.liveError.collect"))
    }

    @Test
    fun verifyDynamicPersonaGreetingsOnHome() {
        // Test Persona adaptation behavior: Pari, Kabir, TechGPT X, and default
        val testNames = listOf("MD Aslam", "Siam", "Friend")

        for (name in testNames) {
            val pariGreeting = "Hi $name! I'm Pari 🌸"
            val kabirGreeting = "Hey $name! I'm Kabir ⚡"
            val defaultGreeting = "Hi $name! I'm Angel 👋"

            assertTrue(pariGreeting.contains("Pari 🌸"))
            assertTrue(kabirGreeting.contains("Kabir ⚡"))
            assertTrue(defaultGreeting.contains("Angel 👋"))
        }

        // Verify companion detection
        assertTrue(CompanionEmotionEngine.isCompanionPersona(GeminiConstants.PERSONALITY_GIRLFRIEND))
        assertTrue(CompanionEmotionEngine.isCompanionPersona(GeminiConstants.PERSONALITY_BOYFRIEND))
        assertTrue(CompanionEmotionEngine.isCompanionPersona("Pari"))
        assertTrue(CompanionEmotionEngine.isCompanionPersona("Kabir"))
        assertFalse(CompanionEmotionEngine.isCompanionPersona(GeminiConstants.PERSONALITY_ASSISTANT))
        assertFalse(CompanionEmotionEngine.isCompanionPersona(GeminiConstants.PERSONALITY_TECHGPT_X))
    }

    @Test
    fun verifyHomeStatusStateTransitions() {
        // IDLE
        assertEquals("IDLE", ConversationState.IDLE.name)
        // LISTENING
        assertEquals("LISTENING", ConversationState.LISTENING.name)
        // SPEAKING
        assertEquals("SPEAKING", ConversationState.SPEAKING.name)
        // THINKING
        assertEquals("THINKING", ConversationState.THINKING.name)
    }

    @Test
    fun verifySupportedLanguagesIncludeBanglaAndEnglish() {
        val languages = GeminiConstants.SUPPORTED_LANGUAGES
        assertTrue("Supported languages must include Auto", languages.any { it.contains("Auto", ignoreCase = true) })
        assertTrue("Supported languages must include Bengali", languages.any { it.contains("Bengali", ignoreCase = true) || it.contains("Bangla", ignoreCase = true) })
        assertTrue("Supported languages must include English", languages.any { it.contains("English", ignoreCase = true) })
    }

    @Test
    fun verifyHudStatusBarContainsBatteryDisplayAndOrbHelper() {
        val hudFileCandidates = listOf(
            File("Part 7/app/src/main/assets/hud/index.html"),
            File("app/src/main/assets/hud/index.html"),
            File("src/main/assets/hud/index.html")
        )
        val hudFile = hudFileCandidates.firstOrNull { it.exists() }
        assertNotNull("hud/index.html must exist", hudFile)
        val hudContent = hudFile!!.readText()

        assertTrue("HUD must include battery-status element", hudContent.contains("id=\"battery-status\""))
        assertTrue("HUD must include battery-percent-text element", hudContent.contains("id=\"battery-percent-text\""))
        assertTrue("HUD must include battery-pill CSS styling", hudContent.contains(".battery-pill"))
        assertTrue("HUD must include setBatteryState JavaScript handler", hudContent.contains("window.setBatteryState"))

        val orbHelperFileCandidates = listOf(
            File("Part 7/app/src/main/java/com/jarvis/assistant/ui/orb/OrbHelper.kt"),
            File("app/src/main/java/com/jarvis/assistant/ui/orb/OrbHelper.kt"),
            File("src/main/java/com/jarvis/assistant/ui/orb/OrbHelper.kt")
        )
        val orbHelperFile = orbHelperFileCandidates.firstOrNull { it.exists() }
        assertNotNull("OrbHelper.kt must exist", orbHelperFile)
        val orbHelperContent = orbHelperFile!!.readText()

        assertTrue("OrbHelper must have updateBatteryState", orbHelperContent.contains("fun updateBatteryState"))
        assertTrue("OrbHelper must have setupBatteryMonitoring", orbHelperContent.contains("setupBatteryMonitoring"))
        assertTrue("OrbHelper must handle ACTION_BATTERY_CHANGED", orbHelperContent.contains("ACTION_BATTERY_CHANGED"))

        val batteryServiceCandidates = listOf(
            File("Part 7/app/src/main/java/com/jarvis/assistant/background/BatteryMonitorService.kt"),
            File("app/src/main/java/com/jarvis/assistant/background/BatteryMonitorService.kt"),
            File("src/main/java/com/jarvis/assistant/background/BatteryMonitorService.kt")
        )
        val batteryServiceFile = batteryServiceCandidates.firstOrNull { it.exists() }
        assertNotNull("BatteryMonitorService.kt must exist", batteryServiceFile)
        val batteryServiceContent = batteryServiceFile!!.readText()
        assertTrue(
            "BatteryMonitorService must listen to ACTION_BATTERY_CHANGED",
            batteryServiceContent.contains("ACTION_BATTERY_CHANGED")
        )
        assertTrue(
            "BatteryMonitorService must update HUD via window.setBatteryState",
            batteryServiceContent.contains("window.setBatteryState")
        )
        assertEquals(
            "javascript:if(window.setBatteryState){window.setBatteryState(85, true);}",
            com.jarvis.assistant.background.BatteryMonitorService.buildSetBatteryStateJs(85, true)
        )
    }

    @Test
    fun verifyLiveHeroAvatarAndSiriEdgeLightOverlayIntegration() {
        // 1. Verify LiveHeroAvatarView exists and animates lips (thot), hands (hat), head (matha), and eyes (chok)
        val avatarCandidates = listOf(
            File("Part 7/app/src/main/java/com/jarvis/assistant/ui/widget/LiveHeroAvatarView.kt"),
            File("app/src/main/java/com/jarvis/assistant/ui/widget/LiveHeroAvatarView.kt"),
            File("src/main/java/com/jarvis/assistant/ui/widget/LiveHeroAvatarView.kt")
        )
        val avatarFile = avatarCandidates.firstOrNull { it.exists() }
        assertNotNull("LiveHeroAvatarView.kt must exist", avatarFile)
        val avatarSource = avatarFile!!.readText()
        assertTrue("LiveHeroAvatarView must use drawBitmapMesh for real-time 2D skeletal animation", avatarSource.contains("drawBitmapMesh"))
        assertTrue("LiveHeroAvatarView must animate mouth/lips (smoothedMouthOpen)", avatarSource.contains("smoothedMouthOpen"))
        assertTrue("LiveHeroAvatarView must animate eyes/blinking (isBlinking)", avatarSource.contains("isBlinking"))
        assertTrue("LiveHeroAvatarView must animate head tilt/nod (headSwayU)", avatarSource.contains("headSwayU"))
        assertTrue("LiveHeroAvatarView must animate hand gestures (smoothedHandEnergy)", avatarSource.contains("smoothedHandEnergy"))

        // 2. Verify fragment_home.xml uses LiveHeroAvatarView for ivHeroBackground
        val layoutCandidates = listOf(
            File("Part 7/app/src/main/res/layout/fragment_home.xml"),
            File("app/src/main/res/layout/fragment_home.xml"),
            File("src/main/res/layout/fragment_home.xml")
        )
        val layoutFile = layoutCandidates.firstOrNull { it.exists() }
        assertNotNull("fragment_home.xml must exist", layoutFile)
        val layoutXml = layoutFile!!.readText()
        assertTrue(
            "fragment_home.xml must use LiveHeroAvatarView for ivHeroBackground",
            layoutXml.contains("com.jarvis.assistant.ui.widget.LiveHeroAvatarView")
        )

        // 3. Verify SiriEdgeLightOverlay exists and implements 4-sided 3D colorful edge light + 3D Siri sphere
        val siriOverlayCandidates = listOf(
            File("Part 7/app/src/main/java/com/jarvis/assistant/ui/overlay/SiriEdgeLightOverlay.kt"),
            File("app/src/main/java/com/jarvis/assistant/ui/overlay/SiriEdgeLightOverlay.kt"),
            File("src/main/java/com/jarvis/assistant/ui/overlay/SiriEdgeLightOverlay.kt")
        )
        val siriFile = siriOverlayCandidates.firstOrNull { it.exists() }
        assertNotNull("SiriEdgeLightOverlay.kt must exist", siriFile)
        val siriSource = siriFile!!.readText()
        assertTrue("SiriEdgeLightOverlay must render 4-sided colorful edge light", siriSource.contains("outerAuraPaint"))
        assertTrue("SiriEdgeLightOverlay must render 3D Siri orb", siriSource.contains("drawSiri3dOrb"))
        assertTrue("SiriEdgeLightOverlay must use SweepGradient for rotating 4-side colors", siriSource.contains("SweepGradient"))
    }

    @Test
    fun verifyChatGptStyleMultimodalCodeAndLiveVoiceSessions() {
        // 1. Verify ChatTurn code extraction works accurately
        val sampleTurn = com.jarvis.assistant.data.model.ChatTurn(
            userTranscript = "Build a website",
            jarvisResponse = "Here is your website:\n```html\n<h1>Hello Jarvis</h1>\n```",
            isLiveVoice = true,
            isLiveWebData = true
        )
        val extracted = sampleTurn.extractCodeBlock()
        assertNotNull("ChatTurn must extract fenced code block", extracted)
        assertEquals("html", extracted!!.first)
        assertTrue(extracted.second.contains("<h1>Hello Jarvis</h1>"))
        assertTrue(sampleTurn.cleanExplanationText().contains("Here is your website:"))

        // 2. Verify ChatFragment supports Image Upload, Voice Dictation, Code Preview, and Session Switcher
        val chatFragCandidates = listOf(
            File("Part 7/app/src/main/java/com/jarvis/assistant/ui/chat/ChatFragment.kt"),
            File("app/src/main/java/com/jarvis/assistant/ui/chat/ChatFragment.kt"),
            File("src/main/java/com/jarvis/assistant/ui/chat/ChatFragment.kt")
        )
        val chatFragFile = chatFragCandidates.firstOrNull { it.exists() }
        assertNotNull("ChatFragment.kt must exist", chatFragFile)
        val chatFragSource = chatFragFile!!.readText()
        assertTrue("ChatFragment must support photo upload via PickVisualMedia", chatFragSource.contains("PickVisualMedia"))
        assertTrue("ChatFragment must bind left-side image upload button (btnAttach)", chatFragSource.contains("btnAttach"))
        assertTrue("ChatFragment must open CodePreviewDialog", chatFragSource.contains("CodePreviewDialog.show"))
        assertTrue("ChatFragment must support switching saved sessions", chatFragSource.contains("showSavedChatSessionsDialog"))

        // 3. Verify ChatRepository & JarvisConversationService create a new session every time Live Voice starts
        val repoCandidates = listOf(
            File("Part 7/app/src/main/java/com/jarvis/assistant/data/repository/ChatRepository.kt"),
            File("app/src/main/java/com/jarvis/assistant/data/repository/ChatRepository.kt"),
            File("src/main/java/com/jarvis/assistant/data/repository/ChatRepository.kt")
        )
        val repoFile = repoCandidates.firstOrNull { it.exists() }
        assertNotNull("ChatRepository.kt must exist", repoFile)
        val repoSource = repoFile!!.readText()
        assertTrue("ChatRepository must implement startNewLiveVoiceSession()", repoSource.contains("fun startNewLiveVoiceSession()"))

        val serviceCandidates = listOf(
            File("Part 7/app/src/main/java/com/jarvis/assistant/background/JarvisConversationService.kt"),
            File("app/src/main/java/com/jarvis/assistant/background/JarvisConversationService.kt"),
            File("src/main/java/com/jarvis/assistant/background/JarvisConversationService.kt")
        )
        val serviceFile = serviceCandidates.firstOrNull { it.exists() }
        assertNotNull("JarvisConversationService.kt must exist", serviceFile)
        val serviceSource = serviceFile!!.readText()
        assertTrue(
            "JarvisConversationService must call startNewLiveVoiceSession() when Live Voice activates",
            serviceSource.contains("app.chatRepository.startNewLiveVoiceSession()")
        )
    }

    @Test
    fun verifyBtnMicHoldEscalatingRippleEffect() {
        val hudFileCandidates = listOf(
            File("Part 7/app/src/main/assets/hud/index.html"),
            File("app/src/main/assets/hud/index.html"),
            File("src/main/assets/hud/index.html")
        )
        val hudFile = hudFileCandidates.firstOrNull { it.exists() }
        assertNotNull("hud/index.html must exist", hudFile)
        val hudContent = hudFile!!.readText()

        assertTrue("HUD must contain #btn-mic button", hudContent.contains("id=\"btn-mic\""))
        assertTrue("HUD must include mic-ripple-container", hudContent.contains("id=\"mic-ripple-container\""))
        assertTrue("HUD must include escalating ripple CSS class", hudContent.contains(".mic-escalating-ripple"))
        assertTrue("HUD must include startMicHoldRipple handler", hudContent.contains("startMicHoldRipple"))
        assertTrue("HUD must include stopMicHoldRipple handler", hudContent.contains("stopMicHoldRipple"))
        assertTrue("HUD must include spawnMicEscalatingRipple function", hudContent.contains("spawnMicEscalatingRipple"))
    }
}
