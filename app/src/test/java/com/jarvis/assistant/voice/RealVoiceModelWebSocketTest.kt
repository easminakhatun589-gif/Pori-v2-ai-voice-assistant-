package com.jarvis.assistant.voice

import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.network.VoiceModelHealthMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RealVoiceModelWebSocketTest {

    private val validGoogleLiveBidiModels = setOf(
        "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "models/gemini-2.0-flash-live-001",
        "models/gemini-2.5-flash-native-audio-preview-09-2025",
        "models/gemini-2.0-flash-exp"
    )

    @Test
    fun allModelsInCatalogHaveGenuineLiveBidiWireModels() {
        val catalog = GeminiConstants.MODEL_CATALOG
        assertEquals("Catalog must contain exactly 8 models", 8, catalog.size)

        for (model in catalog) {
            assertTrue("Model ID must not be empty", model.id.isNotBlank())
            assertTrue("Model DisplayName must not be empty", model.displayName.isNotBlank())
            assertTrue(
                "Model ${model.id} bidiWireModel must be one of the confirmed Google Live Bidi models, got: ${model.bidiWireModel}",
                model.bidiWireModel in validGoogleLiveBidiModels
            )
            assertFalse(
                "Model ${model.id} must NOT use non-existent wire preview: models/gemini-3.1-flash-live-preview",
                model.bidiWireModel == "models/gemini-3.1-flash-live-preview"
            )

            // Verify fallbacks
            assertTrue("Model ${model.id} must have fallbacks", model.fallbackBidiWireModels.isNotEmpty())
            for (fallback in model.fallbackBidiWireModels) {
                assertTrue(
                    "Fallback $fallback for model ${model.id} must be a valid Google Live model",
                    fallback in validGoogleLiveBidiModels
                )
            }

            // Verify thinking budget is 0 for instant, zero-lag reply
            assertEquals(
                "Model ${model.id} thinking budget must be 0 for conversational speed",
                0,
                model.thinkingBudget
            )
        }
    }

    @Test
    fun resolveWorkingBidiWireModelReturnsValidLiveModelForAllEntries() {
        for (model in GeminiConstants.MODEL_CATALOG) {
            val resolved = VoiceModelHealthMonitor.resolveWorkingBidiWireModel(model.id)
            assertTrue(
                "Resolved wire model for ${model.id} must be valid, got: $resolved",
                resolved in validGoogleLiveBidiModels
            )
        }
    }

    @Test
    fun modelFailoverSelectsAlternativeHealthyModelOnStall() {
        val currentModel = GeminiConstants.DEFAULT_MODEL
        val initialInfo = GeminiConstants.getModelInfo(currentModel)

        // Record a stall on the current model
        VoiceModelHealthMonitor.recordModelStallOrError(currentModel, "Test timeout stall")

        val failoverModel = VoiceModelHealthMonitor.selectBestFailoverModel(currentModel)
        assertNotNull(failoverModel)
        assertTrue(
            "Failover model must be different from the stalled model",
            failoverModel.id != initialInfo.id
        )
        assertTrue(
            "Failover model bidiWireModel must be a valid live model",
            failoverModel.bidiWireModel in validGoogleLiveBidiModels
        )
    }

    @Test
    fun webSocketProbeTelemetryUpdatesBadgeCorrectly() {
        val testModelId = GeminiConstants.MODEL_3_8_FLASH_LIVE
        val testWire = "models/gemini-2.5-flash-native-audio-preview-12-2025"

        VoiceModelHealthMonitor.updateModelWithWsProbe(
            modelId = testModelId,
            wireModel = testWire,
            wsLatencyMs = 125L,
            isSetupComplete = true
        )

        val telemetry = VoiceModelHealthMonitor.getTelemetry(testModelId)
        assertTrue("Telemetry must report WebSocket connected", telemetry.isWsConnected)
        assertEquals(125L, telemetry.wsPingMs)
        assertEquals(testWire, telemetry.resolvedBidiWireModel)

        val badge = telemetry.formatCompactPingBadge()
        assertTrue("Badge must contain WS indicator, got: $badge", badge.contains("WS"))
        assertTrue("Badge must contain latency ms, got: $badge", badge.contains("125ms"))
    }

    @Test
    fun liveVoiceWebSocketFullPipelineHandshakeAudioStreamingAndTurnCompletion() {
        val sentFrames = mutableListOf<String>()
        val statusChanges = mutableListOf<String>()
        val receivedAudioChunks = mutableListOf<ByteArray>()
        val receivedUserTexts = mutableListOf<String>()
        val receivedAssistantTexts = mutableListOf<String>()
        val receivedToolCalls = mutableListOf<Triple<String, String, String>>()
        var turnCompletedCount = 0

        val fakeWebSocket = object : okhttp3.WebSocket {
            override fun request(): okhttp3.Request =
                okhttp3.Request.Builder().url("https://example.com").build()
            override fun queueSize(): Long = 0L
            override fun send(text: String): Boolean {
                sentFrames.add(text)
                return true
            }
            override fun send(bytes: okio.ByteString): Boolean = true
            override fun close(code: Int, reason: String?): Boolean = true
            override fun cancel() {}
        }

        val listener = object : com.jarvis.assistant.network.GeminiLiveWebSocket.Listener {
            override fun onConnectionStateChanged(status: String) {
                statusChanges.add(status)
            }
            override fun onAudioDataReceived(pcmData: ByteArray) {
                receivedAudioChunks.add(pcmData)
            }
            override fun onAssistantTextReceived(textChunk: String) {
                receivedAssistantTexts.add(textChunk)
            }
            override fun onUserTextReceived(textChunk: String) {
                receivedUserTexts.add(textChunk)
            }
            override fun onInterrupted() {}
            override fun onTurnCompleted() {
                turnCompletedCount++
            }
            override fun onError(message: String, isFatal: Boolean, errorCode: String?) {}
            override fun onToolCall(callId: String, name: String, args: org.json.JSONObject) {
                receivedToolCalls.add(Triple(callId, name, args.toString()))
            }
            override fun onGroundingMetadataReceived(metadata: org.json.JSONObject) {}
        }

        val liveWs = com.jarvis.assistant.network.GeminiLiveWebSocket(
            apiKey = "AIzaSyTestLiveVoiceKey123456789",
            model = GeminiConstants.DEFAULT_MODEL,
            voiceName = "Aoede",
            systemPrompt = "You are JARVIC AI.",
            listener = listener
        )

        // Inject fakeWebSocket and mark connected
        val wsField = com.jarvis.assistant.network.GeminiLiveWebSocket::class.java.getDeclaredField("webSocket").apply {
            isAccessible = true
        }
        wsField.set(liveWs, fakeWebSocket)

        val isConnectedField = com.jarvis.assistant.network.GeminiLiveWebSocket::class.java.getDeclaredField("isConnected").apply {
            isAccessible = true
        }
        (isConnectedField.get(liveWs) as java.util.concurrent.atomic.AtomicBoolean).set(true)

        // 1. Verify BidiSetup message
        val sendSetupMethod = com.jarvis.assistant.network.GeminiLiveWebSocket::class.java.getDeclaredMethod(
            "sendSetupMessage",
            okhttp3.WebSocket::class.java
        ).apply { isAccessible = true }
        sendSetupMethod.invoke(liveWs, fakeWebSocket)

        assertEquals("Setup message must be sent", 1, sentFrames.size)
        val setupJson = org.json.JSONObject(sentFrames.first()).getJSONObject("setup")
        assertTrue(
            "Setup wire model must be valid Google Live Bidi model",
            setupJson.getString("model") in validGoogleLiveBidiModels
        )
        val voiceConfig = setupJson.getJSONObject("generation_config")
            .getJSONObject("speech_config")
            .getJSONObject("voice_config")
            .getJSONObject("prebuilt_voice_config")
        assertEquals("Aoede", voiceConfig.getString("voice_name"))

        // 2. Simulate server setupComplete -> Session becomes LIVE and ready
        val handleIncoming = com.jarvis.assistant.network.GeminiLiveWebSocket::class.java.getDeclaredMethod(
            "handleIncomingMessage",
            String::class.java
        ).apply { isAccessible = true }

        assertFalse("Session must not be ready before setupComplete", liveWs.isSessionReady())
        handleIncoming.invoke(liveWs, """{"setupComplete":{}}""")
        assertTrue("Session must be ready after setupComplete", liveWs.isSessionReady())
        assertTrue("Listener must receive LIVE state", statusChanges.contains("LIVE"))

        // 3. Test sending 16kHz PCM audio chunk & audio_stream_end
        val samplePcm16k = ByteArray(640) { (it % 127).toByte() }
        liveWs.sendAudioChunk(samplePcm16k)
        assertTrue(
            "Audio chunk frame must contain audio/pcm;rate=16000",
            sentFrames.last().contains("audio/pcm;rate=16000")
        )

        liveWs.sendAudioStreamEnd()
        assertTrue(
            "Audio stream end frame must signal audio_stream_end:true",
            sentFrames.last().contains("\"audio_stream_end\":true")
        )

        // 4. Simulate serverContent with 24kHz PCM audio output, inputTranscription, outputTranscription, and turnComplete
        val sampleOutputPcm24k = ByteArray(960) { ((it * 3) % 120).toByte() }
        val base64OutAudio = android.util.Base64.encodeToString(sampleOutputPcm24k, android.util.Base64.NO_WRAP)
        val turnPayload = """
            {
              "serverContent": {
                "inputTranscription": { "text": "Jarvis live voice thik ache?" },
                "outputTranscription": { "text": "Haan Boss, live voice ekdom thik kaj korche!" },
                "modelTurn": {
                  "parts": [
                    {
                      "inlineData": {
                        "mimeType": "audio/pcm;rate=24000",
                        "data": "$base64OutAudio"
                      }
                    }
                  ]
                },
                "turnComplete": true
              }
            }
        """.trimIndent()
        handleIncoming.invoke(liveWs, turnPayload)

        assertEquals(listOf("Jarvis live voice thik ache?"), receivedUserTexts)
        assertEquals(listOf("Haan Boss, live voice ekdom thik kaj korche!"), receivedAssistantTexts)
        assertEquals("Listener must receive decoded 24kHz PCM audio output chunk", 1, receivedAudioChunks.size)
        assertEquals(960, receivedAudioChunks.first().size)
        assertEquals(1, turnCompletedCount)

        // 5. Simulate toolCall & verify sendToolResponse
        val toolCallPayload = """
            {
              "toolCall": {
                "functionCalls": [
                  {
                    "id": "call_live_01",
                    "name": "open_app",
                    "args": { "app_name": "YouTube" }
                  }
                ]
              }
            }
        """.trimIndent()
        handleIncoming.invoke(liveWs, toolCallPayload)
        assertEquals(1, receivedToolCalls.size)
        assertEquals("call_live_01", receivedToolCalls.first().first)
        assertEquals("open_app", receivedToolCalls.first().second)

        liveWs.sendToolResponse("call_live_01", "open_app", org.json.JSONObject().put("status", "opened"))
        assertTrue(
            "Tool response frame must contain function_responses and call_live_01",
            sentFrames.last().contains("function_responses") && sentFrames.last().contains("call_live_01")
        )

        // 6. Verify wire model fallback rotation when a model is rejected
        val initialWireModel = liveWs.getActiveWireModel()
        val tryNextFallback = com.jarvis.assistant.network.GeminiLiveWebSocket::class.java.getDeclaredMethod(
            "tryNextFallbackModel"
        ).apply { isAccessible = true }
        val switched = tryNextFallback.invoke(liveWs) as Boolean
        assertTrue("tryNextFallbackModel must succeed with a fallback wire model", switched)
        assertTrue(
            "New active wire model must be different from initial wire model and valid",
            liveWs.getActiveWireModel() != initialWireModel && liveWs.getActiveWireModel() in validGoogleLiveBidiModels
        )
    }
}


