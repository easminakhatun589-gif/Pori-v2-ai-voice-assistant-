package com.jarvis.assistant.ui.agent

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jarvis.assistant.audio.PariRealVoiceSpeaker
import com.jarvis.assistant.automation.agent.MasterAutonomousAgent
import kotlinx.coroutines.launch

class AgentDashboardActivity : AppCompatActivity() {

    private lateinit var stepsContainer: LinearLayout
    private lateinit var tvStatusHeader: TextView

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

        val tvTitle = TextView(this).apply {
            text = "🤖 JARVIC Master Autonomous Agent & Multi-Task Dashboard"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, dp(10), 0, dp(6))
        }
        root.addView(tvTitle)

        tvStatusHeader = TextView(this).apply {
            text = "Queue Status: Ready"
            textSize = 13.5f
            setTextColor(Color.parseColor("#2563EB"))
        }
        root.addView(tvStatusHeader)

        val etCommand = EditText(this).apply {
            hint = "Enter multi-step command (e.g., Open Facebook, read post, write comment, then open WhatsApp)"
            setText("Open Facebook, read post, write a nice comment, then open Instagram and check latest post")
        }
        root.addView(etCommand)

        val btnRun = Button(this).apply {
            text = "▶ Execute Multi-Task Plan"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                val cmd = etCommand.text?.toString()?.trim().orEmpty()
                if (cmd.isNotEmpty()) {
                    MasterAutonomousAgent.executeVoiceCommandPlan(this@AgentDashboardActivity, cmd) { spoken ->
                        PariRealVoiceSpeaker.speakInActiveModeVoice(this@AgentDashboardActivity, spoken)
                    }
                }
            }
        }
        root.addView(btnRun)

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
        }

        val btnApprove = Button(this).apply {
            text = "✓ Approve Step"
            isAllCaps = false
            setOnClickListener {
                MasterAutonomousAgent.approveWaitingStepAndContinue(this@AgentDashboardActivity) { spoken ->
                    PariRealVoiceSpeaker.speakInActiveModeVoice(this@AgentDashboardActivity, spoken)
                }
            }
        }

        val btnStop = Button(this).apply {
            text = "✕ Stop Queue"
            isAllCaps = false
            setOnClickListener {
                MasterAutonomousAgent.cancelActivePlan(this@AgentDashboardActivity)
            }
        }

        controlsRow.addView(btnApprove)
        controlsRow.addView(btnStop)
        root.addView(controlsRow)

        stepsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(stepsContainer)

        scroll.addView(root)
        setContentView(scroll)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MasterAutonomousAgent.currentPlan.collect { plan ->
                    stepsContainer.removeAllViews()
                    if (plan == null) {
                        tvStatusHeader.text = "Queue Status: Idle (Tap 'Execute Multi-Task Plan' above)"
                    } else {
                        tvStatusHeader.text = "Queue Status: ${plan.overallStatus} (${plan.steps.size} steps)"
                        for (step in plan.steps) {
                            val tv = TextView(this@AgentDashboardActivity).apply {
                                text = step.formattedDashboardLine()
                                textSize = 14f
                                setTextColor(Color.parseColor("#0F172A"))
                                setPadding(dp(12), dp(10), dp(12), dp(10))
                            }
                            stepsContainer.addView(tv)
                        }
                    }
                }
            }
        }
    }
}
