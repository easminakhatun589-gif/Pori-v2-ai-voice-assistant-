package com.jarvis.assistant.ui.chat

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R
import com.jarvis.assistant.audio.PariRealVoiceSpeaker
import com.jarvis.assistant.data.model.ChatTurn

class JarvisChatAdapter : RecyclerView.Adapter<JarvisChatAdapter.ChatViewHolder>() {

    private val turns = mutableListOf<ChatTurn>()

    fun submitList(newTurns: List<ChatTurn>) {
        turns.clear()
        turns.addAll(newTurns)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

        val userBubble = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                marginStart = dp(40)
                bottomMargin = dp(6)
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(16).toFloat()
            }
        }

        val userTv = TextView(ctx).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
        }
        userBubble.addView(userTv)

        val aiCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(28)
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFFFF"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#CBD5E1"))
            }
        }

        val jarvisTv = TextView(ctx).apply {
            textSize = 14.5f
            setTextColor(Color.parseColor("#0F172A"))
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        val speakerBtn = TextView(ctx).apply {
            text = "🔊 Listen in Real Voice"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EFF6FF"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#BFDBFE"))
            }
            isClickable = true
            isFocusable = true
        }

        val citationsContainer = LinearLayout(ctx).apply {
            id = R.id.layoutCitationsContainer
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }

        aiCard.addView(jarvisTv)
        aiCard.addView(speakerBtn)
        aiCard.addView(citationsContainer)

        root.addView(userBubble)
        root.addView(aiCard)

        return ChatViewHolder(root, userBubble, userTv, jarvisTv, speakerBtn, citationsContainer)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(turns[position], position)
    }

    override fun getItemCount(): Int = turns.size

    class ChatViewHolder(
        itemView: View,
        private val userBubble: LinearLayout,
        private val userTv: TextView,
        private val jarvisTv: TextView,
        private val speakerBtn: TextView,
        private val citationsContainer: LinearLayout
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(turn: ChatTurn, position: Int) {
            userTv.text = turn.userTranscript
            userBubble.visibility = if (turn.userTranscript.isBlank()) View.GONE else View.VISIBLE
            jarvisTv.text = turn.jarvisResponse

            val profile = PariRealVoiceSpeaker.resolveActiveModeVoiceProfile(itemView.context)
            speakerBtn.text = profile.speakerButtonLabel
            speakerBtn.setOnClickListener {
                PariRealVoiceSpeaker.toggleSpeakTurn(
                    context = itemView.context,
                    turnId = turn.id,
                    text = turn.jarvisResponse
                ) { isSpeaking ->
                    speakerBtn.text = if (isSpeaking) profile.speakingStatusLabel else profile.speakerButtonLabel
                }
            }

            citationsContainer.removeAllViews()
            val validCitations = turn.citations.filter { it.url.startsWith("https://", ignoreCase = true) }
            if (validCitations.isNotEmpty()) {
                citationsContainer.visibility = View.VISIBLE
                val headerTv = TextView(itemView.context).apply {
                    text = "Verified Sources:"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#2563EB"))
                }
                citationsContainer.addView(headerTv)

                for (citation in validCitations) {
                    val chip = TextView(itemView.context).apply {
                        text = "🔗 ${citation.title} — ${citation.url}"
                        textSize = 12f
                        setTextColor(Color.parseColor("#0EA5E9"))
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            val browseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(citation.url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            itemView.context.startActivity(browseIntent)
                        }
                    }
                    citationsContainer.addView(chip)
                }
            } else {
                citationsContainer.visibility = View.GONE
            }
        }
    }
}
