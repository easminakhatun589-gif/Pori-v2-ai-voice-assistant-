package com.jarvis.assistant.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SocialAutomationEngine {

    enum class SocialPlatform(val displayName: String) {
        FACEBOOK("Facebook"),
        INSTAGRAM("Instagram"),
        WHATSAPP("WhatsApp")
    }

    enum class PendingSocialActionType {
        PUBLISH_COMMENT,
        PUBLISH_POST,
        DELETE_OWN_POST,
        SEND_MESSAGE
    }

    data class PendingSocialApproval(
        val id: String,
        val platform: SocialPlatform,
        val actionType: PendingSocialActionType,
        val summaryForUser: String,
        val proposedTextOrCaption: String
    )

    private val _pendingApproval = MutableStateFlow<PendingSocialApproval?>(null)
    val pendingApproval: StateFlow<PendingSocialApproval?> = _pendingApproval.asStateFlow()
}
