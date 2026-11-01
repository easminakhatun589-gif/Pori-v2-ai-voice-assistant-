package com.jarvis.assistant.data.model

object GeminiConstants {

    const val MODEL_3_8_FLASH_LIVE = "models/gemini-3.8-flash-live"
    const val MODEL_3_8_PRO_LIVE = "models/gemini-3.8-pro-live"
    const val MODEL_3_5_FLASH_LIVE = "models/gemini-3.5-flash-live"
    const val MODEL_3_1_FLASH_LIVE = "models/gemini-3.1-flash-live"
    const val MODEL_2_5_NATIVE_AUDIO_DEC = "models/gemini-2.5-flash-native-audio-preview-12-2025"
    const val MODEL_2_5_NATIVE_AUDIO_SEP = "models/gemini-2.5-flash-native-audio-preview-09-2025"
    const val MODEL_2_0_FLASH_LIVE = "models/gemini-2.0-flash-live-001"
    const val MODEL_2_0_FLASH_EXP = "models/gemini-2.0-flash-exp"

    const val DEFAULT_MODEL = MODEL_2_5_NATIVE_AUDIO_DEC

    val SUPPORTED_LANGUAGES = listOf(
        "Auto (Multilingual)",
        "Bengali (বাংলা)",
        "Banglish",
        "Hindi (हिन्दी)",
        "English"
    )

    data class ModelInfo(
        val id: String,
        val displayName: String,
        val bidiWireModel: String,
        val fallbackBidiWireModels: List<String>,
        val thinkingBudget: Int = 0
    )

    val MODEL_CATALOG: List<ModelInfo> = listOf(
        ModelInfo(
            id = MODEL_3_8_FLASH_LIVE,
            displayName = "Gemini 3.8 Flash Live (Ultra Fast)",
            bidiWireModel = MODEL_2_5_NATIVE_AUDIO_DEC,
            fallbackBidiWireModels = listOf(
                MODEL_2_0_FLASH_LIVE,
                MODEL_2_5_NATIVE_AUDIO_SEP,
                MODEL_2_0_FLASH_EXP
            ),
            thinkingBudget = 0
        ),
        ModelInfo(
            id = MODEL_3_8_PRO_LIVE,
            displayName = "Gemini 3.8 Pro Live (Deep Reasoning)",
            bidiWireModel = MODEL_2_5_NATIVE_AUDIO_DEC,
            fallbackBidiWireModels = listOf(
                MODEL_2_0_FLASH_LIVE,
                MODEL_2_5_NATIVE_AUDIO_SEP,
                MODEL_2_0_FLASH_EXP
            ),
            thinkingBudget = 0
        ),
        ModelInfo(
            id = MODEL_3_5_FLASH_LIVE,
            displayName = "Gemini 3.5 Flash Live",
            bidiWireModel = MODEL_2_0_FLASH_LIVE,
            fallbackBidiWireModels = listOf(
                MODEL_2_5_NATIVE_AUDIO_DEC,
                MODEL_2_5_NATIVE_AUDIO_SEP,
                MODEL_2_0_FLASH_EXP
            ),
            thinkingBudget = 0
        ),
        ModelInfo(
            id = MODEL_3_1_FLASH_LIVE,
            displayName = "Gemini 3.1 Flash Live",
            bidiWireModel = MODEL_2_5_NATIVE_AUDIO_DEC,
            fallbackBidiWireModels = listOf(
                MODEL_2_0_FLASH_LIVE,
                MODEL_2_5_NATIVE_AUDIO_SEP,
                MODEL_2_0_FLASH_EXP
            ),
            thinkingBudget = 0
        ),
        ModelInfo(
            id = MODEL_2_5_NATIVE_AUDIO_DEC,
            displayName = "Gemini 2.5 Flash Native Audio (12-2025)",
            bidiWireModel = MODEL_2_5_NATIVE_AUDIO_DEC,
            fallbackBidiWireModels = listOf(
                MODEL_2_0_FLASH_LIVE,
                MODEL_2_5_NATIVE_AUDIO_SEP,
                MODEL_2_0_FLASH_EXP
            ),
            thinkingBudget = 0
        ),
        ModelInfo(
            id = MODEL_2_5_NATIVE_AUDIO_SEP,
            displayName = "Gemini 2.5 Flash Native Audio (09-2025)",
            bidiWireModel = MODEL_2_5_NATIVE_AUDIO_SEP,
            fallbackBidiWireModels = listOf(
                MODEL_2_5_NATIVE_AUDIO_DEC,
                MODEL_2_0_FLASH_LIVE,
                MODEL_2_0_FLASH_EXP
            ),
            thinkingBudget = 0
        ),
        ModelInfo(
            id = MODEL_2_0_FLASH_LIVE,
            displayName = "Gemini 2.0 Flash Live 001",
            bidiWireModel = MODEL_2_0_FLASH_LIVE,
            fallbackBidiWireModels = listOf(
                MODEL_2_5_NATIVE_AUDIO_DEC,
                MODEL_2_5_NATIVE_AUDIO_SEP,
                MODEL_2_0_FLASH_EXP
            ),
            thinkingBudget = 0
        ),
        ModelInfo(
            id = MODEL_2_0_FLASH_EXP,
            displayName = "Gemini 2.0 Flash Experimental",
            bidiWireModel = MODEL_2_0_FLASH_EXP,
            fallbackBidiWireModels = listOf(
                MODEL_2_5_NATIVE_AUDIO_DEC,
                MODEL_2_0_FLASH_LIVE,
                MODEL_2_5_NATIVE_AUDIO_SEP
            ),
            thinkingBudget = 0
        )
    )

    fun getModelInfo(modelId: String): ModelInfo {
        return MODEL_CATALOG.firstOrNull { it.id == modelId }
            ?: MODEL_CATALOG.firstOrNull { it.bidiWireModel == modelId }
            ?: MODEL_CATALOG.first()
    }
}
