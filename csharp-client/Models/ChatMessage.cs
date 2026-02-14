using System.Text.Json.Serialization;

namespace ChatClientWpf.Models
{
    public class ChatMessage
    {
        [JsonPropertyName("sender")]
        public string Sender { get; set; } = string.Empty;

        [JsonPropertyName("content")]
        public string Content { get; set; } = string.Empty;

        [JsonPropertyName("timestamp")]
        public long Timestamp { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

        [JsonIgnore]
        public bool IsFromMe { get; set; }

        public bool IsFromServer => Sender == "server";

        public string FormattedTime =>
            DateTimeOffset.FromUnixTimeMilliseconds(Timestamp).LocalDateTime.ToString("HH:mm");
    }
}
