package com.jarvis.assistant.ui.memory

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
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.audio.PariRealVoiceSpeaker

class MemoryActivity : AppCompatActivity() {

    private lateinit var memoriesListLayout: LinearLayout
    private val prefs by lazy { getSharedPreferences("jarvis_memories_store", Context.MODE_PRIVATE) }

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

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(18).toFloat()
            }
        }

        val btnBack = Button(this).apply {
            text = "← Back"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { finish() }
        }

        val tvTitle = TextView(this).apply {
            text = "🧠 JARVIC Long-Term Memory Core"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, 0, 0)
        }

        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        root.addView(topBar)

        val addCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#CBD5E1"))
            }
        }

        val etMemory = EditText(this).apply {
            hint = "নতুন মেমোরি বা নোট লিখুন (e.g., আমার প্রিয় ভাষা বাংলা)"
            textSize = 14f
        }

        val btnSave = Button(this).apply {
            text = "＋ Save to Memory Core"
            isAllCaps = false
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                val text = etMemory.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    etMemory.setText("")
                    saveMemoryItem(text)
                    renderMemories()
                }
            }
        }

        addCard.addView(etMemory)
        addCard.addView(btnSave)
        root.addView(addCard)

        memoriesListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }
        root.addView(memoriesListLayout)

        scroll.addView(root)
        setContentView(scroll)

        if (getMemoriesList().isEmpty()) {
            saveMemoryItem("🌸 পছন্দের মোড: Pari AI Real Voice Companion")
            saveMemoryItem("🇧🇩 ভাষা: বাংলা, বাংলিশ ও ইংরেজি")
        }
        renderMemories()
    }

    private fun getMemoriesList(): MutableList<String> {
        val raw = prefs.getString("saved_memories_pipe", "").orEmpty()
        return if (raw.isBlank()) mutableListOf() else raw.split("||").filter { it.isNotBlank() }.toMutableList()
    }

    private fun saveMemoryItem(item: String) {
        val current = getMemoriesList()
        current.add(0, item)
        prefs.edit().putString("saved_memories_pipe", current.joinToString("||")).apply()
    }

    private fun deleteMemoryAt(index: Int) {
        val current = getMemoriesList()
        if (index in current.indices) {
            current.removeAt(index)
            prefs.edit().putString("saved_memories_pipe", current.joinToString("||")).apply()
        }
    }

    private fun renderMemories() {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        memoriesListLayout.removeAllViews()
        val items = getMemoriesList()
        items.forEachIndexed { idx, memory ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.parseColor("#CBD5E1"))
                }
            }

            val tv = TextView(this).apply {
                text = memory
                textSize = 14f
                setTextColor(Color.parseColor("#0F172A"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnSpeak = Button(this).apply {
                text = "🔊"
                setOnClickListener {
                    PariRealVoiceSpeaker.speakInActiveModeVoice(this@MemoryActivity, memory)
                }
            }

            val btnDel = Button(this).apply {
                text = "✕"
                setOnClickListener {
                    deleteMemoryAt(idx)
                    renderMemories()
                }
            }

            card.addView(tv)
            card.addView(btnSpeak)
            card.addView(btnDel)
            memoriesListLayout.addView(card)
        }
    }
}
