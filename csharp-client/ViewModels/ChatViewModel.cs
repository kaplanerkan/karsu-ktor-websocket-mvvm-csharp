using System.Collections.ObjectModel;
using System.Media;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using ChatClientWpf.Models;
using ChatClientWpf.Services;

namespace ChatClientWpf.ViewModels
{
    public class ChatViewModel : BaseViewModel, IDisposable
    {
        private const int MaxReconnectAttempts = 10;
        private const int InitialBackoffMs = 1000;
        private const int MaxBackoffMs = 30_000;
        private const long TypingDebounceMs = 2000;
        private const long TypingExpireMs = 4000;

        private const long OnlineUsersPollMs = 5000;

        private readonly WebSocketService _webSocketService;
        private readonly SettingsService _settings;
        private readonly AudioPlayerService _audioPlayer;
        private readonly AudioRecorderService _audioRecorder;
        private readonly HttpClient _httpClient = new();

        private readonly RelayCommand _connectCommand;
        private readonly RelayCommand _disconnectCommand;
        private readonly RelayCommand _sendCommand;
        private readonly RelayCommand _playVoiceCommand;
        private readonly RelayCommand _toggleThemeCommand;

        private bool _userDisconnected;
        private CancellationTokenSource? _reconnectCts;
        private string _lastHost = "";
        private int _lastPort = 8080;
        private string _lastClientId = "";
        private string _lastRoomId = "general";

        // Online users polling
        private CancellationTokenSource? _onlineUsersCts;

        // Typing indicator state
        private CancellationTokenSource? _typingDebounceCts;
        private long _lastTypingSentTime;
        private readonly Dictionary<string, CancellationTokenSource> _typingExpireCts = new();
        private readonly HashSet<string> _typingUserSet = new();

        // ── Properties ──────────────────────────────────────

        private string _host = "127.0.0.1";
        public string Host
        {
            get => _host;
            set => SetProperty(ref _host, value);
        }

        private string _port = "8080";
        public string Port
        {
            get => _port;
            set => SetProperty(ref _port, value);
        }

        private string _username = "papa-1";
        public string Username
        {
            get => _username;
            set => SetProperty(ref _username, value);
        }

        private string _roomId = "general";
        public string RoomId
        {
            get => _roomId;
            set => SetProperty(ref _roomId, value);
        }

        private string _messageInput = string.Empty;
        public string MessageInput
        {
            get => _messageInput;
            set
            {
                if (SetProperty(ref _messageInput, value))
                {
                    _sendCommand.RaiseCanExecuteChanged();
                    OnMessageInputChanged(value);
                }
            }
        }

        private string _connectionStatus = "Disconnected";
        public string ConnectionStatus
        {
            get => _connectionStatus;
            set => SetProperty(ref _connectionStatus, value);
        }

        private bool _isConnected;
        public bool IsConnected
        {
            get => _isConnected;
            set
            {
                if (SetProperty(ref _isConnected, value))
                {
                    _connectCommand.RaiseCanExecuteChanged();
                    _disconnectCommand.RaiseCanExecuteChanged();
                    _sendCommand.RaiseCanExecuteChanged();
                }
            }
        }

        private bool _isRecording;
        public bool IsRecording
        {
            get => _isRecording;
            set => SetProperty(ref _isRecording, value);
        }

        private string _typingStatus = string.Empty;
        public string TypingStatus
        {
            get => _typingStatus;
            set => SetProperty(ref _typingStatus, value);
        }

        private string? _dmTarget;
        public string? DmTarget
        {
            get => _dmTarget;
            set
            {
                if (SetProperty(ref _dmTarget, value))
                    OnPropertyChanged(nameof(DmHint));
            }
        }

        public string DmHint => DmTarget != null ? $"DM to {DmTarget}..." : "Type a message...";

        private string _onlineUsersText = string.Empty;
        public string OnlineUsersText
        {
            get => _onlineUsersText;
            set => SetProperty(ref _onlineUsersText, value);
        }

        private ObservableCollection<string> _onlineUsers = new();
        public ObservableCollection<string> OnlineUsers
        {
            get => _onlineUsers;
            set => SetProperty(ref _onlineUsers, value);
        }

        public ObservableCollection<ChatMessage> Messages { get; } = new();

        // ── Commands (public ICommand for XAML binding) ─────

        private bool _isDarkMode = true;
        public bool IsDarkMode
        {
            get => _isDarkMode;
            set => SetProperty(ref _isDarkMode, value);
        }

        public ICommand ConnectCommand => _connectCommand;
        public ICommand DisconnectCommand => _disconnectCommand;
        public ICommand SendCommand => _sendCommand;
        public ICommand PlayVoiceCommand => _playVoiceCommand;
        public ICommand ToggleThemeCommand => _toggleThemeCommand;

        // ── Constructor ─────────────────────────────────────

        public ChatViewModel()
        {
            _webSocketService = new WebSocketService();
            _settings = SettingsService.Load();
            _audioPlayer = new AudioPlayerService();
            _audioRecorder = new AudioRecorderService();

            Host = _settings.Host ?? "127.0.0.1";
            Port = _settings.Port ?? "8080";
            Username = _settings.Username ?? "papa-1";

            _connectCommand = new RelayCommand(_ => ConnectAsync(), _ => !IsConnected);
            _disconnectCommand = new RelayCommand(_ => DisconnectAsync(), _ => IsConnected);
            _sendCommand = new RelayCommand(_ => SendAsync(), _ => IsConnected && !string.IsNullOrWhiteSpace(MessageInput));
            _playVoiceCommand = new RelayCommand(PlayVoice, _ => true);
            _toggleThemeCommand = new RelayCommand(_ => ToggleTheme(), _ => true);

            _webSocketService.OnMessageReceived += OnMessageReceived;
            _webSocketService.OnConnectionStateChanged += OnConnectionStateChanged;
            _webSocketService.OnError += OnErrorOccurred;
        }

        // ── Command Handlers ────────────────────────────────

        private async void ConnectAsync()
        {
            try
            {
                CancelReconnect();
                _userDisconnected = false;

                var port = int.TryParse(Port, out var p) ? p : 8080;
                var clientId = string.IsNullOrWhiteSpace(Username) ? "papa-1" : Username.Trim();

                var roomId = string.IsNullOrWhiteSpace(RoomId) ? "general" : RoomId.Trim();
                _lastHost = Host;
                _lastPort = port;
                _lastClientId = clientId;
                _lastRoomId = roomId;

                _settings.Host = Host;
                _settings.Port = Port;
                _settings.Username = clientId;
                _settings.Save();

                // Clear chat history and typing state on new connection
                ClearTypingState();
                DispatchUI(() => Messages.Clear());

                await _webSocketService.ConnectAsync(Host, port, clientId, roomId);
            }
            catch (Exception ex)
            {
                Logger.Error("ConnectAsync unhandled", ex);
                DispatchUI(() => ConnectionStatus = $"Error: {ex.Message}");
            }
        }

        private async void DisconnectAsync()
        {
            try
            {
                SendTypingStopSync();
                ClearTypingState();
                _userDisconnected = true;
                CancelReconnect();

                // Clear chat history on disconnect
                DispatchUI(() => Messages.Clear());

                await _webSocketService.DisconnectAsync();
            }
            catch (Exception ex)
            {
                Logger.Error("DisconnectAsync unhandled", ex);
            }
        }

        private async void SendAsync()
        {
            try
            {
                var text = MessageInput.Trim();
                if (string.IsNullOrEmpty(text)) return;

                SendTypingStopSync();
                var senderName = string.IsNullOrWhiteSpace(Username) ? "papa-1" : Username.Trim();
                var target = DmTarget;
                var msgId = Guid.NewGuid().ToString()[..8];
                var displayMsg = new ChatMessage
                {
                    Sender = senderName,
                    Content = target != null ? $"[DM to {target}] {text}" : text,
                    MessageId = msgId,
                    Status = "sent",
                    IsFromMe = true
                };
                var wireMsg = new ChatMessage
                {
                    Sender = senderName,
                    Content = text,
                    SendTo = target,
                    MessageId = msgId,
                    Status = "sent"
                };

                DispatchUI(() => Messages.Add(displayMsg));
                await _webSocketService.SendMessageAsync(wireMsg);
                SystemSounds.Exclamation.Play();
                MessageInput = string.Empty;
            }
            catch (Exception ex)
            {
                Logger.Error("SendAsync unhandled", ex);
            }
        }

        /// <summary>
        /// Starts microphone recording. Called on mouse-down of the mic button.
        /// </summary>
        public void OnRecordStart()
        {
            if (!IsConnected || IsRecording) return;

            try
            {
                _audioRecorder.Start();
                IsRecording = true;
            }
            catch (Exception ex)
            {
                Logger.Error("OnRecordStart failed", ex);
            }
        }

        /// <summary>
        /// Stops recording and sends the voice message. Called on mouse-up of the mic button.
        /// </summary>
        public async void OnRecordStop()
        {
            if (!IsRecording) return;

            try
            {
                var result = _audioRecorder.Stop();
                IsRecording = false;

                if (result == null) return;

                var senderName = string.IsNullOrWhiteSpace(Username) ? "papa-1" : Username.Trim();
                var voiceMessage = new ChatMessage
                {
                    Sender = senderName,
                    Content = "",
                    Type = "voice",
                    AudioData = result.Value.base64Audio,
                    AudioDuration = result.Value.durationMs,
                    IsFromMe = true
                };

                DispatchUI(() => Messages.Add(voiceMessage));
                await _webSocketService.SendMessageAsync(voiceMessage);
                SystemSounds.Exclamation.Play();
            }
            catch (Exception ex)
            {
                Logger.Error("OnRecordStop failed", ex);
                IsRecording = false;
            }
        }

        /// <summary>
        /// Plays or stops voice playback for the given message.
        /// </summary>
        private void PlayVoice(object? parameter)
        {
            if (parameter is not ChatMessage message || !message.IsVoice) return;
            if (string.IsNullOrEmpty(message.AudioData)) return;

            if (_audioPlayer.IsPlaying)
                _audioPlayer.Stop();
            else
                _audioPlayer.Play(message.AudioData);
        }

        // ── Event Handlers ──────────────────────────────────

        private void OnMessageReceived(ChatMessage message)
        {
            if (message.Type == "typing")
            {
                HandleIncomingTypingIndicator(message);
                return;
            }

            if (message.IsStatusUpdate)
            {
                // Update status of existing sent message
                var msgId = message.MessageId;
                if (string.IsNullOrEmpty(msgId)) return;
                DispatchUI(() =>
                {
                    var existing = Messages.FirstOrDefault(m => m.MessageId == msgId);
                    if (existing != null)
                    {
                        existing.Status = message.Status;
                        // Trigger UI update via property change
                        var idx = Messages.IndexOf(existing);
                        if (idx >= 0) { Messages.RemoveAt(idx); Messages.Insert(idx, existing); }
                    }
                });
                return;
            }

            // A real message means this sender stopped typing
            UpdateTypingStatus(message.Sender, add: false);
            if (message.IsDirectMessage)
                message.Content = $"[DM] {message.Content}";
            DispatchUI(() => Messages.Add(message));
            SystemSounds.Asterisk.Play();
        }

        private void OnConnectionStateChanged(string state)
        {
            DispatchUI(() =>
            {
                IsConnected = _webSocketService.IsConnected;
                ConnectionStatus = state switch
                {
                    "Connected" => "Connected",
                    "Connecting" => "Connecting...",
                    _ => "Disconnected"
                };
            });

            if (state == "Connected")
                StartOnlineUsersPolling();
            else if (state == "Disconnected")
                StopOnlineUsersPolling();
        }

        private void OnErrorOccurred(string error)
        {
            Logger.Error($"Service error: {error}");
            DispatchUI(() =>
            {
                ConnectionStatus = $"Error: {error}";
                IsConnected = false;
            });

            // Auto-reconnect on unexpected connection loss
            if (!_userDisconnected && error.Contains("Connection lost"))
            {
                _ = StartReconnectAsync();
            }
        }

        // ── Typing Indicator ──────────────────────────────

        private void OnMessageInputChanged(string text)
        {
            if (!IsConnected) return;

            if (!string.IsNullOrEmpty(text))
            {
                var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                if (now - _lastTypingSentTime >= TypingDebounceMs)
                {
                    _lastTypingSentTime = now;
                    _ = SendTypingIndicatorAsync(true);
                }

                // Schedule a "stop" if user pauses typing
                _typingDebounceCts?.Cancel();
                _typingDebounceCts?.Dispose();
                _typingDebounceCts = new CancellationTokenSource();
                var cts = _typingDebounceCts;
                _ = Task.Run(async () =>
                {
                    try
                    {
                        await Task.Delay((int)TypingDebounceMs, cts.Token);
                        await SendTypingIndicatorAsync(false);
                        _lastTypingSentTime = 0;
                    }
                    catch (OperationCanceledException) { /* debounce reset */ }
                });
            }
            else
            {
                SendTypingStopSync();
            }
        }

        private void HandleIncomingTypingIndicator(ChatMessage message)
        {
            var sender = message.Sender;

            if (message.Content == "start")
            {
                if (_typingExpireCts.TryGetValue(sender, out var oldCts))
                {
                    oldCts.Cancel();
                    oldCts.Dispose();
                }

                var cts = new CancellationTokenSource();
                _typingExpireCts[sender] = cts;
                UpdateTypingStatus(sender, add: true);

                _ = Task.Run(async () =>
                {
                    try
                    {
                        await Task.Delay((int)TypingExpireMs, cts.Token);
                        UpdateTypingStatus(sender, add: false);
                        _typingExpireCts.Remove(sender);
                    }
                    catch (OperationCanceledException) { /* cancelled */ }
                });
            }
            else
            {
                if (_typingExpireCts.TryGetValue(sender, out var cts))
                {
                    cts.Cancel();
                    cts.Dispose();
                    _typingExpireCts.Remove(sender);
                }
                UpdateTypingStatus(sender, add: false);
            }
        }

        private void UpdateTypingStatus(string sender, bool add)
        {
            lock (_typingUserSet)
            {
                if (add) _typingUserSet.Add(sender);
                else _typingUserSet.Remove(sender);

                var text = _typingUserSet.Count switch
                {
                    0 => string.Empty,
                    1 => $"{_typingUserSet.First()} is typing...",
                    2 => $"{string.Join(" and ", _typingUserSet)} are typing...",
                    _ => $"{string.Join(", ", _typingUserSet.Take(2))} and {_typingUserSet.Count - 2} more are typing..."
                };

                DispatchUI(() => TypingStatus = text);
            }
        }

        private async Task SendTypingIndicatorAsync(bool isTyping)
        {
            var senderName = string.IsNullOrWhiteSpace(Username) ? "papa-1" : Username.Trim();
            var message = new ChatMessage
            {
                Sender = senderName,
                Content = isTyping ? "start" : "stop",
                Type = "typing"
            };
            await _webSocketService.SendMessageAsync(message);
        }

        private void SendTypingStopSync()
        {
            _typingDebounceCts?.Cancel();
            _typingDebounceCts?.Dispose();
            _typingDebounceCts = null;
            _lastTypingSentTime = 0;
            _ = SendTypingIndicatorAsync(false);
        }

        private void ClearTypingState()
        {
            foreach (var cts in _typingExpireCts.Values)
            {
                cts.Cancel();
                cts.Dispose();
            }
            _typingExpireCts.Clear();
            lock (_typingUserSet) { _typingUserSet.Clear(); }
            DispatchUI(() => TypingStatus = string.Empty);
        }

        // ── Online Users Polling ─────────────────────────

        private void StartOnlineUsersPolling()
        {
            StopOnlineUsersPolling();
            var cts = new CancellationTokenSource();
            _onlineUsersCts = cts;
            _ = Task.Run(async () =>
            {
                while (!cts.Token.IsCancellationRequested)
                {
                    try
                    {
                        var response = await _httpClient.GetStringAsync($"http://{_lastHost}:{_lastPort}/clients");
                        var users = JsonSerializer.Deserialize<List<string>>(response) ?? new List<string>();
                        DispatchUI(() =>
                        {
                            OnlineUsers.Clear();
                            foreach (var u in users) OnlineUsers.Add(u);
                            OnlineUsersText = users.Count > 0 ? $"{users.Count} online" : string.Empty;
                        });
                    }
                    catch { /* ignore polling errors */ }

                    try { await Task.Delay((int)OnlineUsersPollMs, cts.Token); }
                    catch (OperationCanceledException) { break; }
                }
            });
        }

        private void StopOnlineUsersPolling()
        {
            _onlineUsersCts?.Cancel();
            _onlineUsersCts?.Dispose();
            _onlineUsersCts = null;
            DispatchUI(() =>
            {
                OnlineUsers.Clear();
                OnlineUsersText = string.Empty;
            });
        }

        // ── Reconnect ─────────────────────────────────────

        /// <summary>
        /// Attempts to reconnect with exponential backoff.
        /// Messages are preserved during reconnection attempts.
        /// </summary>
        private async Task StartReconnectAsync()
        {
            CancelReconnect();
            var cts = new CancellationTokenSource();
            _reconnectCts = cts;

            try
            {
                for (var attempt = 1; attempt <= MaxReconnectAttempts; attempt++)
                {
                    cts.Token.ThrowIfCancellationRequested();

                    DispatchUI(() =>
                    {
                        ConnectionStatus = $"Reconnecting ({attempt}/{MaxReconnectAttempts})...";
                        _connectCommand.RaiseCanExecuteChanged();
                        _disconnectCommand.RaiseCanExecuteChanged();
                    });

                    var backoff = Math.Min(InitialBackoffMs * (1 << (attempt - 1)), MaxBackoffMs);
                    await Task.Delay(backoff, cts.Token);

                    if (_userDisconnected) return;

                    await _webSocketService.ConnectAsync(_lastHost, _lastPort, _lastClientId, _lastRoomId);

                    // Wait briefly for connection to establish
                    await Task.Delay(2000, cts.Token);

                    if (_webSocketService.IsConnected) return;
                }

                // All attempts exhausted
                DispatchUI(() =>
                {
                    ConnectionStatus = $"Reconnect failed after {MaxReconnectAttempts} attempts";
                    _connectCommand.RaiseCanExecuteChanged();
                });
            }
            catch (OperationCanceledException) { /* reconnect cancelled */ }
            catch (Exception ex)
            {
                Logger.Error("Reconnect error", ex);
            }
        }

        private void CancelReconnect()
        {
            _reconnectCts?.Cancel();
            _reconnectCts?.Dispose();
            _reconnectCts = null;
        }

        // ── Theme ─────────────────────────────────────────

        private void ToggleTheme()
        {
            IsDarkMode = !IsDarkMode;
            ApplyTheme(IsDarkMode);
        }

        private static void ApplyTheme(bool dark)
        {
            var res = Application.Current.Resources;
            if (dark)
            {
                res["BgBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1B1B1F"));
                res["SurfaceBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#2B2B30"));
                res["SurfaceVariantBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#3A3A40"));
                res["PrimaryBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#4DB6AC"));
                res["OnBgBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#E0E0E0"));
                res["OnSurfaceBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#CCCCCC"));
                res["BorderBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#444444"));
            }
            else
            {
                res["BgBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#F5F5F5"));
                res["SurfaceBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#FFFFFF"));
                res["SurfaceVariantBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#E0E0E0"));
                res["PrimaryBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#00897B"));
                res["OnBgBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#212121"));
                res["OnSurfaceBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#424242"));
                res["BorderBrush"] = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#BDBDBD"));
            }
        }

        // ── Helpers ─────────────────────────────────────────

        private static void DispatchUI(Action action)
        {
            var app = Application.Current;
            if (app == null) return;

            if (app.Dispatcher.CheckAccess())
                action();
            else
                app.Dispatcher.BeginInvoke(action);
        }

        public void Dispose()
        {
            _typingDebounceCts?.Cancel();
            _typingDebounceCts?.Dispose();
            ClearTypingState();
            StopOnlineUsersPolling();
            CancelReconnect();
            _httpClient.Dispose();
            _webSocketService.OnMessageReceived -= OnMessageReceived;
            _webSocketService.OnConnectionStateChanged -= OnConnectionStateChanged;
            _webSocketService.OnError -= OnErrorOccurred;
            _webSocketService.Dispose();
            _audioPlayer.Dispose();
            _audioRecorder.Dispose();
            GC.SuppressFinalize(this);
        }
    }
}
