using System.IO;

namespace ChatClientWpf.Services
{
    public static class Logger
    {
        private static readonly string LogDirectory = Path.Combine(
            AppDomain.CurrentDomain.BaseDirectory, "logs");

        private static readonly string LogFile = Path.Combine(
            LogDirectory, $"chatclient_{DateTime.Now:yyyy-MM-dd}.log");

        private static readonly object Lock = new();

        static Logger()
        {
            Directory.CreateDirectory(LogDirectory);
        }

        public static void Info(string message) => Write("INFO", message);
        public static void Error(string message) => Write("ERROR", message);
        public static void Error(string message, Exception ex) =>
            Write("ERROR", $"{message}: {ex.Message}\n{ex.StackTrace}");

        private static void Write(string level, string message)
        {
            var line = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] [{level}] {message}";
            lock (Lock)
            {
                try
                {
                    File.AppendAllText(LogFile, line + Environment.NewLine);
                }
                catch { /* Don't crash on log failure */ }
            }
        }
    }
}
