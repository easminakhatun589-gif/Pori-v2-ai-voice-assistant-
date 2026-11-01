package com.jarvis.assistant.ui.orb

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.model.ChatTurn
import com.jarvis.assistant.data.model.ConversationState
import org.json.JSONArray
import org.json.JSONObject

class OrbHelper(
    private val webView: WebView,
    private val listener: OrbListener? = null
) {

    interface OrbListener {
        fun onOrbClicked()
        fun onMicClicked()
        fun onMicHold() {
            onMicClicked()
        }
        fun onPowerClicked()
        fun onSettingsClicked()
        fun onHistoryClicked()
        fun onScreenShareClicked()
    }

    private var isLoaded = false
    private var pendingState: ConversationState? = null
    private var pendingAudioLevel: Float? = null
    private var pendingTime: String? = null
    private var pendingStatus: String? = null
    private var pendingPower: Boolean? = null
    private var pendingMicMuted: Boolean? = null
    private var pendingTurns: List<ChatTurn>? = null
    private var pendingScreenShare: Boolean? = null
    private var pendingBatteryPercent: Int? = null
    private var pendingBatteryCharging: Boolean? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private val serviceBatteryListener: (Int, Boolean) -> Unit = { percent, isCharging ->
        updateBatteryState(percent, isCharging)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun setup() {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.isHapticFeedbackEnabled = true

        // Add JavaScript Interface for Native-Web Bridge with distinct, punchy tactile haptics
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onOrbClicked() {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerOrbPress(webView)
                    listener?.onOrbClicked()
                }
            }

            @JavascriptInterface
            fun onMicClicked() {
                webView.post {
                    val willActivate = !com.jarvis.assistant.background.JarvisConversationService.isSessionOn.value ||
                        com.jarvis.assistant.JarvisApp.instance.preferences.isMicMuted
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerMicActivation(
                        activated = willActivate,
                        view = webView
                    )
                    listener?.onMicClicked()
                }
            }

            @JavascriptInterface
            fun onMicHold() {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerMicActivation(
                        activated = true,
                        view = webView
                    )
                    listener?.onMicHold()
                }
            }

            @JavascriptInterface
            fun onPowerClicked() {
                webView.post {
                    val willPowerOn = !com.jarvis.assistant.background.JarvisConversationService.isSessionOn.value
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerPowerToggle(
                        isPowerOn = willPowerOn,
                        view = webView
                    )
                    listener?.onPowerClicked()
                }
            }

            @JavascriptInterface
            fun onSettingsClicked() {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerButtonTap(webView)
                    listener?.onSettingsClicked()
                }
            }

            @JavascriptInterface
            fun onHistoryClicked() {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerButtonTap(webView)
                    listener?.onHistoryClicked()
                }
            }

            @JavascriptInterface
            fun onScreenShareClicked() {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerButtonTap(webView)
                    listener?.onScreenShareClicked()
                }
            }

            @JavascriptInterface
            fun onSubtleTap() {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerSubtleTick(webView)
                }
            }

            @JavascriptInterface
            fun triggerHapticFeedback(type: String?) {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerByType(type, view = webView)
                }
            }

            @JavascriptInterface
            fun triggerHapticFeedback() {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerButtonTap(webView)
                }
            }

            @JavascriptInterface
            fun performHapticFeedback(type: String?) {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerByType(type, view = webView)
                }
            }

            @JavascriptInterface
            fun performHapticFeedback() {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackManager.triggerButtonTap(webView)
                }
            }

            @JavascriptInterface
            fun vibrate(durationMs: Int) {
                webView.post {
                    com.jarvis.assistant.util.HapticFeedbackHelper.vibrateDuration(
                        webView.context,
                        durationMs.toLong()
                    )
                }
            }
        }, "JarvisBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoaded = true

                pendingTime?.let { updateLiveTime(it) }
                pendingStatus?.let { updateConnectionStatus(it) }
                pendingPower?.let { updatePowerState(it) }
                pendingMicMuted?.let { updateMicMuted(it) }
                pendingScreenShare?.let { updateScreenShareState(it) }
                val latestBattery = com.jarvis.assistant.background.BatteryMonitorService.batteryState.value
                val initialPct = pendingBatteryPercent ?: latestBattery.percent
                val initialCharging = pendingBatteryCharging ?: latestBattery.isCharging
                updateBatteryState(initialPct, initialCharging)
                com.jarvis.assistant.background.BatteryMonitorService.registerHudWebView(webView)
                pendingState?.let { updateState(it, pendingAudioLevel ?: 0f) }
                pendingTurns?.let { updateChatTurns(it) }

                pendingTime = null
                pendingStatus = null
                pendingPower = null
                pendingMicMuted = null
                pendingScreenShare = null
                pendingBatteryPercent = null
                pendingBatteryCharging = null
                pendingState = null
                pendingAudioLevel = null
                pendingTurns = null
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                android.util.Log.d("JarvisHudWebView", "${message?.message()} -- line ${message?.lineNumber()}")
                return true
            }
        }

        setupBatteryMonitoring()
        webView.loadUrl("file:///android_asset/hud/index.html")
    }

    fun updateState(state: ConversationState, audioLevel: Float = 0f) {
        if (!isLoaded) {
            pendingState = state
            pendingAudioLevel = audioLevel
            return
        }
        val orbKey = state.orbKey
        val level = audioLevel.coerceIn(0f, 1f)
        val js = "javascript:if(window.setOrbState){window.setOrbState('$orbKey', $level);}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun updateAudioLevel(level: Float) {
        if (!isLoaded) return
        val clamped = level.coerceIn(0f, 1f)
        val js = "javascript:if(window.setAudioLevel){window.setAudioLevel($clamped);}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun updateAudioFrequency(amp: Float, pitchHz: Float, bass: Float, mid: Float, high: Float) {
        if (!isLoaded) return
        val clampedAmp = amp.coerceIn(0f, 1f)
        val clampedPitch = pitchHz.coerceIn(0f, 4000f)
        val clampedBass = bass.coerceIn(0f, 1f)
        val clampedMid = mid.coerceIn(0f, 1f)
        val clampedHigh = high.coerceIn(0f, 1f)
        val js = "javascript:if(window.setAudioFrequency){window.setAudioFrequency($clampedAmp,$clampedPitch,$clampedBass,$clampedMid,$clampedHigh);}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun updateScreenShareState(isSharing: Boolean) {
        if (!isLoaded) {
            pendingScreenShare = isSharing
            return
        }
        val js = "javascript:if(window.setScreenShareState){window.setScreenShareState($isSharing);}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun updateLiveTime(time: String) {
        if (!isLoaded) {
            pendingTime = time
            return
        }
        val safeTime = time.replace("'", "\\'")
        val js = "javascript:if(window.setLiveTime){window.setLiveTime('$safeTime');}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun updateConnectionStatus(status: String) {
        if (!isLoaded) {
            pendingStatus = status
            return
        }
        val safeStatus = status.replace("'", "\\'")
        val js = "javascript:if(window.setConnectionStatus){window.setConnectionStatus('$safeStatus');}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun updatePowerState(isOn: Boolean) {
        if (!isLoaded) {
            pendingPower = isOn
            return
        }
        val js = "javascript:if(window.setPowerState){window.setPowerState($isOn);}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun updateMicMuted(isMuted: Boolean) {
        if (!isLoaded) {
            pendingMicMuted = isMuted
            return
        }
        val js = "javascript:if(window.setMicMuted){window.setMicMuted($isMuted);}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun updateChatTurns(turns: List<ChatTurn>) {
        if (!isLoaded) {
            pendingTurns = turns
            return
        }
        try {
            val jsonArray = JSONArray()
            for (turn in turns) {
                val obj = JSONObject()
                obj.put("userTranscript", turn.userTranscript)
                obj.put("jarvisResponse", turn.jarvisResponse)
                obj.put("formattedTime", turn.formattedTime)
                jsonArray.put(obj)
            }
            val rawJson = jsonArray.toString()
            // Escape for embedding in single-quoted JS literal
            val safeJson = rawJson.replace("\\", "\\\\").replace("'", "\\'")
            val js = "javascript:if(window.syncChatTurns){window.syncChatTurns('$safeJson');}"
            webView.post {
                webView.evaluateJavascript(js, null)
            }
        } catch (e: Exception) {
            android.util.Log.e("OrbHelper", "Failed to update chat turns in HUD", e)
        }
    }

    fun updateLiveUserTranscript(text: String) {
        if (!isLoaded) return
        val quoted = JSONObject.quote(text)
        val js = "javascript:if(window.updateLiveUserTranscript){window.updateLiveUserTranscript($quoted);}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun updateLiveAssistantResponse(text: String) {
        if (!isLoaded) return
        val quoted = JSONObject.quote(text)
        val js = "javascript:if(window.updateLiveAssistantResponse){window.updateLiveAssistantResponse($quoted);}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun finalizeLiveTurn() {
        if (!isLoaded) return
        val js = "javascript:if(window.finalizeLiveTurn){window.finalizeLiveTurn();}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    private fun setupBatteryMonitoring() {
        try {
            val context = webView.context
            com.jarvis.assistant.background.BatteryMonitorService.start(context)
            com.jarvis.assistant.background.BatteryMonitorService.registerHudWebView(webView)
            com.jarvis.assistant.background.BatteryMonitorService.addBatteryListener(serviceBatteryListener)

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(null, filter)
            stickyIntent?.let { handleBatteryIntent(it) }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    intent?.let { handleBatteryIntent(it) }
                }
            }
            batteryReceiver = receiver
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            android.util.Log.e("OrbHelper", "Failed to register battery receiver", e)
        }
    }

    private fun handleBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        if (level >= 0 && scale > 0) {
            val percent = (level * 100f / scale).toInt()
            updateBatteryState(percent, isCharging)
        }
    }

    fun updateBatteryState(percent: Int, isCharging: Boolean) {
        if (!isLoaded) {
            pendingBatteryPercent = percent
            pendingBatteryCharging = isCharging
            return
        }
        val clamped = percent.coerceIn(0, 100)
        val js = "javascript:if(window.setBatteryState){window.setBatteryState($clamped, $isCharging);}"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun destroy() {
        com.jarvis.assistant.background.BatteryMonitorService.removeBatteryListener(serviceBatteryListener)
        com.jarvis.assistant.background.BatteryMonitorService.unregisterHudWebView(webView)
        batteryReceiver?.let {
            try {
                webView.context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
            batteryReceiver = null
        }
    }
}
