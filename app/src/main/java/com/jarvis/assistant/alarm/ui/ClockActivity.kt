package com.jarvis.assistant.alarm.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jarvis.assistant.alarm.data.AlarmRepository
import com.jarvis.assistant.alarm.scheduler.AlarmTriggerCalculator
import com.jarvis.assistant.audio.PariRealVoiceSpeaker
import kotlinx.coroutines.launch

class ClockActivity : AppCompatActivity() {

    private lateinit var alarmsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val repo = AlarmRepository.getInstance(this)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#EEF2FF"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(28))
        }

        // Top Bar with Back Button
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
            text = "⏰ JARVIC Voice Alarms & Clock"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, 0, 0)
        }

        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        root.addView(topBar)

        // Add Alarm Card
        val formCard = LinearLayout(this).apply {
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

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val etHour = EditText(this).apply {
            hint = "Hour (0-23)"
            setText("7")
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }

        val etMinute = EditText(this).apply {
            hint = "Min (0-59)"
            setText("30")
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        }

        timeRow.addView(etHour)
        timeRow.addView(etMinute)

        val etLabel = EditText(this).apply {
            hint = "Alarm Label (e.g., সকাল ৭টায় ঘুম থেকে ডেকে দিও)"
            setText("Wake up with Pari AI 🌸")
        }

        val btnAddAlarm = Button(this).apply {
            text = "＋ Add Voice Alarm"
            isAllCaps = false
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                val h = etHour.text?.toString()?.toIntOrNull() ?: 7
                val m = etMinute.text?.toString()?.toIntOrNull() ?: 0
                val lbl = etLabel.text?.toString()?.trim().orEmpty()
                val added = repo.addAlarm(h, m, lbl)
                PariRealVoiceSpeaker.speakInActiveModeVoice(
                    this@ClockActivity,
                    "আপনার অ্যালার্ম সেট করা হয়েছে: ${added.label}"
                )
            }
        }

        formCard.addView(timeRow)
        formCard.addView(etLabel)
        formCard.addView(btnAddAlarm)
        root.addView(formCard)

        alarmsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }
        root.addView(alarmsContainer)

        scroll.addView(root)
        setContentView(scroll)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.allAlarms.collect { alarms ->
                    alarmsContainer.removeAllViews()
                    for (alarm in alarms) {
                        val (timeStr, amPm) = AlarmTriggerCalculator.formatTime12Or24(alarm.hour, alarm.minute, false)
                        val row = LinearLayout(this@ClockActivity).apply {
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

                        val info = TextView(this@ClockActivity).apply {
                            text = "⏰ $timeStr $amPm\n${alarm.label} (${if (alarm.enabled) "ON" else "OFF"})"
                            textSize = 14f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.parseColor("#0F172A"))
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val btnToggle = Button(this@ClockActivity).apply {
                            text = if (alarm.enabled) "ON" else "OFF"
                            isAllCaps = false
                            setTextColor(Color.WHITE)
                            background = GradientDrawable().apply {
                                setColor(if (alarm.enabled) Color.parseColor("#16A34A") else Color.parseColor("#64748B"))
                                cornerRadius = dp(12).toFloat()
                            }
                            setOnClickListener { repo.toggleAlarm(alarm.id) }
                        }

                        val btnDelete = Button(this@ClockActivity).apply {
                            text = "✕"
                            setTextColor(Color.WHITE)
                            layoutParams = LinearLayout.LayoutParams(dp(48), dp(44)).apply {
                                marginStart = dp(6)
                            }
                            background = GradientDrawable().apply {
                                setColor(Color.parseColor("#DC2626"))
                                cornerRadius = dp(12).toFloat()
                            }
                            setOnClickListener { repo.deleteAlarm(alarm.id) }
                        }

                        row.addView(info)
                        row.addView(btnToggle)
                        row.addView(btnDelete)
                        alarmsContainer.addView(row)
                    }
                }
            }
        }
    }
}
