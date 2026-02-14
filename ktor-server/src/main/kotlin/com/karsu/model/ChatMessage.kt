package com.karsu.model

import kotlinx.serialization.Serializable

/**
 * Server-side chat message model. Serialized/deserialized as JSON over WebSocket.
 *
 * @property sender        Client identifier (e.g. "android-1", "csharp-1", "server").
 * @property content       Text body of the message (empty for voice messages).
 * @property timestamp     Unix epoch millis when the message was created.
 * @property type          Message type: `"text"` (default) or `"voice"`.
 * @property audioData     Base64-encoded audio bytes for voice messages. Null for text.
 * @property audioDuration Duration of the voice recording in milliseconds.
 */
@Serializable
data class ChatMessage(
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text",
    val audioData: String? = null,
    val audioDuration: Long = 0
)
