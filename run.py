import sys
import os
import ctypes
from PyQt6.QtWidgets import QApplication
from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtGui import QIcon

# Ensure project root in sys.path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core.config import config
from ui.styles import generate_stylesheet
from ui.main_window import MainWindow

def setup_windows_appid():
    """Sets explicit Windows AppUserModelID for proper taskbar grouping and notifications."""
    if os.name == 'nt':
        try:
            myappid = 'ElectroBot.Kharkiv.PowerMonitor.v1'
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(myappid)
            user32 = ctypes.windll.user32
            user32.AllowSetForegroundWindow(-1)
        except Exception as e:
            print(f"Windows AppID error: {e}")

def ensure_foreground(window):
    """Z-Order flip (TOPMOST -> NOTOPMOST) to reliably bring window to foreground without sticking."""
    if os.name == 'nt':
        try:
            hwnd = int(window.winId())
            user32 = ctypes.windll.user32
            # HWND_TOPMOST (-1), SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW
            user32.SetWindowPos(hwnd, -1, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0040)
            # HWND_NOTOPMOST (-2)
            user32.SetWindowPos(hwnd, -2, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0040)
            user32.BringWindowToTop(hwnd)
            user32.SetForegroundWindow(hwnd)
        except Exception as e:
            print(f"Foreground flip error: {e}")

def main():
    setup_windows_appid()
    
    # High-DPI policy
    QApplication.setHighDpiScaleFactorRoundingPolicy(Qt.HighDpiScaleFactorRoundingPolicy.PassThrough)
    
    app = QApplication(sys.argv)
    app.setApplicationName("ElectroBot")
    app.setApplicationDisplayName("ElectroBot — Відключення світла Харків")
    app.setDesktopFileName("ElectroBot.lnk")
    
    # Set application icon if exists
    icon_path = os.path.join(os.path.dirname(__file__), "resources", "icons", "app_icon.ico")
    if os.path.exists(icon_path):
        app.setWindowIcon(QIcon(icon_path))
        
    # Apply theme stylesheet
    theme = config.get("theme", "dark")
    app.setStyleSheet(generate_stylesheet(theme))
    
    window = MainWindow()
    
    start_minimized = config.get("start_minimized_to_tray", False)
    if not start_minimized:
        window.show()
        # Foreground activation
        ensure_foreground(window)
        QTimer.singleShot(150, lambda: ensure_foreground(window))
        
    sys.exit(app.exec())

if __name__ == '__main__':
    main()
