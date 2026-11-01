package com.jarvis.assistant.data.model

enum class ConversationState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class CitationSource(
    val title: String,
    val url: String
)

data class ChatTurn(
    val id: String,
    val userTranscript: String,
    val jarvisResponse: String,
    val timestamp: Long,
    val turnSource: String = "voice",
    val isLiveWebData: Boolean = false,
    val citations: List<CitationSource> = emptyList()
)

data class AudioFrequencyState(
    val amplitude: Float = 0f,
    val bass: Float = 0f,
    val high: Float = 0f
)

data class LiveErrorState(
    val title: String,
    val message: String,
    val actionText: String = "RETRY",
    val errorCode: String = "GENERAL"
)
