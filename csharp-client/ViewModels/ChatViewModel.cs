using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Input;
using ChatClientWpf.Models;
using ChatClientWpf.Services;

namespace ChatClientWpf.ViewModels
{
    public class ChatViewModel : BaseViewModel, IDisposable
    {
        private readonly WebSocketService _webSocketService;
        private readonly SettingsService _settings;

        private readonly RelayCommand _connectCommand;
        private readonly RelayCommand _disconnectCommand;
        private readonly RelayCommand _sendCommand;

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

        // ── Constructor ─────────────────────────────────────

        public ChatViewModel()
        {
            _webSocketService = new WebSocketService();
            _settings = SettingsService.Load();

            Host = _settings.Host;
            Port = _settings.Port;
            Username = _settings.Username;

            _connectCommand = new RelayCommand(_ => ConnectAsync(), _ => !IsConnected);
            _disconnectCommand = new RelayCommand(_ => DisconnectAsync(), _ => IsConnected);
            _sendCommand = new RelayCommand(_ => SendAsync(), _ => IsConnected && !string.IsNullOrWhiteSpace(MessageInput));

            _webSocketService.OnMessageReceived += OnMessageReceived;
            _webSocketService.OnConnectionStateChanged += OnConnectionStateChanged;
            _webSocketService.OnError += OnErrorOccurred;
        }

        // ── Command Handlers ────────────────────────────────

        private async void ConnectAsync()
        {
            try
            {
                var port = int.TryParse(Port, out var p) ? p : 8080;
                var clientId = string.IsNullOrWhiteSpace(Username) ? "papa-1" : Username.Trim();

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
            _webSocketService.Dispose();
        }
    }
}
