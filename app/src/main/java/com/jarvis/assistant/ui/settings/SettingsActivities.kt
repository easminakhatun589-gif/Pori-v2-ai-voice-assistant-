package com.jarvis.assistant.ui.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.audio.PariRealVoiceSpeaker

class PersonaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        val prefs = JarvisApp.instance.preferences

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#EEF2FF"))
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

        val tvHeader = TextView(this).apply {
            text = "👑 JARVIC Persona & Voice Studio (Pari / Jarvis / Kabir / TechGPT X)"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, dp(10), 0, dp(12))
        }
        root.addView(tvHeader)

        val personas = listOf(
            Triple("Pari", "🌸 Pari AI (Caring Girlfriend Companion)", "Aoede"),
            Triple("Jarvis", "👑 JARVIC Pro (Smart Executive Assistant)", "Aoede"),
            Triple("Kabir", "⚡ Kabir AI (Loyal Best Friend Companion)", "Fenrir"),
            Triple("TechGPT", "💡 TechGPT X (Coding & Architecture Specialist)", "Puck")
        )

        for ((id, title, defaultVoice) in personas) {
            val btn = Button(this).apply {
                text = title
                isAllCaps = false
                minHeight = dp(52)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
                setOnClickListener {
                    prefs.personality = id
                    prefs.voice = defaultVoice
                    Toast.makeText(this@PersonaActivity, "Activated $title", Toast.LENGTH_SHORT).show()
                    PariRealVoiceSpeaker.speakInActiveModeVoice(
                        this@PersonaActivity,
                        "হ্যালো! এখন $id মোড চালু হয়েছে।"
                    )
                }
            }
            root.addView(btn)
        }

        scroll.addView(root)
        setContentView(scroll)
    }
}

class VoiceCommandsGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#EEF2FF"))
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

        val tvGuide = TextView(this).apply {
            text = "📖 JARVIC Mobile Voice Control Guide (A to Z)\n\n" +
                "1. অ্যাপ ওপেন করতে বলুন:\n   • 'Open WhatsApp' / 'হোয়াটসঅ্যাপ খোলো'\n   • 'Open YouTube' / 'ইউটিউব খোলো'\n\n" +
                "2. লাইভ ওয়েব সার্চ ও খবর:\n   • 'আজকের তাজা খবর বলো'\n   • 'আজকের আবহাওয়া কেমন?'\n\n" +
                "3. মাল্টি-টাস্ক অটোমেশন:\n   • হোম পেজের 'Queue' বাটনে ট্যাপ করে ৫-স্টেপ অটোমেশন রান করুন।\n\n" +
                "4. একটিভ ক্যামেরা ও ভিশন:\n   • নিচের নেভিগেশন বারে 'Camera' ট্যাবে গিয়ে যেকোনো লেখা বা দৃশ্য স্ক্যান করুন।"
            textSize = 15f
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(tvGuide)

        scroll.addView(root)
        setContentView(scroll)
    }

    companion object {
        fun showMobileControlPopup(context: Context) {
            MaterialAlertDialogBuilder(context)
                .setTitle("JARVIC Voice Control Guide")
                .setMessage(
                    "• Say 'Open WhatsApp / YouTube / Facebook'\n" +
                        "• Say 'Search latest news on Google'\n" +
                        "• Tap or hold the center orb to toggle live voice conversation.\n" +
                        "• Use the 5-tab Bottom Navigation Bar for Home, Chatbox, Live Voice, Active Camera & Settings."
                )
                .setPositiveButton("Got it", null)
                .show()
        }
    }
}

class ApiConnectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        val prefs = JarvisApp.instance.preferences

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(Color.parseColor("#EEF2FF"))
        }

        val btnBack = Button(this).apply {
            text = "← Back"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        root.addView(btnBack)

        val tvTitle = TextView(this).apply {
            text = "🔑 JARVIC API & Cloud Connection Settings"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(tvTitle)

        val etKey = EditText(this).apply {
            hint = "Enter Gemini API Key (AIzaSy...)"
            setText(prefs.apiKey)
        }
        root.addView(etKey)

        val btnSave = Button(this).apply {
            text = "✓ Save API Key"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                prefs.apiKey = etKey.text?.toString()?.trim().orEmpty()
                Toast.makeText(this@ApiConnectionActivity, "API Key Saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        root.addView(btnSave)

        setContentView(root)
    }
}
