from PyQt6.QtWidgets import QSystemTrayIcon, QMenu
from PyQt6.QtGui import QIcon, QPixmap, QPainter, QColor, QFont
from PyQt6.QtCore import pyqtSignal, QObject
from core.models import PowerStatus

class TrayManager(QObject):
    show_window_requested = pyqtSignal()
    refresh_requested = pyqtSignal()
    mini_widget_requested = pyqtSignal()
    exit_requested = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.tray_icon = QSystemTrayIcon(parent)
        self.current_status = PowerStatus.ON
        self.current_queue = "1.1"
        
        # Build context menu
        self.menu = QMenu()
        
        self.action_show = self.menu.addAction("⚡ Показати ElectroBot")
        self.action_show.triggered.connect(self.show_window_requested.emit)
        
        self.action_mini = self.menu.addAction("📌 Плаваючий міні-віджет")
        self.action_mini.triggered.connect(self.mini_widget_requested.emit)
        
        self.action_refresh = self.menu.addAction("🔄 Оновити графік")
        self.action_refresh.triggered.connect(self.refresh_requested.emit)
        
        self.action_queue_info = self.menu.addAction("📍 Черга: 1.1")
        self.action_queue_info.setEnabled(False)
        
        self.menu.addSeparator()
        
        self.action_exit = self.menu.addAction("✕ Вихід")
        self.action_exit.triggered.connect(self.exit_requested.emit)
        
        self.tray_icon.setContextMenu(self.menu)
        self.tray_icon.activated.connect(self._on_tray_activated)
        
        self.update_tray(PowerStatus.ON, "1.1", "Світло є")
        self.tray_icon.show()

    def _create_status_icon(self, status: PowerStatus) -> QIcon:
        """Dynamically generates a crisp 32x32 status icon with lightning bolt."""
        pixmap = QPixmap(32, 32)
        pixmap.fill(QColor(0, 0, 0, 0)) # transparent
        
        painter = QPainter(pixmap)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        
        if status == PowerStatus.ON:
            bg_color = QColor("#238636") # Green
        elif status == PowerStatus.SWITCHING:
            bg_color = QColor("#d29922") # Amber
        else:
            bg_color = QColor("#da3633") # Red
            
        # Draw background circle
        painter.setBrush(bg_color)
        painter.setPen(QColor("#ffffff"))
        painter.drawEllipse(2, 2, 28, 28)
        
        # Draw small lightning symbol in center
        font = QFont("Segoe UI", 12, QFont.Weight.Bold)
        painter.setFont(font)
        painter.setPen(QColor("#ffffff"))
        painter.drawText(0, 0, 32, 32, 0x0084, "⚡") # AlignCenter
        painter.end()
        
        return QIcon(pixmap)

    def update_tray(self, status: PowerStatus, queue_name: str, message: str):
        self.current_status = status
        self.current_queue = queue_name
        
        icon = self._create_status_icon(status)
        self.tray_icon.setIcon(icon)
        
        tooltip = f"⚡ ElectroBot: Черга {queue_name}\n{message}"
        self.tray_icon.setToolTip(tooltip)
        self.action_queue_info.setText(f"📍 Черга: {queue_name} ({message})")

    def show_notification(self, title: str, message: str, is_warning: bool = False):
        """Sends native Windows 10/11 Toast with AppUserModelId 'ElectroBot' so header shows ElectroBot."""
        import subprocess
        import os
        
        try:
            clean_title = title.replace('"', '`"').replace('$', '`$')
            clean_msg = message.replace('"', '`"').replace('$', '`$')
            
            ps_cmd = f"""
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null

            $xml = @"
            <toast>
                <visual>
                    <binding template="ToastGeneric">
                        <text>{clean_title}</text>
                        <text>{clean_msg}</text>
                    </binding>
                </visual>
            </toast>
            "@

            $toastXml = New-Object Windows.Data.Xml.Dom.XmlDocument
            $toastXml.LoadXml($xml)
            $toast = New-Object Windows.UI.Notifications.ToastNotification $toastXml
            [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("ElectroBot").Show($toast)
            """
            
            creationflags = getattr(subprocess, 'CREATE_NO_WINDOW', 0x08000000) if os.name == 'nt' else 0
            subprocess.Popen(
                ["powershell", "-NoProfile", "-NonInteractive", "-Command", ps_cmd],
                creationflags=creationflags,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
        except Exception:
            icon = QSystemTrayIcon.MessageIcon.Warning if is_warning else QSystemTrayIcon.MessageIcon.Information
            self.tray_icon.showMessage(title, message, icon, 6000)

    def _on_tray_activated(self, reason):
        if reason in (QSystemTrayIcon.ActivationReason.Trigger, QSystemTrayIcon.ActivationReason.DoubleClick):
            self.show_window_requested.emit()
