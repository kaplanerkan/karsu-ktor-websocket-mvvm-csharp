using System.IO;
using System.Text.Json;

namespace ChatClientWpf.Services
{
    public class SettingsService
    {
        private static readonly string SettingsFile = Path.Combine(
            AppDomain.CurrentDomain.BaseDirectory, "settings.json");

        public string Host { get; set; } = "127.0.0.1";
        public string Port { get; set; } = "8080";
        public string Username { get; set; } = "papa-1";

        public static SettingsService Load()
        {
            try
            {
                if (File.Exists(SettingsFile))
                {
                    var json = File.ReadAllText(SettingsFile);
                    var result = JsonSerializer.Deserialize<SettingsService>(json) ?? new SettingsService();
                    result.Host ??= "127.0.0.1";
                    result.Port ??= "8080";
                    result.Username ??= "papa-1";
                    return result;
                }
            }
            catch (Exception ex)
            {
                Logger.Error("Failed to load settings", ex);
            }
            return new SettingsService();
        }

        public void Save()
        {
            try
            {
                var json = JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(SettingsFile, json);
                Logger.Info("Settings saved");
            }
            catch (Exception ex)
            {
                Logger.Error("Failed to save settings", ex);
            }
        }
    }
}
