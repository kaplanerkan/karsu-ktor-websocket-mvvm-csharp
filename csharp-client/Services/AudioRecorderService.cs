using System.IO;
using NAudio.Wave;

namespace ChatClientWpf.Services
{
    /// <summary>
    /// Records audio from the default microphone using NAudio.
    /// Produces Base64-encoded WAV data suitable for WebSocket transmission.
    /// </summary>
    public class AudioRecorderService : IDisposable
    {
        private WaveInEvent? _waveIn;
        private MemoryStream? _rawStream;
        private WaveFormat? _waveFormat;
        private DateTime _recordStartTime;

        public bool IsRecording { get; private set; }

        /// <summary>
        /// Starts recording from the default microphone.
        /// Audio is captured as 44.1 kHz, 16-bit, mono PCM.
        /// </summary>
        public void Start()
        {
            if (IsRecording) return;

            _rawStream = new MemoryStream();
            _waveFormat = new WaveFormat(44100, 16, 1);
            _waveIn = new WaveInEvent { WaveFormat = _waveFormat };
            _waveIn.DataAvailable += OnDataAvailable;

            _recordStartTime = DateTime.UtcNow;
            _waveIn.StartRecording();
            IsRecording = true;
        }

        /// <summary>
        /// Stops recording and returns Base64-encoded WAV data with duration.
        /// Returns null if the recording was shorter than 500 ms.
        /// </summary>
        public (string base64Audio, long durationMs)? Stop()
        {
            if (!IsRecording) return null;
            IsRecording = false;

            var durationMs = (long)(DateTime.UtcNow - _recordStartTime).TotalMilliseconds;

            try
            {
                _waveIn!.StopRecording();

                // Ignore very short recordings (accidental clicks)
                if (durationMs < 500)
                {
                    Cleanup();
                    return null;
                }

                var rawData = _rawStream!.ToArray();
                var format = _waveFormat!;
                Cleanup();

                // Build WAV file in memory from raw PCM data
                var wavStream = new MemoryStream();
                using (var writer = new WaveFileWriter(wavStream, format))
                {
                    writer.Write(rawData, 0, rawData.Length);
                }

                // ToArray() works even after WaveFileWriter closes the stream
                var base64 = Convert.ToBase64String(wavStream.ToArray());
                return (base64, durationMs);
            }
            catch (Exception ex)
            {
                Logger.Error("AudioRecorder.Stop failed", ex);
                Cleanup();
                return null;
            }
        }

        private void OnDataAvailable(object? sender, WaveInEventArgs e)
        {
            _rawStream?.Write(e.Buffer, 0, e.BytesRecorded);
        }

        private void Cleanup()
        {
            try { _waveIn?.Dispose(); } catch { /* ignore */ }
            try { _rawStream?.Dispose(); } catch { /* ignore */ }
            _waveIn = null;
            _rawStream = null;
            _waveFormat = null;
        }

        public void Dispose()
        {
            if (IsRecording)
            {
                try { _waveIn?.StopRecording(); } catch { /* ignore */ }
                IsRecording = false;
            }

            Cleanup();
            GC.SuppressFinalize(this);
        }
    }
}
