package com.jarvis.assistant.builder

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.audio.PariRealVoiceSpeaker

class VoiceBuilderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(28))
        }

        val btnBack = Button(this).apply {
            text = "← Back"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        root.addView(btnBack)

        val tvTitle = TextView(this).apply {
            text = "🛠️ JARVIC Voice Builder & Code Studio"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(tvTitle)

        val etPrompt = EditText(this).apply {
            hint = "Describe what code or app module to generate..."
            setText("Create a responsive Kotlin coroutine helper for network retry")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
        }
        root.addView(etPrompt)

        val tvCodeOutput = TextView(this).apply {
            text = "// Generated code will appear here..."
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#A7F3D0"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(14).toFloat()
            }
        }

        val btnGenerate = Button(this).apply {
            text = "⚡ Generate Code with AI"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                val prompt = etPrompt.text?.toString()?.trim().orEmpty()
                tvCodeOutput.text = """
                    // Generated for: $prompt
                    suspend fun <T> retryWithBackoff(
                        times: Int = 3,
                        initialDelayMs: Long = 500L,
                        block: suspend () -> T
                    ): T {
                        var currentDelay = initialDelayMs
                        repeat(times - 1) {
                            try {
                                return block()
                            } catch (_: Exception) {
                                kotlinx.coroutines.delay(currentDelay)
                                currentDelay *= 2
                            }
                        }
                        return block()
                    }
                """.trimIndent()
                PariRealVoiceSpeaker.speakInActiveModeVoice(
                    this@VoiceBuilderActivity,
                    "আপনার কোড তৈরি করা হয়েছে।"
                )
            }
        }
        root.addView(btnGenerate)
        root.addView(tvCodeOutput)

        scroll.addView(root)
        setContentView(scroll)
    }
}
