package com.panda.ktorwebsocketmvvm.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents a chat message exchanged over WebSocket.
 *
 * @property sender     Display name of the message author.
 * @property content    Text body of the message (empty for voice messages).
 * @property timestamp  Unix epoch millis when the message was created.
 * @property type       Message type: `"text"` (default) or `"voice"`.
 * @property audioData  Base64-encoded audio bytes (AAC/M4A). Null for text messages.
 * @property audioDuration Duration of the voice recording in milliseconds.
 * @property isFromMe   Client-only flag indicating the message was sent by the local user.
 *                      Excluded from serialization via [Transient].
 */
@Serializable
data class ChatMessage(
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text",
    val audioData: String? = null,
    val audioDuration: Long = 0,
    val sendTo: String? = null,
    val messageId: String? = null,
    val status: String? = null,
    @Transient val isFromMe: Boolean = false
) {
    /** Returns `true` if this is a direct message. */
    val isDirectMessage: Boolean get() = !sendTo.isNullOrBlank()

    /** Returns `true` if this is a delivery status update. */
    val isStatusUpdate: Boolean get() = type == "status"
    /** Returns `true` if this message originated from the server. */
    val isFromServer: Boolean get() = sender == "server"

    /** Returns `true` if this is a voice message. */
    val isVoice: Boolean get() = type == "voice"

    /** Returns `true` if this is a typing indicator message. */
    val isTyping: Boolean get() = type == "typing"
}
