package com.jarvis.assistant.alarm.voice

import android.speech.tts.TextToSpeech
import java.util.Locale

object AlarmTemplates {
    fun configureAndroidTtsVoiceAndLocale(
        tts: TextToSpeech,
        language: String,
        voiceId: String,
        voiceSpeed: Float
    ) {
        val locale = when (language.lowercase(Locale.ROOT)) {
            "bangla", "bengali" -> Locale("bn", "BD")
            "hindi" -> Locale("hi", "IN")
            else -> Locale.US
        }
        try {
            tts.language = locale
            tts.setSpeechRate(voiceSpeed)
        } catch (_: Exception) {
        }
    }
}
