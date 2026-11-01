package com.jarvis.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Base64
import android.util.Log
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.alarm.voice.AlarmTemplates
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.util.CompanionEmotionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Mode-synced Real Voice Speaker for Chatbox messages, Speaker button playback, and Live Voice replies.
 *
 * Dynamically adapts its voice persona, cloud voice model ("Aoede" for Pari, "Fenrir" for Kabir,
 * "Puck" for TechGPT X, "Charon"/"Aoede" for Jarvis Pro), pitch, and speech rate to whichever
 * App Mode / Personality is currently selected in the app.
 */
object PariRealVoiceSpeaker {

    private const val TAG = "PariRealVoiceSpeaker"
    private const val DEFAULT_SAMPLE_RATE = 24000

    data class ActiveModeVoiceProfile(
        val modeId: String,
        val modeTitle: String,
        val modeBadge: String,
        val speakerButtonLabel: String,
        val speakingStatusLabel: String,
        val cloudVoiceName: String,
        val isFemaleVoice: Boolean,
        val pitch: Float,
        val speechRate: Float,
        val tone: String
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(16, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var activeMediaPlayer: MediaPlayer? = null

    @Volatile
    private var currentPlayingId: String? = null

    private val _activeSpeakingTurnId = MutableStateFlow<String?>(null)
    val activeSpeakingTurnId: StateFlow<String?> = _activeSpeakingTurnId.asStateFlow()

    @Volatile
    private var localFallbackTts: TextToSpeech? = null

    @Volatile
    private var isLocalTtsReady = false

    /**
     * Resolves the real voice profile matching the currently selected App Mode (`preferences.personality`)
     * and voice configuration (`preferences.voice` & `preferences.personaVoiceTone`).
     */
    fun resolveActiveModeVoiceProfile(context: Context? = null): ActiveModeVoiceProfile {
        val prefs: AppPreferences? = try {
            when {
                context != null && context.applicationContext is JarvisApp ->
                    (context.applicationContext as JarvisApp).preferences
                context != null ->
                    AppPreferences(context.applicationContext)
                else ->
                    JarvisApp.instance.preferences
            }
        } catch (_: Exception) {
            null
        }

        val rawPersonality = prefs?.personality?.trim().orEmpty()
        val configuredVoice = prefs?.voice?.trim().orEmpty()
        val tone = prefs?.personaVoiceTone?.trim().ifNullOrBlank { "Soft" }

        val pLower = rawPersonality.lowercase(Locale.ROOT)
        val vLower = configuredVoice.lowercase(Locale.ROOT)

        val isExplicitFemaleVoice = vLower in setOf("aoede", "kore", "leda")

        return when {
            pLower.contains("girlfriend") || pLower.contains("pari") || pLower.contains("companion") -> {
                val cloudVoice = when (vLower) {
                    "kore" -> "Kore"
                    "leda" -> "Leda"
                    else -> "Aoede"
                }
                val pitch = when (tone.lowercase(Locale.ROOT)) {
                    "cheerful" -> 1.18f
                    "calm" -> 1.08f
                    "energetic" -> 1.16f
                    else -> 1.14f
                }
                val rate = when (tone.lowercase(Locale.ROOT)) {
                    "cheerful", "energetic" -> 0.99f
                    "calm" -> 0.91f
                    else -> 0.95f
                }
                ActiveModeVoiceProfile(
                    modeId = "pari",
                    modeTitle = "Pari AI (Real Voice)",
                    modeBadge = "🌸 Mode: Pari Real Voice ($cloudVoice)",
                    speakerButtonLabel = "🌸 Pari Real Voice",
                    speakingStatusLabel = "⏹️ Speaking (Pari)...",
                    cloudVoiceName = cloudVoice,
                    isFemaleVoice = true,
                    pitch = pitch,
                    speechRate = rate,
                    tone = tone
                )
            }

            pLower.contains("boyfriend") || pLower.contains("kabir") -> {
                val cloudVoice = if (vLower in setOf("fenrir", "charon", "puck", "orus")) {
                    configuredVoice.replaceFirstChar { it.uppercase() }
                } else {
                    "Fenrir"
                }
                val pitch = when (tone.lowercase(Locale.ROOT)) {
                    "calm" -> 0.91f
                    "energetic", "cheerful" -> 0.98f
                    else -> 0.94f
                }
                ActiveModeVoiceProfile(
                    modeId = "kabir",
                    modeTitle = "Kabir AI (Real Voice)",
                    modeBadge = "⚡ Mode: Kabir Real Voice ($cloudVoice)",
                    speakerButtonLabel = "⚡ Kabir Real Voice",
                    speakingStatusLabel = "⏹️ Speaking (Kabir)...",
                    cloudVoiceName = cloudVoice,
                    isFemaleVoice = false,
                    pitch = pitch,
                    speechRate = 1.00f,
                    tone = tone
                )
            }

            pLower.contains("techgpt") || pLower.contains("trading") -> {
                val cloudVoice = if (isExplicitFemaleVoice) {
                    configuredVoice.replaceFirstChar { it.uppercase() }
                } else {
                    "Puck"
                }
                ActiveModeVoiceProfile(
                    modeId = "techgpt",
                    modeTitle = "TechGPT X",
                    modeBadge = "💡 Mode: TechGPT X ($cloudVoice)",
                    speakerButtonLabel = if (isExplicitFemaleVoice) "🌸 Pari Tech Voice" else "💡 TechGPT Real Voice",
                    speakingStatusLabel = "⏹️ Speaking (TechGPT)...",
                    cloudVoiceName = cloudVoice,
                    isFemaleVoice = isExplicitFemaleVoice,
                    pitch = if (isExplicitFemaleVoice) 1.12f else 1.00f,
                    speechRate = 1.02f,
                    tone = tone
                )
            }

            else -> {
                // Jarvis Pro / Professional / Focus Mode
                // If the user selected a female voice (Aoede/Kore/Leda), honor Pari's real female voice in Jarvis mode too
                if (isExplicitFemaleVoice) {
                    val cloudVoice = configuredVoice.replaceFirstChar { it.uppercase() }
                    ActiveModeVoiceProfile(
                        modeId = "jarvis_pari_voice",
                        modeTitle = "Jarvis Pro • Pari Voice",
                        modeBadge = "🌸 Mode: Jarvis Pro ($cloudVoice Real Voice)",
                        speakerButtonLabel = "🌸 Pari Real Voice",
                        speakingStatusLabel = "⏹️ Speaking (Pari)...",
                        cloudVoiceName = cloudVoice,
                        isFemaleVoice = true,
                        pitch = 1.13f,
                        speechRate = 0.96f,
                        tone = tone
                    )
                } else {
                    val cloudVoice = when (vLower) {
                        "charon" -> "Charon"
                        "fenrir" -> "Fenrir"
                        "puck" -> "Puck"
                        else -> "Aoede" // Default to Aoede (Pori real voice) if unset
                    }
                    val useFemale = cloudVoice.equals("Aoede", ignoreCase = true)
                    ActiveModeVoiceProfile(
                        modeId = "jarvis_pro",
                        modeTitle = if (useFemale) "Jarvis AI (Pari Voice)" else "Jarvis Pro AI",
                        modeBadge = if (useFemale) "🌸 Mode: Pari Voice ($cloudVoice)" else "🤖 Mode: Jarvis Pro ($cloudVoice)",
                        speakerButtonLabel = if (useFemale) "🌸 Pari Real Voice" else "🔊 Jarvis Real Voice",
                        speakingStatusLabel = if (useFemale) "⏹️ Speaking (Pari)..." else "⏹️ Speaking (Jarvis)...",
                        cloudVoiceName = cloudVoice,
                        isFemaleVoice = useFemale,
                        pitch = if (useFemale) 1.13f else 0.94f,
                        speechRate = if (useFemale) 0.96f else 0.98f,
                        tone = tone
                    )
                }
            }
        }
    }

    private fun String?.ifNullOrBlank(fallback: () -> String): String {
        return if (this.isNullOrBlank()) fallback() else this
    }

    /**
     * Toggles speech playback for a specific chat message turn.
     * If [turnId] is already speaking, stops playback and returns false (stopped).
     * Otherwise starts speaking in the active App Mode's real voice and returns true (started).
     */
    fun toggleSpeakTurn(
        context: Context,
        turnId: String,
        text: String,
        onStateChanged: ((isSpeaking: Boolean) -> Unit)? = null
    ): Boolean {
        if (turnId.isNotBlank() && _activeSpeakingTurnId.value == turnId && isPlaying()) {
            stop()
            onStateChanged?.invoke(false)
            return false
        }
        speakInActiveModeVoice(
            context = context,
            text = text,
            turnId = turnId,
            flushExisting = true,
            onPlaybackStarted = { onStateChanged?.invoke(true) },
            onPlaybackDone = { onStateChanged?.invoke(false) }
        )
        return true
    }

    /**
     * Legacy entry point kept for compatibility — delegates to [speakInActiveModeVoice] so all callers
     * automatically respect the currently selected App Mode and real voice.
     */
    fun speakPariReply(
        context: Context,
        text: String,
        flushExisting: Boolean = true,
        onPlaybackStarted: (() -> Unit)? = null,
        onPlaybackDone: (() -> Unit)? = null
    ) {
        speakInActiveModeVoice(
            context = context,
            text = text,
            turnId = null,
            flushExisting = flushExisting,
            onPlaybackStarted = onPlaybackStarted,
            onPlaybackDone = onPlaybackDone
        )
    }

    /**
     * Speaks [text] using the exact Real Voice corresponding to the currently active App Mode.
     */
    fun speakInActiveModeVoice(
        context: Context,
        text: String,
        turnId: String? = null,
        flushExisting: Boolean = true,
        onPlaybackStarted: (() -> Unit)? = null,
        onPlaybackDone: (() -> Unit)? = null
    ) {
        val cleanSpeech = CompanionEmotionEngine.sanitizeForSpeech(text).trim()
        if (cleanSpeech.isBlank()) {
            onPlaybackDone?.invoke()
            return
        }

        if (JarvisConversationService.isStreamingLiveAudio() && turnId == null) {
            Log.i(TAG, "Suppressing auto-speak because Gemini Live is actively streaming response audio.")
            return
        }

        if (flushExisting) {
            stop()
        }

        val speechTurnId = turnId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        currentPlayingId = speechTurnId
        _activeSpeakingTurnId.value = speechTurnId

        val profile = resolveActiveModeVoiceProfile(context)

        executor.execute {
            if (currentPlayingId != speechTurnId) return@execute

            val app = try { context.applicationContext as? JarvisApp ?: JarvisApp.instance } catch (_: Exception) { null }
            val apiKey = try {
                val prefKey = app?.preferences?.apiKey?.trim().orEmpty()
                if (prefKey.isNotBlank()) prefKey else com.jarvis.assistant.BuildConfig.GEMINI_API_KEY.trim()
            } catch (_: Exception) {
                com.jarvis.assistant.BuildConfig.GEMINI_API_KEY.trim()
            }
            var cloudSuccess = false

            // Tier 1: Gemini Cloud Neural Audio TTS with active mode's prebuilt voice (Aoede, Kore, Fenrir, Charon, Puck)
            if (apiKey.isNotBlank()) {
                com.jarvis.assistant.automation.maintenance.VoiceCacheMaintenanceManager.autoPruneAsync(context)
                val wavFile = File(context.cacheDir, "mode_voice_${profile.modeId}_${System.currentTimeMillis()}.wav")
                val rendered = synthesizeGeminiCloudTts(
                    apiKey = apiKey,
                    text = cleanSpeech.take(750),
                    voiceName = profile.cloudVoiceName,
                    outputWavFile = wavFile
                )
                if (rendered && wavFile.exists() && wavFile.length() > 512L && currentPlayingId == speechTurnId) {
                    cloudSuccess = playAudioFile(
                        file = wavFile,
                        speechTurnId = speechTurnId,
                        onStarted = onPlaybackStarted,
                        onDone = onPlaybackDone
                    )
                }
            }

            // Tier 2: High-Definition Local Neural TTS tuned to the active mode's gender, pitch, rate, and language
            if (!cloudSuccess && currentPlayingId == speechTurnId) {
                mainHandler.post {
                    if (currentPlayingId == speechTurnId) {
                        speakViaLocalTts(
                            context = context,
                            text = cleanSpeech,
                            profile = profile,
                            speechTurnId = speechTurnId,
                            flushExisting = flushExisting,
                            onStarted = onPlaybackStarted,
                            onDone = onPlaybackDone
                        )
                    }
                }
            }
        }
    }

    /**
     * Stops any currently playing speech (MediaPlayer or TextToSpeech).
     */
    fun stop() {
        currentPlayingId = null
        _activeSpeakingTurnId.value = null
        try {
            activeMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (_: Exception) {}
        activeMediaPlayer = null

        try {
            localFallbackTts?.stop()
        } catch (_: Exception) {}
    }

    fun isPlaying(): Boolean {
        return try {
            activeMediaPlayer?.isPlaying == true || localFallbackTts?.isSpeaking == true
        } catch (_: Exception) {
            false
        }
    }

    private fun synthesizeGeminiCloudTts(
        apiKey: String,
        text: String,
        voiceName: String,
        outputWavFile: File
    ): Boolean {
        val ttsModels = listOf(
            "gemini-3.8-flash-lite-tts",
            "gemini-3.8-flash-tts",
            "gemini-3.1-flash-tts-preview",
            "gemini-2.5-flash-preview-tts",
            "gemini-2.0-flash-exp"
        )
        for (modelName in ttsModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val promptText = if (modelName.contains("tts")) {
                    text
                } else {
                    "Read the following text aloud naturally and expressively without adding any extra words:\n$text"
                }
                val payload = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", promptText))
                            )
                        )
                    )
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("responseModalities", JSONArray().put("AUDIO"))
                            put(
                                "speechConfig",
                                JSONObject().put(
                                    "voiceConfig",
                                    JSONObject().put(
                                        "prebuiltVoiceConfig",
                                        JSONObject().put("voiceName", voiceName)
                                    )
                                )
                            )
                        }
                    )
                }

                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Gemini Cloud TTS ($modelName) failed HTTP ${response.code}")
                        return@use
                    }
                    val bodyStr = response.body?.string().orEmpty()
                    if (bodyStr.isBlank()) return@use

                    val root = JSONObject(bodyStr)
                    val parts = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts") ?: return@use

                    val pcmStream = ByteArrayOutputStream()
                    for (i in 0 until parts.length()) {
                        val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                        val b64 = inlineData.optString("data", "")
                        if (b64.isNotBlank()) {
                            val pcmBytes = Base64.decode(b64, Base64.DEFAULT)
                            pcmStream.write(pcmBytes)
                        }
                    }
                    val pcmData = pcmStream.toByteArray()
                    if (pcmData.size > 256) {
                        writePcm16MonoToWav(pcmData, DEFAULT_SAMPLE_RATE, outputWavFile)
                        if (outputWavFile.exists() && outputWavFile.length() > 44L) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Gemini Cloud TTS ($modelName) error: ${e.message}")
            }
        }
        return false
    }

    private fun playAudioFile(
        file: File,
        speechTurnId: String,
        onStarted: (() -> Unit)?,
        onDone: (() -> Unit)?
    ): Boolean {
        return try {
            val player = MediaPlayer()
            activeMediaPlayer = player

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.prepare()

            player.setOnCompletionListener { mp ->
                try {
                    mp.reset()
                    mp.release()
                } catch (_: Exception) {}
                if (activeMediaPlayer == mp) {
                    activeMediaPlayer = null
                }
                if (_activeSpeakingTurnId.value == speechTurnId) {
                    _activeSpeakingTurnId.value = null
                }
                try { file.delete() } catch (_: Exception) {}
                mainHandler.post { onDone?.invoke() }
            }

            player.setOnErrorListener { mp, _, _ ->
                try {
                    mp.reset()
                    mp.release()
                } catch (_: Exception) {}
                if (activeMediaPlayer == mp) {
                    activeMediaPlayer = null
                }
                if (_activeSpeakingTurnId.value == speechTurnId) {
                    _activeSpeakingTurnId.value = null
                }
                try { file.delete() } catch (_: Exception) {}
                mainHandler.post { onDone?.invoke() }
                false
            }

            player.start()
            mainHandler.post { onStarted?.invoke() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer play failed: ${e.message}", e)
            if (_activeSpeakingTurnId.value == speechTurnId) {
                _activeSpeakingTurnId.value = null
            }
            try { file.delete() } catch (_: Exception) {}
            false
        }
    }

    private fun speakViaLocalTts(
        context: Context,
        text: String,
        profile: ActiveModeVoiceProfile,
        speechTurnId: String,
        flushExisting: Boolean,
        onStarted: (() -> Unit)?,
        onDone: (() -> Unit)?
    ) {
        val app = try { context.applicationContext as? JarvisApp ?: JarvisApp.instance } catch (_: Exception) { null }
        val targetLocale = JarvisConversationService.resolveLocaleForSpeech(text, app?.preferences?.language ?: "Auto")

        fun doSpeak(engine: TextToSpeech) {
            try {
                val langName = when (targetLocale.language) {
                    "bn" -> "Bangla"
                    "hi" -> "Hindi"
                    else -> "English"
                }
                val voiceId = when (targetLocale.language) {
                    "bn" -> if (profile.isFemaleVoice) "pari_sweet_bn" else "jarvis_bn"
                    "hi" -> if (profile.isFemaleVoice) "pari_caring_hi" else "jarvis_hi"
                    else -> if (profile.isFemaleVoice) "pari_romantic_en" else "jarvis_en"
                }
                AlarmTemplates.configureAndroidTtsVoiceAndLocale(
                    tts = engine,
                    language = langName,
                    voiceId = voiceId,
                    voiceSpeed = profile.speechRate
                )

                // Select best neural voice matching the active mode's gender & locale
                try {
                    val availableVoices = engine.voices?.filter { v: Voice ->
                        v.locale.language.equals(targetLocale.language, ignoreCase = true)
                    }.orEmpty()

                    val matchedVoice = if (profile.isFemaleVoice) {
                        availableVoices.sortedByDescending { v ->
                            var score = 0
                            val n = v.name.lowercase(Locale.ROOT)
                            if (n.contains("network") || n.contains("wavenet") || n.contains("neural")) score += 4
                            if (n.contains("female") || n.contains("f0") || n.contains("sfg") || n.contains("ban-bd-x-ban") || n.contains("hin-in-x-hie")) score += 5
                            if (n.contains("male") && !n.contains("female")) score -= 8
                            score + (v.quality / 100)
                        }.firstOrNull()
                    } else {
                        availableVoices.sortedByDescending { v ->
                            var score = 0
                            val n = v.name.lowercase(Locale.ROOT)
                            if (n.contains("network") || n.contains("wavenet") || n.contains("neural")) score += 4
                            if ((n.contains("male") && !n.contains("female")) || n.contains("m0") || n.contains("iom") || n.contains("hi-in-x-hid")) score += 5
                            if (n.contains("female")) score -= 6
                            score + (v.quality / 100)
                        }.firstOrNull()
                    }

                    if (matchedVoice != null) {
                        engine.voice = matchedVoice
                    }
                } catch (_: Exception) {}

                engine.setPitch(profile.pitch)
                engine.setSpeechRate(profile.speechRate)

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { onStarted?.invoke() }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_activeSpeakingTurnId.value == speechTurnId) {
                            _activeSpeakingTurnId.value = null
                        }
                        mainHandler.post { onDone?.invoke() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (_activeSpeakingTurnId.value == speechTurnId) {
                            _activeSpeakingTurnId.value = null
                        }
                        mainHandler.post { onDone?.invoke() }
                    }
                })

                val queueMode = if (flushExisting) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val result = engine.speak(text, queueMode, null, speechTurnId)
                if (result == TextToSpeech.SUCCESS) {
                    onStarted?.invoke()
                } else {
                    if (_activeSpeakingTurnId.value == speechTurnId) {
                        _activeSpeakingTurnId.value = null
                    }
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Local TTS speak error: ${e.message}")
                if (_activeSpeakingTurnId.value == speechTurnId) {
                    _activeSpeakingTurnId.value = null
                }
                onDone?.invoke()
            }
        }

        val existing = localFallbackTts
        if (existing != null && isLocalTtsReady) {
            doSpeak(existing)
        } else {
            localFallbackTts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isLocalTtsReady = true
                    localFallbackTts?.let { doSpeak(it) }
                } else {
                    if (_activeSpeakingTurnId.value == speechTurnId) {
                        _activeSpeakingTurnId.value = null
                    }
                    mainHandler.post { onDone?.invoke() }
                }
            }
        }
    }

    private fun writePcm16MonoToWav(pcmData: ByteArray, sampleRate: Int, outputFile: File) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalDataLen = dataSize + 36

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        FileOutputStream(outputFile).use { fos ->
            fos.write(header)
            fos.write(pcmData)
            fos.flush()
        }
    }
}
