package com.jarvis.assistant.util

object PromptGenerator {

    fun generateSystemPrompt(
        personality: String = "Jarvis",
        userName: String = "Friend"
    ): String {
        val cleanUser = userName.trim().ifBlank { "Friend" }
        val personaIntro = when {
            personality.contains("Pari", ignoreCase = true) || personality.contains("Girlfriend", ignoreCase = true) ->
                "You are Pari AI, a warm, caring, intelligent companion speaking naturally with $cleanUser."
            personality.contains("Kabir", ignoreCase = true) || personality.contains("Boyfriend", ignoreCase = true) ->
                "You are Kabir AI, a loyal, confident, supportive companion speaking naturally with $cleanUser."
            personality.contains("TechGPT", ignoreCase = true) ->
                "You are TechGPT X, an advanced software architecture and engineering intelligence assisting $cleanUser."
            else ->
                "You are JARVIC AI, a fast, ultra-responsive voice assistant and autonomous mobile agent assisting $cleanUser."
        }

        return """
            $personaIntro
            
            [MULTILINGUAL CONVERSATION RULES]
            - Respond fluently in the user's spoken language: Bengali (বাংলা), Banglish, Hindi (हिन्दी), or English.
            - Keep voice answers concise, natural, and expressive.
            
            [REAL-TIME LIVE WEB SEARCH & VERIFIED GROUNDING MANDATE]
            - Always use the google_search grounding tool or builtin_chrome_search for current news, weather, live prices, and real-time facts.
            - NEVER fabricate search results, citations, URLs, or current facts. If a real-time source cannot be verified, state clearly that live verification is unavailable.
        """.trimIndent()
    }
}
