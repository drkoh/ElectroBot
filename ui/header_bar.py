from PyQt6.QtWidgets import (
    QFrame, QHBoxLayout, QLabel, QPushButton, QSpacerItem, QSizePolicy
)
from PyQt6.QtCore import pyqtSignal, Qt

class HeaderBar(QFrame):
    theme_toggled = pyqtSignal()
    console_toggled = pyqtSignal()
    refresh_requested = pyqtSignal()
    mini_widget_toggled = pyqtSignal()
    edit_address_requested = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("HeaderBar")
        self.setFixedHeight(48)
        
        layout = QHBoxLayout(self)
        layout.setContentsMargins(16, 0, 16, 0)
        layout.setSpacing(10)
        
        # 1. Left side: Brand, Badge, Breadcrumb
        self.lbl_brand = QLabel("⚡ ElectroBot")
        self.lbl_brand.setStyleSheet("font-size: 16px; font-weight: 800; color: #1f6feb;")
        
        self.lbl_badge = QLabel("v2.0 M3")
        self.lbl_badge.setObjectName("BadgePill")
        
        self.lbl_sep = QLabel("/")
        self.lbl_sep.setStyleSheet("color: #687182; font-weight: 400;")
        
        self.lbl_breadcrumb = QLabel("Пошук адреси та черги")
        self.lbl_breadcrumb.setStyleSheet("color: #9ba3b4; font-size: 13px; font-weight: 500;")
        
        layout.addWidget(self.lbl_brand)
        layout.addWidget(self.lbl_badge)
        layout.addWidget(self.lbl_sep)
        layout.addWidget(self.lbl_breadcrumb)
        
        layout.addSpacerItem(QSpacerItem(30, 20, QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum))
        
        # 2. Middle-Right: Active Address Quick Button (with edit pencil)
        self.btn_active_addr = QPushButton("📍 вул. Дудинської ✏️")
        self.btn_active_addr.setObjectName("SecondaryButton")
        self.btn_active_addr.setToolTip("Клікніть для швидкої зміни або редагування адреси")
        self.btn_active_addr.clicked.connect(self.edit_address_requested.emit)
        layout.addWidget(self.btn_active_addr)
        
        # 3. Right side: Live Queue Indicator Pill, Refresh, Mini-Widget, Theme, Console
        self.lbl_status = QLabel("🟢 Ч.1.1: Світло є")
        self.lbl_status.setObjectName("StatusOnBadge")
        layout.addWidget(self.lbl_status)
        
        self.btn_refresh = QPushButton("🔄 Оновити")
        self.btn_refresh.setObjectName("SecondaryButton")
        self.btn_refresh.setToolTip("Оновити графік з офіційного Telegram Харківобленерго")
        self.btn_refresh.clicked.connect(self.refresh_requested.emit)
        layout.addWidget(self.btn_refresh)
        
        self.btn_mini = QPushButton("📌 Віджет")
        self.btn_mini.setObjectName("SecondaryButton")
        self.btn_mini.setToolTip("Відкрити/сховати плаваючий міні-віджет на робочому столі")
        self.btn_mini.clicked.connect(self.mini_widget_toggled.emit)
        layout.addWidget(self.btn_mini)
        
        self.btn_theme = QPushButton("🌙 Темна")
        self.btn_theme.setObjectName("SecondaryButton")
        self.btn_theme.setToolTip("Перемкнути світлу/темну тему")
        self.btn_theme.clicked.connect(self.theme_toggled.emit)
        layout.addWidget(self.btn_theme)
        
        self.btn_console = QPushButton("🖥️ Консоль")
        self.btn_console.setObjectName("SecondaryButton")
        self.btn_console.setToolTip("Показати/сховати системний журнал")
        self.btn_console.clicked.connect(self.console_toggled.emit)
        layout.addWidget(self.btn_console)

    def set_breadcrumb(self, text: str):
        self.lbl_breadcrumb.setText(text)

    def set_active_address_text(self, addr_text: str):
        clean = addr_text.replace("Харків,", "").replace("Харків", "").strip()
        display = f"📍 {clean} ✏️" if clean else "📍 Обрати адресу ✏️"
        self.btn_active_addr.setText(display)

    def update_status(self, is_on: bool, queue_name: str, text: str):
        full_text = text
        if "ЕКСТРЕНІ" in text or "ГАВ" in text:
            display_text = f"🚨 Ч.{queue_name}: ГАВ"
            self.lbl_status.setObjectName("StatusOffBadge")
        elif "1562" in text or "Аварія" in text:
            display_text = f"🚨 Ч.{queue_name}: 1562"
            self.lbl_status.setObjectName("StatusOffBadge")
        elif is_on:
            display_text = f"🟢 Ч.{queue_name}: Світло є"
            self.lbl_status.setObjectName("StatusOnBadge")
        else:
            display_text = f"🔴 Ч.{queue_name}: Відключено"
            self.lbl_status.setObjectName("StatusOffBadge")
            
        self.lbl_status.setText(display_text)
        self.lbl_status.setToolTip(f"Черга {queue_name}: {full_text}")
        self.lbl_status.style().unpolish(self.lbl_status)
        self.lbl_status.style().polish(self.lbl_status)

    def update_station_status(self, tel):
        if not tel or not tel.is_configured:
            return
        if tel.is_grid_online:
            display_text = f"🟢 220В Є | 🔋 {tel.battery_percent}%"
            self.lbl_status.setObjectName("StatusOnBadge")
        else:
            display_text = f"🔴 220В Немає | 🔋 {tel.battery_percent}%"
            self.lbl_status.setObjectName("StatusOffBadge")
        self.lbl_status.setText(display_text)
        self.lbl_status.setToolTip(f"{tel.model_name}: {tel.status_details}")
        self.lbl_status.style().unpolish(self.lbl_status)
        self.lbl_status.style().polish(self.lbl_status)

    def set_theme_button_text(self, is_dark: bool):
        self.btn_theme.setText("🌙 Темна" if is_dark else "☀️ Світла")
