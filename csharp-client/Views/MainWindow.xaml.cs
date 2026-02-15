using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using ChatClientWpf.ViewModels;

namespace ChatClientWpf
{
    public partial class MainWindow : Window
    {
        private static readonly string[] Emojis =
        {
            "\U0001F600", "\U0001F602", "\U0001F60D", "\U0001F970", "\U0001F60E",
            "\U0001F914", "\U0001F62E", "\U0001F622", "\U0001F621", "\U0001F973",
            "\U0001F44D", "\U0001F44E", "\U0001F44F", "\U0001F64F", "\U0001F4AA",
            "\U0001F91D", "\u2764\uFE0F", "\U0001F525", "\u2B50", "\U0001F389",
            "\u2705", "\u274C", "\U0001F4AF", "\U0001F680", "\U0001F4A1",
            "\U0001F3B5", "\U0001F4F8", "\U0001F3C6", "\U0001F31F", "\U0001F4AC",
            "\U0001F60A", "\U0001F601", "\U0001F923", "\U0001F605", "\U0001F607",
            "\U0001F97A", "\U0001F60F", "\U0001F634", "\U0001F92E", "\U0001F92F",
            "\U0001F44B", "\u270C\uFE0F", "\U0001F91E", "\U0001F590\uFE0F", "\U0001F440",
            "\U0001F480", "\U0001F921", "\U0001F47B", "\U0001F4A9", "\U0001F648"
        };

        public MainWindow()
        {
            InitializeComponent();

            BtnMic.PreviewMouseLeftButtonDown += OnMicMouseDown;
            BtnMic.PreviewMouseLeftButtonUp += OnMicMouseUp;
            BtnMic.MouseLeave += OnMicMouseLeave;

            // Populate emoji panel
            foreach (var emoji in Emojis)
            {
                var btn = new Button
                {
                    Content = emoji,
                    FontSize = 18,
                    Width = 36,
                    Height = 36,
                    Background = System.Windows.Media.Brushes.Transparent,
                    BorderThickness = new Thickness(0),
                    Cursor = Cursors.Hand,
                    Margin = new Thickness(1)
                };
                btn.Click += (_, _) =>
                {
                    if (DataContext is ChatViewModel vm)
                    {
                        var pos = TxtMessage.CaretIndex;
                        vm.MessageInput = vm.MessageInput.Insert(pos >= 0 ? pos : vm.MessageInput.Length, emoji);
                        TxtMessage.CaretIndex = pos + emoji.Length;
                    }
                    EmojiPopup.IsOpen = false;
                    TxtMessage.Focus();
                };
                EmojiPanel.Children.Add(btn);
            }
        }

        private void OnMicMouseDown(object sender, MouseButtonEventArgs e)
        {
            if (DataContext is ChatViewModel vm)
                vm.OnRecordStart();
        }

        private void OnMicMouseUp(object sender, MouseButtonEventArgs e)
        {
            if (DataContext is ChatViewModel vm && vm.IsRecording)
                vm.OnRecordStop();
        }

        private void OnMicMouseLeave(object sender, MouseEventArgs e)
        {
            if (DataContext is ChatViewModel vm && vm.IsRecording)
                vm.OnRecordStop();
        }

        private void OnEmojiClick(object sender, RoutedEventArgs e)
        {
            EmojiPopup.IsOpen = !EmojiPopup.IsOpen;
        }

        private void OnOnlineUsersClick(object sender, MouseButtonEventArgs e)
        {
            if (DataContext is not ChatViewModel vm || vm.OnlineUsers.Count == 0) return;

            var menu = new System.Windows.Controls.ContextMenu();
            var broadcastItem = new System.Windows.Controls.MenuItem { Header = "Broadcast (All)" };
            broadcastItem.Click += (_, _) => vm.DmTarget = null;
            menu.Items.Add(broadcastItem);
            menu.Items.Add(new System.Windows.Controls.Separator());

            foreach (var user in vm.OnlineUsers)
            {
                var item = new System.Windows.Controls.MenuItem { Header = $"DM: {user}" };
                var u = user;
                item.Click += (_, _) => vm.DmTarget = u;
                menu.Items.Add(item);
            }

            menu.IsOpen = true;
        }

        protected override void OnClosed(EventArgs e)
        {
            (DataContext as IDisposable)?.Dispose();
            base.OnClosed(e);
        }
    }
}
