package com.jarvis.assistant.data.repository

import java.util.Locale

object UserAdminRepository {

    private val ownerEmails = setOf(
        "mdaslamlaskar14@gmail.com"
    )

    fun isOwnerEmail(email: String?): Boolean {
        val clean = email?.trim()?.lowercase(Locale.ROOT) ?: return false
        return clean in ownerEmails
    }
}
