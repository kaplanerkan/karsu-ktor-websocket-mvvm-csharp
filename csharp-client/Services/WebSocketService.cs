using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using ChatClientWpf.Models;

namespace ChatClientWpf.Services
{
    public class WebSocketService : IDisposable
    {
        private readonly SemaphoreSlim _connectionLock = new(1, 1);
        private ClientWebSocket? _webSocket;
        private CancellationTokenSource? _cts;
        private volatile bool _errorFired;

        public event Action<ChatMessage>? OnMessageReceived;
        public event Action<string>? OnConnectionStateChanged;
        public event Action<string>? OnError;

        public bool IsConnected
        {
            get
            {
                var ws = _webSocket;
                return ws?.State == WebSocketState.Open;
            }
        }

        public async Task ConnectAsync(string host, int port, string clientId = "csharp-1", string roomId = "general")
        {
            await _connectionLock.WaitAsync();
            try
            {
                // Stop previous connection cleanly
                await CleanupInternalAsync();
                _errorFired = false;

                var ws = new ClientWebSocket();
                var cts = new CancellationTokenSource();
                _webSocket = ws;
                _cts = cts;

                var path = roomId == "general" ? $"/chat/{clientId}" : $"/chat/{roomId}/{clientId}";
                var uri = new Uri($"ws://{host}:{port}{path}");
                Logger.Info($"Connecting to {uri}");
                OnConnectionStateChanged?.Invoke("Connecting");

                await ws.ConnectAsync(uri, cts.Token);
                Logger.Info("Connected successfully");
                OnConnectionStateChanged?.Invoke("Connected");

                _ = Task.Run(() => ReceiveLoopAsync(ws, cts.Token));
            }
            catch (Exception ex)
            {
                Logger.Error("Connection failed", ex);
                FireError($"Connection error: {ex.Message}");
            }
            finally
            {
                _connectionLock.Release();
            }
        }

        public async Task SendMessageAsync(ChatMessage message)
        {
            var ws = _webSocket;
            if (ws?.State != WebSocketState.Open) return;

            try
            {
                var json = JsonSerializer.Serialize(message);
                var bytes = Encoding.UTF8.GetBytes(json);
                var segment = new ArraySegment<byte>(bytes);
                using var sendTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
                await ws.SendAsync(segment, WebSocketMessageType.Text, true, sendTimeout.Token);
                Logger.Info($"Sent: {json}");
            }
            catch (Exception ex)
            {
                Logger.Error("Message send failed", ex);
                FireError($"Message send failed: {ex.Message}");
            }
        }

        private async Task ReceiveLoopAsync(ClientWebSocket ws, CancellationToken token)
        {
            var buffer = new byte[4096];

            try
            {
                while (ws.State == WebSocketState.Open && !token.IsCancellationRequested)
                {
                    using var ms = new MemoryStream();
                    WebSocketReceiveResult result;

                    // Read full message (may span multiple frames)
                    do
                    {
                        result = await ws.ReceiveAsync(new ArraySegment<byte>(buffer), token);
                        ms.Write(buffer, 0, result.Count);
                    } while (!result.EndOfMessage);

                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        Logger.Info("Server closed connection");
                        OnConnectionStateChanged?.Invoke("Disconnected");
                        return;
                    }

                    if (result.MessageType == WebSocketMessageType.Text)
                    {
                        var json = Encoding.UTF8.GetString(ms.ToArray());
                        Logger.Info($"Received: {json}");
                        try
                        {
                            var message = JsonSerializer.Deserialize<ChatMessage>(json);
                            if (message != null)
                                OnMessageReceived?.Invoke(message);
                        }
                        catch (JsonException ex)
                        {
                            Logger.Error("Failed to deserialize message", ex);
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
            await _connectionLock.WaitAsync();
            try
            {
                var ws = _webSocket;
                if (ws?.State == WebSocketState.Open)
                {
                    Logger.Info("Disconnecting...");
                    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(3));
                    await ws.CloseAsync(
                        WebSocketCloseStatus.NormalClosure,
                        "Client closed",
                        timeout.Token);
                    Logger.Info("Disconnected");
                }

                await CleanupInternalAsync();
                OnConnectionStateChanged?.Invoke("Disconnected");
            }
            catch (Exception ex)
            {
                Logger.Error("Disconnect error", ex);
                await CleanupInternalAsync();
                OnConnectionStateChanged?.Invoke("Disconnected");
            }
            finally
            {
                _connectionLock.Release();
            }
        }

        private void FireError(string message)
        {
            if (_errorFired) return;
            _errorFired = true;
            OnError?.Invoke(message);
            OnConnectionStateChanged?.Invoke("Disconnected");
        }

        private async Task CleanupInternalAsync()
        {
            var oldCts = _cts;
            var oldWs = _webSocket;
            _cts = null;
            _webSocket = null;

            try { oldCts?.Cancel(); await Task.Delay(100); } catch { }
            try { oldWs?.Dispose(); } catch { }
            try { oldCts?.Dispose(); } catch { }
        }

        public void Dispose()
        {
            var oldCts = _cts;
            var oldWs = _webSocket;
            _cts = null;
            _webSocket = null;

            try { oldCts?.Cancel(); } catch { }
            try { oldWs?.Dispose(); } catch { }
            try { oldCts?.Dispose(); } catch { }
            _connectionLock.Dispose();
            GC.SuppressFinalize(this);
        }
    }
}
