using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace ElectroBotLauncher
{
    static class Program
    {
        [DllImport("user32.dll")]
        static extern bool AllowSetForegroundWindow(int dwProcessId);

        [STAThread]
        static void Main()
        {
            string appDir = AppDomain.CurrentDomain.BaseDirectory;
            string scriptPath = Path.Combine(appDir, "run.py");

            if (!File.Exists(scriptPath))
            {
                MessageBox.Show("Не знайдено головний скрипт програми: " + scriptPath, "ElectroBot Помилка", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            // Look for pythonw.exe first (no console window), fallback to python.exe
            string pythonExe = "pythonw.exe";
            
            // Check common paths or PATH
            string venvPython = Path.Combine(appDir, "venv", "Scripts", "pythonw.exe");
            if (File.Exists(venvPython))
            {
                pythonExe = venvPython;
            }

            ProcessStartInfo psi = new ProcessStartInfo();
            psi.FileName = pythonExe;
            psi.Arguments = "\"" + scriptPath + "\"";
            psi.WorkingDirectory = appDir;
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.WindowStyle = ProcessWindowStyle.Hidden;

            try
            {
                Process proc = Process.Start(psi);
                if (proc != null)
                {
                    AllowSetForegroundWindow(proc.Id);
                }
                else
                {
                    AllowSetForegroundWindow(-1);
                }
            }
            catch (Exception ex)
            {
                // Fallback to python.exe if pythonw wasn't found directly
                psi.FileName = "python.exe";
                try
                {
                    Process proc = Process.Start(psi);
                    AllowSetForegroundWindow(proc != null ? proc.Id : -1);
                }
                catch (Exception ex2)
                {
                    MessageBox.Show("Не вдалося запустити Python: " + ex2.Message + "\nПереконайтеся, що Python 3 встановлено в системі.", "ElectroBot Помилка", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
            }
        }
    }
}
