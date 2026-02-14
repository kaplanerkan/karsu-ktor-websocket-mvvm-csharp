package com.panda.ktorwebsocketmvvm.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for chat messages.
 * Supports both text and voice message types with WhatsApp-style bubble alignment.
 */
class ChatAdapter(
    private val onPlayVoiceClick: (ChatMessage) -> Unit
) : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding, onPlayVoiceClick)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(
        private val binding: ItemMessageBinding,
        private val onPlayVoiceClick: (ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        /**
         * Binds a [ChatMessage] to the bubble view.
         * Handles text/voice visibility toggle and bubble alignment/colors.
         */
        fun bind(message: ChatMessage) {
            val context = binding.root.context

            binding.tvSender.text = message.sender
            binding.tvTime.text = timeFormat.format(Date(message.timestamp))

            // Toggle text vs voice content
            if (message.isVoice) {
                binding.tvContent.visibility = View.GONE
                binding.voiceContainer.visibility = View.VISIBLE
                val seconds = message.audioDuration / 1000
                binding.tvDuration.text = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
                binding.btnPlay.setOnClickListener { onPlayVoiceClick(message) }
            } else {
                binding.tvContent.visibility = View.VISIBLE
                binding.tvContent.text = message.content
                binding.voiceContainer.visibility = View.GONE
            }

            val bubbleParams = binding.cardBubble.layoutParams as? FrameLayout.LayoutParams ?: return

            when {
                message.isFromMe -> {
                    bubbleParams.gravity = Gravity.END
                    binding.cardBubble.setCardBackgroundColor(
                        MaterialColors.getColor(context, MaterialR.attr.colorPrimaryContainer, 0)
                    )
                    binding.tvSender.setTextColor(
                        MaterialColors.getColor(context, android.R.attr.colorPrimary, 0)
                    )
                    binding.tvContent.setTextColor(
                        MaterialColors.getColor(context, MaterialR.attr.colorOnPrimaryContainer, 0)
                    )
                }
                message.isFromServer -> {
                    bubbleParams.gravity = Gravity.CENTER_HORIZONTAL
                    binding.cardBubble.setCardBackgroundColor(
                        MaterialColors.getColor(context, MaterialR.attr.colorSecondaryContainer, 0)
                    )
                    binding.tvSender.setTextColor(
                        MaterialColors.getColor(context, MaterialR.attr.colorSecondary, 0)
                    )
                    binding.tvContent.setTextColor(
                        MaterialColors.getColor(context, MaterialR.attr.colorOnSecondaryContainer, 0)
                    )
                }
                else -> {
                    bubbleParams.gravity = Gravity.START
                    binding.cardBubble.setCardBackgroundColor(
                        MaterialColors.getColor(context, MaterialR.attr.colorSurfaceVariant, 0)
                    )
                    binding.tvSender.setTextColor(
                        MaterialColors.getColor(context, android.R.attr.colorPrimary, 0)
                    )
                    binding.tvContent.setTextColor(
                        MaterialColors.getColor(context, MaterialR.attr.colorOnSurface, 0)
                    )
                }
            }

            binding.cardBubble.layoutParams = bubbleParams
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) =
            old.timestamp == new.timestamp && old.sender == new.sender

        override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) =
            old == new
    }
}
