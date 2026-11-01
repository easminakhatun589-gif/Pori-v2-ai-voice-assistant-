package com.jarvis.assistant.data.preferences

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jarvis_preferences", Context.MODE_PRIVATE)

    var personality: String
        get() = prefs.getString("personality", "Jarvis") ?: "Jarvis"
        set(value) = prefs.edit().putString("personality", value).apply()

    var voice: String
        get() = prefs.getString("voice", "Aoede") ?: "Aoede"
        set(value) = prefs.edit().putString("voice", value).apply()

    var personaVoiceTone: String
        get() = prefs.getString("persona_voice_tone", "Soft") ?: "Soft"
        set(value) = prefs.edit().putString("persona_voice_tone", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var language: String
        get() = prefs.getString("language", "Auto") ?: "Auto"
        set(value) = prefs.edit().putString("language", value).apply()

    var taskOverlayMode: String
        get() = prefs.getString("task_overlay_mode", "active") ?: "active"
        set(value) = prefs.edit().putString("task_overlay_mode", value).apply()

    var isTaskOverlayEnabled: Boolean
        get() = prefs.getBoolean("is_task_overlay_enabled", true)
        set(value) = prefs.edit().putBoolean("is_task_overlay_enabled", value).apply()

    var taskOverlayAvatar: String
        get() = prefs.getString("task_overlay_avatar", "default") ?: "default"
        set(value) = prefs.edit().putString("task_overlay_avatar", value).apply()

    var taskOverlayFreePosition: Boolean
        get() = prefs.getBoolean("task_overlay_free_position", false)
        set(value) = prefs.edit().putBoolean("task_overlay_free_position", value).apply()

    var taskOverlayX: Int
        get() = prefs.getInt("task_overlay_x", -1)
        set(value) = prefs.edit().putInt("task_overlay_x", value).apply()

    var taskOverlayY: Int
        get() = prefs.getInt("task_overlay_y", -1)
        set(value) = prefs.edit().putInt("task_overlay_y", value).apply()

    var taskOverlayEdge: String
        get() = prefs.getString("task_overlay_edge", "right") ?: "right"
        set(value) = prefs.edit().putString("task_overlay_edge", value).apply()

    var appTheme: String
        get() = prefs.getString("app_theme", "cyber_blue") ?: "cyber_blue"
        set(value) = prefs.edit().putString("app_theme", value).apply()

    var customThemeColor: String
        get() = prefs.getString("custom_theme_color", "#38BDF8") ?: "#38BDF8"
        set(value) = prefs.edit().putString("custom_theme_color", value).apply()

    var userLocation: String
        get() = prefs.getString("user_location", "") ?: ""
        set(value) = prefs.edit().putString("user_location", value).apply()

    var userCountryName: String
        get() = prefs.getString("user_country_name", "") ?: ""
        set(value) = prefs.edit().putString("user_country_name", value).apply()

    var userCountryCode: String
        get() = prefs.getString("user_country_code", "") ?: ""
        set(value) = prefs.edit().putString("user_country_code", value).apply()

    var isAdminModeActive: Boolean
        get() = prefs.getBoolean("is_admin_mode_active", false)
        set(value) = prefs.edit().putBoolean("is_admin_mode_active", value).apply()

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) = prefs.edit().putString("user_name", value).apply()
}
