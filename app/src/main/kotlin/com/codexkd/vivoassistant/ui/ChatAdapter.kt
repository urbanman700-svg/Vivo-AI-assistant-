package com.codexkd.vivoassistant.ui

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.codexkd.vivoassistant.R
import com.codexkd.vivoassistant.models.Message
import io.noties.markwon.Markwon

/**
 * ChatAdapter — RecyclerView adapter for AI chat messages.
 *
 * Features:
 * - Separate layouts for user and assistant bubbles
 * - Markdown rendering for AI responses
 * - Typing animation for streaming effect
 * - Long-press to copy message
 * - Error state styling
 * - Smooth slide-in animations
 */
class ChatAdapter(
    private val context: Context,
    private val onSpeakClick: ((Message) -> Unit)? = null
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val markwon: Markwon = Markwon.create(context)
    private var typingAnimatorMap = mutableMapOf<Int, ValueAnimator>()

    companion object {
        private const val VIEW_USER      = 0
        private const val VIEW_ASSISTANT = 1
        private const val VIEW_TYPING    = 2
    }

    // ═══════════════════════════════════════════════
    // TYPING INDICATOR STATE
    // ═══════════════════════════════════════════════

    private var isTyping = false

    fun showTyping() {
        if (isTyping) return
        isTyping = true
        notifyItemInserted(itemCount)
    }

    fun hideTyping() {
        if (!isTyping) return
        isTyping = false
        notifyItemRemoved(itemCount)
    }

    override fun getItemCount(): Int = super.getItemCount() + if (isTyping) 1 else 0

    // ═══════════════════════════════════════════════
    // VIEW TYPE
    // ═══════════════════════════════════════════════

    override fun getItemViewType(position: Int): Int {
        if (isTyping && position == itemCount - 1) return VIEW_TYPING
        return if (getItem(position).role == "user") VIEW_USER else VIEW_ASSISTANT
    }

    // ═══════════════════════════════════════════════
    // VIEWHOLDERS
    // ═══════════════════════════════════════════════

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_USER      -> UserViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            VIEW_TYPING    -> TypingViewHolder(inflater.inflate(R.layout.item_message_typing, parent, false))
            else           -> AssistantViewHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Apply slide-in animation
        val animation = AnimationUtils.loadAnimation(context, R.anim.slide_up_fade_in)
        holder.itemView.startAnimation(animation)

        when (holder) {
            is UserViewHolder      -> holder.bind(getItem(position))
            is AssistantViewHolder -> holder.bind(getItem(position))
            is TypingViewHolder    -> holder.startAnimation()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is TypingViewHolder) holder.stopAnimation()
        super.onViewRecycled(holder)
    }

    // ─────────────────────────────
    // USER BUBBLE
    // ─────────────────────────────
    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        private val tvTime: TextView    = view.findViewById(R.id.tvTime)
        private val icVoice: ImageView  = view.findViewById(R.id.icVoice)

        fun bind(message: Message) {
            tvMessage.text = message.content
            tvTime.text    = formatTime(message.timestamp)
            icVoice.visibility = if (message.isVoice) View.VISIBLE else View.GONE

            // Long-press to copy
            itemView.setOnLongClickListener {
                copyToClipboard(message.content)
                true
            }
        }
    }

    // ─────────────────────────────
    // ASSISTANT BUBBLE
    // ─────────────────────────────
    inner class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView  = view.findViewById(R.id.tvMessage)
        private val tvTime: TextView     = view.findViewById(R.id.tvTime)
        private val tvModel: TextView    = view.findViewById(R.id.tvModel)
        private val btnSpeak: ImageView  = view.findViewById(R.id.btnSpeak)
        private val ivError: ImageView   = view.findViewById(R.id.ivError)

        fun bind(message: Message) {
            // Render Markdown for AI responses
            markwon.setMarkdown(tvMessage, message.content)

            tvTime.text = formatTime(message.timestamp)

            // Show model name if available
            if (message.model.isNotBlank()) {
                tvModel.visibility = View.VISIBLE
                tvModel.text = formatModelName(message.model)
            } else {
                tvModel.visibility = View.GONE
            }

            // Error state
            ivError.visibility = if (message.isError) View.VISIBLE else View.GONE
            if (message.isError) {
                tvMessage.setTextColor(itemView.context.getColor(R.color.error_text))
            } else {
                tvMessage.setTextColor(itemView.context.getColor(R.color.text_primary))
            }

            // Speak button
            btnSpeak.setOnClickListener {
                onSpeakClick?.invoke(message)
            }

            // Long-press to copy
            itemView.setOnLongClickListener {
                copyToClipboard(message.content)
                true
            }
        }
    }

    // ─────────────────────────────
    // TYPING INDICATOR
    // ─────────────────────────────
    inner class TypingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dot1: View = view.findViewById(R.id.dot1)
        private val dot2: View = view.findViewById(R.id.dot2)
        private val dot3: View = view.findViewById(R.id.dot3)
        private var animator: ValueAnimator? = null

        fun startAnimation() {
            var step = 0
            animator = ValueAnimator.ofInt(0, 2).apply {
                duration = 600
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    val v = it.animatedValue as Int
                    dot1.alpha = if (v == 0) 1f else 0.3f
                    dot2.alpha = if (v == 1) 1f else 0.3f
                    dot3.alpha = if (v == 2) 1f else 0.3f
                }
                start()
            }
        }

        fun stopAnimation() {
            animator?.cancel()
            animator = null
        }
    }

    // ═══════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════

    private fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Vivo Message", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE))
    }

    private fun formatModelName(model: String): String {
        return model.split("/").lastOrNull()?.take(20) ?: model.take(20)
    }

    // ═══════════════════════════════════════════════
    // DIFF CALLBACK
    // ═══════════════════════════════════════════════

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
    }
}
