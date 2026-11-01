package com.jarvis.assistant.ui.chat

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.audio.PariRealVoiceSpeaker
import com.jarvis.assistant.ui.home.MainActivity
import com.jarvis.assistant.ui.home.MainViewModel
import com.jarvis.assistant.util.HapticFeedbackHelper
import kotlinx.coroutines.launch

class ChatboxFragment : Fragment() {

    private val viewModel: MainViewModel
        get() = (requireActivity() as MainActivity).viewModel

    private lateinit var chatAdapter: JarvisChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvModeBadge: TextView
    private lateinit var inputField: EditText
    private lateinit var btnWebToggle: TextView
    private var webSearchEnabled: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#EEF2FF"))
        }

        // 1. Top Header Bar
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(ctx).apply {
            text = "💬 AI Chatbox (চ্যাটবক্স)"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        tvModeBadge = TextView(ctx).apply {
            val profile = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(ctx)
            text = profile.modeBadge
            textSize = 11.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, dp(2), 0, 0)
        }

        titleCol.addView(tvTitle)
        titleCol.addView(tvModeBadge)

        val btnStopVoice = Button(ctx).apply {
            text = "⏹ Stop Audio"
            isAllCaps = false
            textSize = 11f
            setTextColor(Color.WHITE)
            minHeight = dp(40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                PariRealVoiceSpeaker.stop()
            }
        }

        val btnClear = Button(ctx).apply {
            text = "🗑 Clear"
            isAllCaps = false
            textSize = 11f
            setTextColor(Color.parseColor("#FCA5A5"))
            minHeight = dp(40)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#EF4444"))
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                viewModel.clearChatHistory(ctx)
            }
        }

        header.addView(titleCol)
        header.addView(btnStopVoice)
        header.addView(btnClear)
        root.addView(header)

        // 2. Quick Suggestion Chips Row
        val chipScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(12), dp(8), dp(12), dp(6))
            setBackgroundColor(Color.parseColor("#E0E7FF"))
        }

        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val quickPrompts = listOf(
            "🌸 হাই পরী, কেমন আছো?",
            "📰 আজকের তাজা খবর বলো",
            "📱 Open WhatsApp",
            "🌤️ আজকের আবহাওয়া কেমন?",
            "💡 Write a Python script"
        )

        for (prompt in quickPrompts) {
            val chip = TextView(ctx).apply {
                text = prompt
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1E3A8A"))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(8)
                }
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.parseColor("#93C5FD"))
                }
                setOnClickListener {
                    HapticFeedbackHelper.performControlClick(it)
                    viewModel.sendChatMessage(ctx, prompt, webSearchEnabled)
                }
            }
            chipRow.addView(chip)
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        // 3. Chat RecyclerView
        chatAdapter = JarvisChatAdapter()
        recyclerView = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding(0, dp(8), 0, dp(8))
            clipToPadding = false
        }
        root.addView(recyclerView)

        // 4. Bottom Input Bar
        val inputBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.WHITE)
        }

        btnWebToggle = TextView(ctx).apply {
            text = "🌐 Web ON"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setTextColor(Color.parseColor("#0284C7"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E0F2FE"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                webSearchEnabled = !webSearchEnabled
                text = if (webSearchEnabled) "🌐 Web ON" else "🌐 Web OFF"
                setTextColor(if (webSearchEnabled) Color.parseColor("#0284C7") else Color.parseColor("#64748B"))
            }
        }

        inputField = EditText(ctx).apply {
            hint = "মেসেজ লিখুন... (Type message in Bangla/English)"
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#64748B"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F5F9"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#CBD5E1"))
            }
        }

        val btnSend = Button(ctx).apply {
            text = "Send ➤"
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(20).toFloat()
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                val msg = inputField.text?.toString()?.trim().orEmpty()
                if (msg.isNotEmpty()) {
                    inputField.setText("")
                    viewModel.sendChatMessage(ctx, msg, webSearchEnabled)
                }
            }
        }

        inputBar.addView(btnWebToggle)
        inputBar.addView(inputField)
        inputBar.addView(btnSend)
        root.addView(inputBar)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.ensureWelcomeTurn(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chatTurns.collect { turns ->
                    chatAdapter.submitList(turns)
                    if (turns.isNotEmpty()) {
                        recyclerView.scrollToPosition(turns.size - 1)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshVoiceModeHeader()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded) {
            refreshVoiceModeHeader()
            chatAdapter.notifyDataSetChanged()
        }
    }

    private fun refreshVoiceModeHeader() {
        val ctx = context ?: return
        val profile = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(ctx)
        if (::tvModeBadge.isInitialized) {
            tvModeBadge.text = profile.modeBadge
        }
    }
}
