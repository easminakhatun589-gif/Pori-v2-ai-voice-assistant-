package com.jarvis.assistant.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object HapticFeedbackHelper {

    fun performControlClick(view: View?) {
        HapticFeedbackManager.triggerButtonClick(view)
    }

    fun performCorePulse(view: View?) {
        HapticFeedbackManager.triggerSuccessConfirm(view)
    }
}

object HapticFeedbackManager {

    const val EVENT_BUTTON_CLICK = "BUTTON_CLICK"
    const val EVENT_NAV_TAB_SELECT = "NAV_TAB_SELECT"
    const val EVENT_ORB_PRESS = "ORB_PRESS"
    const val EVENT_VOICE_START = "VOICE_START"
    const val EVENT_VOICE_STOP = "VOICE_STOP"
    const val EVENT_SUCCESS_CONFIRM = "SUCCESS_CONFIRM"
    const val EVENT_SUBTLE_TICK = "SUBTLE_TICK"

    private val _lastHapticEvent = MutableStateFlow("")
    val lastHapticEvent: StateFlow<String> = _lastHapticEvent.asStateFlow()

    private val _hapticTriggerCount = MutableStateFlow(0)
    val hapticTriggerCount: StateFlow<Int> = _hapticTriggerCount.asStateFlow()

    private fun recordEvent(eventName: String) {
        _lastHapticEvent.value = eventName
        _hapticTriggerCount.value = _hapticTriggerCount.value + 1
    }

    private fun resolveVibrator(context: Context?): Vibrator? {
        val ctx = context ?: try { JarvisApp.instance } catch (_: Exception) { null } ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun vibrateOneShot(context: Context?, durationMs: Long, amplitude: Int) {
        try {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs.coerceAtLeast(1L),
                        amplitude.coerceIn(1, 255)
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs.coerceAtLeast(1L))
            }
        } catch (_: Exception) {
        }
    }

    private fun vibrateWaveform(context: Context?, timings: LongArray, amplitudes: IntArray) {
        try {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vibrator.hasAmplitudeControl()) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (_: Exception) {
        }
    }

    private fun performViewHaptic(view: View?, feedbackConstant: Int) {
        try {
            view?.isHapticFeedbackEnabled = true
            view?.performHapticFeedback(
                feedbackConstant,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Exception) {
        }
    }

    fun triggerButtonClick(view: View?, context: Context? = view?.context) {
        recordEvent(EVENT_BUTTON_CLICK)
        performViewHaptic(view, HapticFeedbackConstants.VIRTUAL_KEY)
        vibrateOneShot(context, durationMs = 14L, amplitude = 110)
    }

    fun triggerNavTabSelect(view: View?, context: Context? = view?.context) {
        recordEvent(EVENT_NAV_TAB_SELECT)
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            HapticFeedbackConstants.CONTEXT_CLICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        performViewHaptic(view, constant)
        vibrateOneShot(context, durationMs = 12L, amplitude = 95)
    }

    fun triggerOrbPress(view: View?, context: Context? = view?.context) {
        recordEvent(EVENT_ORB_PRESS)
        performViewHaptic(view, HapticFeedbackConstants.VIRTUAL_KEY)
        vibrateOneShot(context, durationMs = 20L, amplitude = 165)
    }

    fun triggerVoiceStart(context: Context? = null, view: View? = null) {
        recordEvent(EVENT_VOICE_START)
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        performViewHaptic(view, constant)
        // Rising two-step tactile pulse indicating live voice assistant activation
        vibrateWaveform(
            context = context ?: view?.context,
            timings = longArrayOf(0L, 18L, 42L, 32L),
            amplitudes = intArrayOf(0, 140, 0, 255)
        )
    }

    fun triggerVoiceStop(context: Context? = null, view: View? = null) {
        recordEvent(EVENT_VOICE_STOP)
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        performViewHaptic(view, constant)
        // Descending two-step tactile pulse indicating live voice assistant stop / mute
        vibrateWaveform(
            context = context ?: view?.context,
            timings = longArrayOf(0L, 28L, 48L, 16L),
            amplitudes = intArrayOf(0, 230, 0, 110)
        )
    }

    fun triggerPowerToggle(isPowerOn: Boolean, view: View?, context: Context? = view?.context) {
        if (isPowerOn) {
            triggerVoiceStart(context = context, view = view)
        } else {
            triggerVoiceStop(context = context, view = view)
        }
    }

    fun triggerSuccessConfirm(view: View?, context: Context? = view?.context) {
        recordEvent(EVENT_SUCCESS_CONFIRM)
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        performViewHaptic(view, constant)
        vibrateOneShot(context, durationMs = 24L, amplitude = 190)
    }

    fun triggerSubtleTick(view: View?, context: Context? = view?.context) {
        recordEvent(EVENT_SUBTLE_TICK)
        performViewHaptic(view, HapticFeedbackConstants.CLOCK_TICK)
        vibrateOneShot(context, durationMs = 8L, amplitude = 70)
    }
}

object PermissionHelper {
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

object LocationWeatherHelper {

    data class CountryIdentity(
        val countryName: String,
        val countryCode: String
    )

    data class WeatherData(
        val temperature: String,
        val condition: String,
        val cityName: String,
        val countryName: String,
        val countryCode: String,
        val details: String,
        val iconResId: Int
    )

    suspend fun fetchDeviceCountryIdentity(context: Context): CountryIdentity? {
        val locale = Locale.getDefault()
        val code = locale.country.ifBlank { "BD" }
        val name = locale.displayCountry.ifBlank { "Bangladesh" }
        return CountryIdentity(countryName = name, countryCode = code)
    }

    suspend fun fetchWeather(context: Context): WeatherData? {
        if (!PermissionHelper.hasLocationPermission(context)) {
            return null
        }
        val prefs = try {
            (context.applicationContext as? JarvisApp)?.preferences ?: JarvisApp.instance.preferences
        } catch (_: Exception) {
            null
        }
        val city = prefs?.userLocation?.takeIf { it.isNotBlank() } ?: "Dhaka"
        val country = prefs?.userCountryName?.takeIf { it.isNotBlank() } ?: "Bangladesh"
        val code = prefs?.userCountryCode?.takeIf { it.isNotBlank() } ?: "BD"
        return WeatherData(
            temperature = "28°C",
            condition = "Clear Sky",
            cityName = city,
            countryName = country,
            countryCode = code,
            details = "Humidity 62% • Wind 11 km/h",
            iconResId = R.drawable.ic_angel_weather_sun
        )
    }
}

object CountryFlagHelper {
    fun normalizeCountry(name: String, code: String): Pair<String, String> {
        val cleanName = name.trim()
        val cleanCode = code.trim().uppercase(Locale.ROOT)
        if (cleanName.isBlank() && cleanCode.isBlank()) {
            return "" to ""
        }
        val resolvedName = cleanName.ifBlank {
            when (cleanCode) {
                "BD" -> "Bangladesh"
                "IN" -> "India"
                "US" -> "United States"
                else -> cleanCode
            }
        }
        val resolvedCode = cleanCode.ifBlank {
            when (cleanName.lowercase(Locale.ROOT)) {
                "bangladesh" -> "BD"
                "india" -> "IN"
                else -> cleanName.take(2).uppercase(Locale.ROOT)
            }
        }
        return resolvedName to resolvedCode
    }

    fun createCountryFlagDrawable(
        context: Context,
        countryCodeOrName: String,
        widthDp: Int,
        heightDp: Int
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).toInt().coerceAtLeast(16)
        val h = (heightDp * density).toInt().coerceAtLeast(12)
        val code = countryCodeOrName.trim().uppercase(Locale.ROOT)

        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun getIntrinsicWidth(): Int = w
            override fun getIntrinsicHeight(): Int = h

            override fun draw(canvas: Canvas) {
                val b = bounds
                val rect = RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                if (code == "BD" || code.contains("BANGLA")) {
                    paint.color = Color.parseColor("#006A4E")
                    canvas.drawRoundRect(rect, 4f, 4f, paint)
                    paint.color = Color.parseColor("#F42A41")
                    canvas.drawCircle(rect.centerX() - rect.width() * 0.05f, rect.centerY(), rect.height() * 0.3f, paint)
                } else {
                    paint.color = Color.parseColor("#2563EB")
                    canvas.drawRoundRect(rect, 4f, 4f, paint)
                }
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                paint.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }
}

object UserProfileDialogHelper {
    fun showProfileDialog(
        context: Context,
        onRequestLocationPermission: () -> Unit,
        onRefreshLocation: () -> Unit,
        onOpenFullSettings: () -> Unit,
        onProfileUpdated: () -> Unit
    ) {
        val prefs = try {
            (context.applicationContext as? JarvisApp)?.preferences ?: JarvisApp.instance.preferences
        } catch (_: Exception) {
            null
        }
        val input = EditText(context).apply {
            hint = "Your Name"
            setText(prefs?.userName.orEmpty())
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("User Profile & Location")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                HapticFeedbackManager.triggerSuccessConfirm(null, context)
                prefs?.userName = input.text?.toString()?.trim().orEmpty()
                onProfileUpdated()
            }
            .setNeutralButton("Settings") { _, _ ->
                HapticFeedbackManager.triggerButtonClick(null, context)
                onOpenFullSettings()
            }
            .setNegativeButton("Refresh Location") { _, _ ->
                HapticFeedbackManager.triggerButtonClick(null, context)
                if (!PermissionHelper.hasLocationPermission(context)) {
                    onRequestLocationPermission()
                } else {
                    onRefreshLocation()
                }
            }
            .show()
    }
}
