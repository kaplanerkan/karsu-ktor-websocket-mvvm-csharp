using System.Windows;
using ChatClientWpf.Services;

namespace ChatClientWpf
{
    public partial class App : Application
    {
        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);

            DispatcherUnhandledException += (_, args) =>
            {
                Logger.Error("UI thread exception", args.Exception);
                args.Handled = true;
            };

            AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            {
                if (args.ExceptionObject is Exception ex)
                    Logger.Error("Unhandled domain exception", ex);
            };

            TaskScheduler.UnobservedTaskException += (_, args) =>
            {
                Logger.Error("Unobserved task exception", args.Exception);
                args.SetObserved();
            };

            Logger.Info("Application started");
        }
    }
}
