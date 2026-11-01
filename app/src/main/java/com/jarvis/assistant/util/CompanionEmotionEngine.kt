package com.jarvis.assistant.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object CompanionEmotionEngine {

    enum class CompanionEmotion {
        NEUTRAL,
        HAPPY,
        CARING,
        EXCITED,
        CALM
    }

    private val _currentEmotion = MutableStateFlow(CompanionEmotion.CARING)
    val currentEmotion: StateFlow<CompanionEmotion> = _currentEmotion.asStateFlow()

    fun isCompanionPersona(personality: String): Boolean {
        val lower = personality.lowercase(Locale.ROOT)
        return lower.contains("pari") ||
            lower.contains("girlfriend") ||
            lower.contains("kabir") ||
            lower.contains("boyfriend") ||
            lower.contains("companion")
    }

    fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("\\*+"), "")
            .replace(Regex("`+"), "")
            .replace(Regex("#+\\s*"), "")
            .trim()
    }
}
