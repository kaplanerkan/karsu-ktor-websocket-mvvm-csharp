using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Input;
using ChatClientWpf.Models;
using ChatClientWpf.Services;

namespace ChatClientWpf.ViewModels
{
    public class ChatViewModel : BaseViewModel, IDisposable
    {
        private const int MaxReconnectAttempts = 10;
        private const int InitialBackoffMs = 1000;
        private const int MaxBackoffMs = 30_000;

        private readonly WebSocketService _webSocketService;
        private readonly SettingsService _settings;
        private readonly AudioPlayerService _audioPlayer;

        private readonly RelayCommand _connectCommand;
        private readonly RelayCommand _disconnectCommand;
        private readonly RelayCommand _sendCommand;
        private readonly RelayCommand _playVoiceCommand;

        private bool _userDisconnected;
        private CancellationTokenSource? _reconnectCts;
        private string _lastHost = "";
        private int _lastPort = 8080;
        private string _lastClientId = "";

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

        private string _messageInput = string.Empty;
        public string MessageInput
        {
            get => _messageInput;
            set
            {
                if (SetProperty(ref _messageInput, value))
                    _sendCommand.RaiseCanExecuteChanged();
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

        public ObservableCollection<ChatMessage> Messages { get; } = new();

        // ── Commands (public ICommand for XAML binding) ─────

        public ICommand ConnectCommand => _connectCommand;
        public ICommand DisconnectCommand => _disconnectCommand;
        public ICommand SendCommand => _sendCommand;
        public ICommand PlayVoiceCommand => _playVoiceCommand;

        // ── Constructor ─────────────────────────────────────

        public ChatViewModel()
        {
            _webSocketService = new WebSocketService();
            _settings = SettingsService.Load();
            _audioPlayer = new AudioPlayerService();

            Host = _settings.Host ?? "127.0.0.1";
            Port = _settings.Port ?? "8080";
            Username = _settings.Username ?? "papa-1";

            _connectCommand = new RelayCommand(_ => ConnectAsync(), _ => !IsConnected);
            _disconnectCommand = new RelayCommand(_ => DisconnectAsync(), _ => IsConnected);
            _sendCommand = new RelayCommand(_ => SendAsync(), _ => IsConnected && !string.IsNullOrWhiteSpace(MessageInput));
            _playVoiceCommand = new RelayCommand(PlayVoice, _ => true);

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

                _lastHost = Host;
                _lastPort = port;
                _lastClientId = clientId;

                _settings.Host = Host;
                _settings.Port = Port;
                _settings.Username = clientId;
                _settings.Save();

                // Clear chat history on new connection
                DispatchUI(() => Messages.Clear());

                await _webSocketService.ConnectAsync(Host, port, clientId);
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

                var senderName = string.IsNullOrWhiteSpace(Username) ? "papa-1" : Username.Trim();
                var myMessage = new ChatMessage
                {
                    Sender = senderName,
                    Content = text,
                    IsFromMe = true
                };

                DispatchUI(() => Messages.Add(myMessage));
                await _webSocketService.SendMessageAsync(myMessage);
                MessageInput = string.Empty;
            }
            catch (Exception ex)
            {
                Logger.Error("SendAsync unhandled", ex);
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
            DispatchUI(() => Messages.Add(message));
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

                    await _webSocketService.ConnectAsync(_lastHost, _lastPort, _lastClientId);

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
            CancelReconnect();
            _webSocketService.OnMessageReceived -= OnMessageReceived;
            _webSocketService.OnConnectionStateChanged -= OnConnectionStateChanged;
            _webSocketService.OnError -= OnErrorOccurred;
            _webSocketService.Dispose();
            _audioPlayer.Dispose();
            GC.SuppressFinalize(this);
        }
    }
}
