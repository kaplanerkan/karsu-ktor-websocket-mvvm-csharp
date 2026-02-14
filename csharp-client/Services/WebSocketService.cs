using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using ChatClientWpf.Models;

namespace ChatClientWpf.Services
{
    public class WebSocketService : IDisposable
    {
        private ClientWebSocket? _webSocket;
        private CancellationTokenSource? _cts;
        private bool _errorFired;

        public event Action<ChatMessage>? OnMessageReceived;
        public event Action<string>? OnConnectionStateChanged;
        public event Action<string>? OnError;

        public bool IsConnected =>
            _webSocket?.State == WebSocketState.Open;

        public async Task ConnectAsync(string host, int port, string clientId = "csharp-1")
        {
            // Stop previous connection cleanly
            await CleanupAsync();
            _errorFired = false;

            try
            {
                _webSocket = new ClientWebSocket();
                _cts = new CancellationTokenSource();

                var uri = new Uri($"ws://{host}:{port}/chat/{clientId}");
                Logger.Info($"Connecting to {uri}");
                OnConnectionStateChanged?.Invoke("Connecting");

                await _webSocket.ConnectAsync(uri, _cts.Token);
                Logger.Info("Connected successfully");
                OnConnectionStateChanged?.Invoke("Connected");

                _ = Task.Run(() => ReceiveLoopAsync(_cts.Token));
            }
            catch (Exception ex)
            {
                Logger.Error("Connection failed", ex);
                FireError($"Connection error: {ex.Message}");
            }
        }

        public async Task SendMessageAsync(ChatMessage message)
        {
            if (_webSocket?.State != WebSocketState.Open) return;

            try
            {
                var json = JsonSerializer.Serialize(message);
                var bytes = Encoding.UTF8.GetBytes(json);
                var segment = new ArraySegment<byte>(bytes);
                await _webSocket.SendAsync(segment, WebSocketMessageType.Text, true, CancellationToken.None);
                Logger.Info($"Sent: {json}");
            }
            catch (Exception ex)
            {
                Logger.Error("Message send failed", ex);
                FireError($"Message send failed: {ex.Message}");
            }
        }

        private async Task ReceiveLoopAsync(CancellationToken token)
        {
            var buffer = new byte[4096];

            try
            {
                while (_webSocket?.State == WebSocketState.Open && !token.IsCancellationRequested)
                {
                    var result = await _webSocket.ReceiveAsync(
                        new ArraySegment<byte>(buffer), token);

                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        Logger.Info("Server closed connection");
                        OnConnectionStateChanged?.Invoke("Disconnected");
                        return;
                    }

                    if (result.MessageType == WebSocketMessageType.Text)
                    {
                        var json = Encoding.UTF8.GetString(buffer, 0, result.Count);
                        Logger.Info($"Received: {json}");
                        try
                        {
                            var message = JsonSerializer.Deserialize<ChatMessage>(json);
                            if (message != null)
                                OnMessageReceived?.Invoke(message);
                        }
                        catch
                        {
                            OnMessageReceived?.Invoke(new ChatMessage
                            {
                                Sender = "unknown",
                                Content = json
                            });
                        }
                    }
                }
            }
            catch (OperationCanceledException)
            {
                Logger.Info("Receive loop cancelled");
            }
            catch (WebSocketException ex)
            {
                Logger.Error("WebSocket error in receive loop", ex);
                FireError($"Connection lost: {ex.Message}");
            }
            catch (Exception ex)
            {
                Logger.Error("Unexpected error in receive loop", ex);
                FireError($"Connection lost: {ex.Message}");
            }
        }

        public async Task DisconnectAsync()
        {
            try
            {
                if (_webSocket?.State == WebSocketState.Open)
                {
                    Logger.Info("Disconnecting...");
                    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(3));
                    await _webSocket.CloseAsync(
                        WebSocketCloseStatus.NormalClosure,
                        "Client closed",
                        timeout.Token);
                    Logger.Info("Disconnected");
                }
            }
            catch (Exception ex)
            {
                Logger.Error("Disconnect error", ex);
            }

            await CleanupAsync();
            OnConnectionStateChanged?.Invoke("Disconnected");
        }

        /// <summary>
        /// Fire error only once per connection attempt to prevent cascade.
        /// </summary>
        private void FireError(string message)
        {
            if (_errorFired) return;
            _errorFired = true;
            OnError?.Invoke(message);
            OnConnectionStateChanged?.Invoke("Disconnected");
        }

        /// <summary>
        /// Cancel receive loop and dispose old socket.
        /// </summary>
        private async Task CleanupAsync()
        {
            try
            {
                _cts?.Cancel();
                // Give receive loop time to exit
                await Task.Delay(100);
            }
            catch { /* ignore */ }

            try { _webSocket?.Dispose(); } catch { /* ignore */ }
            try { _cts?.Dispose(); } catch { /* ignore */ }

            _webSocket = null;
            _cts = null;
        }

        public void Dispose()
        {
            _cts?.Cancel();
            try { _webSocket?.Dispose(); } catch { /* ignore */ }
            try { _cts?.Dispose(); } catch { /* ignore */ }
        }
    }
}
