using System.IO;

namespace ChatClientWpf.Services
{
    public static class Logger
    {
        private static readonly string LogDirectory = Path.Combine(
            AppDomain.CurrentDomain.BaseDirectory, "logs");

        private static readonly object Lock = new();

        static Logger()
        {
            Directory.CreateDirectory(LogDirectory);
        }

        private static string GetLogFilePath() =>
            Path.Combine(LogDirectory, $"chatclient_{DateTime.Now:yyyy-MM-dd}.log");

        public static void Info(string message) => Write("INFO", message);
        public static void Error(string message) => Write("ERROR", message);
        public static void Error(string message, Exception ex) =>
            Write("ERROR", $"{message}: {ex.Message}\n{ex.StackTrace}");

        private static void Write(string level, string message)
        {
            var line = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] [{level}] {message}";
            // Fire-and-forget async write to avoid blocking UI thread
            Task.Run(() =>
            {
                lock (Lock)
                {
                    try
                    {
                        File.AppendAllText(GetLogFilePath(), line + Environment.NewLine);
                    }
                    catch { /* Don't crash on log failure */ }
                }
            });
        }
    }
}
