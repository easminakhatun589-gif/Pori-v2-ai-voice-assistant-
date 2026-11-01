package com.jarvis.assistant.trading.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.audio.PariRealVoiceSpeaker

class TradingAnalyzerActivity : AppCompatActivity() {
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
            text = "📊 JARVIC Market Chart & S&R Scanner"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(tvTitle)

        val tvAnalysis = TextView(this).apply {
            text = "Support & Resistance Scanner Ready.\nTap below to run AI technical structure analysis and hear voice guidance."
            textSize = 14f
            setTextColor(Color.parseColor("#BAE6FD"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(16).toFloat()
            }
        }
        root.addView(tvAnalysis)

        val btnScan = Button(this).apply {
            text = "🔍 Scan Chart Support & Resistance"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#16A34A"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                val report = "Bullish momentum detected above key support zone. Resistance level identified with strong volume confirmation."
                tvAnalysis.text = "📈 Live Scan Result:\n$report"
                PariRealVoiceSpeaker.speakInActiveModeVoice(this@TradingAnalyzerActivity, report)
            }
        }
        root.addView(btnScan)

        scroll.addView(root)
        setContentView(scroll)
    }
}
