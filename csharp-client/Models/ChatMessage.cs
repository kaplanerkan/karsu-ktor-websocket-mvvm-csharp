using System.Text.Json.Serialization;

namespace ChatClientWpf.Models
{
    /// <summary>
    /// Represents a chat message exchanged over WebSocket.
    /// Supports both text and voice message types.
    /// </summary>
    public class ChatMessage
    {
        /// <summary>Display name of the message author.</summary>
        [JsonPropertyName("sender")]
        public string Sender { get; set; } = string.Empty;

        /// <summary>Text body of the message (empty for voice messages).</summary>
        [JsonPropertyName("content")]
        public string Content { get; set; } = string.Empty;

        /// <summary>Unix epoch milliseconds when the message was created.</summary>
        [JsonPropertyName("timestamp")]
        public long Timestamp { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

        /// <summary>Message type: "text" (default) or "voice".</summary>
        [JsonPropertyName("type")]
        public string Type { get; set; } = "text";

        /// <summary>Base64-encoded audio bytes (AAC/M4A) for voice messages. Null for text.</summary>
        [JsonPropertyName("audioData")]
        public string? AudioData { get; set; }

        /// <summary>Duration of the voice recording in milliseconds.</summary>
        [JsonPropertyName("audioDuration")]
        public long AudioDuration { get; set; }

        /// <summary>Client-only flag indicating the message was sent by the local user.</summary>
        [JsonIgnore]
        public bool IsFromMe { get; set; }

        /// <summary>Returns true if this is a voice message.</summary>
        [JsonIgnore]
        public bool IsVoice => Type == "voice";

        /// <summary>Returns true if this message originated from the server.</summary>
        public bool IsFromServer => Sender == "server";

        /// <summary>Returns the timestamp formatted as HH:mm for display.</summary>
        public string FormattedTime =>
            DateTimeOffset.FromUnixTimeMilliseconds(Timestamp).LocalDateTime.ToString("HH:mm");

        /// <summary>Returns the audio duration formatted as M:SS (e.g. "0:05").</summary>
        public string FormattedDuration
        {
            get
            {
                var seconds = AudioDuration / 1000;
                return $"{seconds / 60}:{seconds % 60:D2}";
            }
        }
    }
}
