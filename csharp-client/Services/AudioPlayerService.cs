using System.IO;
using System.Windows.Media;

namespace ChatClientWpf.Services
{
    /// <summary>
    /// Decodes Base64-encoded audio and plays it using WPF MediaPlayer.
    /// Manages temp file lifecycle for playback.
    /// </summary>
    public class AudioPlayerService : IDisposable
    {
        private readonly MediaPlayer _player = new();
        private string? _currentTempFile;

        public bool IsPlaying { get; private set; }

        public AudioPlayerService()
        {
            _player.MediaEnded += (_, _) => Stop();
        }

        /// <summary>
        /// Plays Base64-encoded audio data. Stops any current playback first.
        /// </summary>
        public void Play(string base64Audio)
        {
            Stop();

            try
            {
                var bytes = Convert.FromBase64String(base64Audio);
                var tempFile = Path.Combine(Path.GetTempPath(), $"karsu_play_{DateTime.Now.Ticks}.m4a");
                File.WriteAllBytes(tempFile, bytes);
                _currentTempFile = tempFile;

                _player.Open(new Uri(tempFile));
                _player.Play();
                IsPlaying = true;
            }
            catch (Exception ex)
            {
                Logger.Error("AudioPlayer.Play failed", ex);
                Stop();
            }
        }

        /// <summary>
        /// Stops playback and cleans up the temp file.
        /// </summary>
        public void Stop()
        {
            try
            {
                _player.Stop();
                _player.Close();
            }
            catch { /* ignore */ }

            IsPlaying = false;
            CleanupTempFile();
        }

        private void CleanupTempFile()
        {
            if (_currentTempFile == null) return;
            try { File.Delete(_currentTempFile); } catch { /* ignore */ }
            _currentTempFile = null;
        }

        public void Dispose()
        {
            Stop();
            GC.SuppressFinalize(this);
        }
    }
}
