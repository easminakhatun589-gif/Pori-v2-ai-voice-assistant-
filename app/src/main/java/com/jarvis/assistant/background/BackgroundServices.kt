package com.jarvis.assistant.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.audio.PariRealVoiceSpeaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class JarvisConversationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val _isSessionOn = MutableStateFlow(false)
        val isSessionOn: StateFlow<Boolean> = _isSessionOn.asStateFlow()

        private val _isStandby = MutableStateFlow(false)
        val isStandby: StateFlow<Boolean> = _isStandby.asStateFlow()

        @Volatile
        private var streamingLiveAudio: Boolean = false

        fun isStreamingLiveAudio(): Boolean = streamingLiveAudio

        fun setSessionActive(active: Boolean) {
            _isSessionOn.value = active
        }

        fun resolveLocaleForSpeech(text: String, preferredLanguage: String): Locale {
            val prefLower = preferredLanguage.lowercase(Locale.ROOT)
            if (prefLower.contains("bengali") || prefLower.contains("bangla") || prefLower.contains("বাংলা")) {
                return Locale("bn", "BD")
            }
            if (prefLower.contains("hindi") || prefLower.contains("हिन्दी")) {
                return Locale("hi", "IN")
            }
            val hasBengaliScript = text.any { it in '\u0980'..'\u09FF' }
            if (hasBengaliScript) return Locale("bn", "BD")
            val hasDevanagariScript = text.any { it in '\u0900'..'\u097F' }
            if (hasDevanagariScript) return Locale("hi", "IN")
            return Locale.US
        }

        fun speakText(text: String) {
            try {
                PariRealVoiceSpeaker.speakInActiveModeVoice(
                    context = JarvisApp.instance,
                    text = text
                )
            } catch (_: Exception) {
            }
        }
    }
}

class BatteryMonitorService : Service() {

    data class BatteryState(
        val percent: Int = 100,
        val isCharging: Boolean = false
    )

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val _batteryState = MutableStateFlow(BatteryState(percent = 100, isCharging = false))
        val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

        fun start(context: Context) {
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100
                val charging = bm?.isCharging ?: false
                _batteryState.value = BatteryState(percent = pct, isCharging = charging)
            } catch (_: Exception) {
            }
        }
    }
}
