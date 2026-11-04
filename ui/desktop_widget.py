from PyQt6.QtWidgets import (
    QWidget, QHBoxLayout, QLabel, QPushButton, QGraphicsDropShadowEffect
)
from PyQt6.QtCore import Qt, QPoint, pyqtSignal
from PyQt6.QtGui import QColor, QMouseEvent

class DesktopWidget(QWidget):
    """
    Compact, draggable, Always-on-Top floating mini-widget
    displaying power status, active queue, and live countdown.
    """
    open_main_requested = pyqtSignal()
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowFlags(
            Qt.WindowType.FramelessWindowHint |
            Qt.WindowType.WindowStaysOnTopHint |
            Qt.WindowType.Tool
        )
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        
        self._drag_pos = QPoint()
        self.init_ui()
        self.resize(380, 52)
        
    def init_ui(self):
        main_layout = QHBoxLayout(self)
        main_layout.setContentsMargins(6, 6, 6, 6)
        
        # Inner container card
        self.container = QWidget(self)
        self.container.setObjectName("MiniWidgetContainer")
        self.container_layout = QHBoxLayout(self.container)
        self.container_layout.setContentsMargins(12, 6, 8, 6)
        self.container_layout.setSpacing(10)
        
        # Queue Pill
        self.lbl_queue = QLabel("⚡ 1.1")
        self.lbl_queue.setObjectName("MiniQueuePill")
        self.lbl_queue.setFixedHeight(26)
        self.container_layout.addWidget(self.lbl_queue)
        
        # Status Badge
        self.lbl_status = QLabel("🟢 Світло є")
        self.lbl_status.setObjectName("MiniStatusBadge")
        self.lbl_status.setFixedHeight(26)
        self.container_layout.addWidget(self.lbl_status)
        
        # Countdown
        self.lbl_countdown = QLabel("⏳ --:--:--")
        self.lbl_countdown.setObjectName("MiniCountdownText")
        self.container_layout.addWidget(self.lbl_countdown)
        
        self.container_layout.addStretch()
        
        # Open Main Window Button
        self.btn_open = QPushButton("🗖")
        self.btn_open.setToolTip("Відкрити головне вікно")
        self.btn_open.setFixedSize(26, 26)
        self.btn_open.setObjectName("MiniIconButton")
        self.btn_open.clicked.connect(self.open_main_requested.emit)
        self.container_layout.addWidget(self.btn_open)
        
        # Close / Hide Button
        self.btn_close = QPushButton("✕")
        self.btn_close.setToolTip("Приховати міні-віджет")
        self.btn_close.setFixedSize(26, 26)
        self.btn_close.setObjectName("MiniIconButton")
        self.btn_close.clicked.connect(self.hide)
        self.container_layout.addWidget(self.btn_close)
        
        main_layout.addWidget(self.container)
        
        # Drop shadow
        shadow = QGraphicsDropShadowEffect(self)
        shadow.setBlurRadius(16)
        shadow.setColor(QColor(0, 0, 0, 80))
        shadow.setOffset(0, 3)
        self.container.setGraphicsEffect(shadow)

    def update_data(self, queue_name: str, badge_text: str, badge_theme: str, countdown_text: str):
        self.lbl_queue.setText(f"⚡ {queue_name}")
        self.lbl_status.setText(badge_text)
        self.lbl_countdown.setText(f"⏳ {countdown_text}")
        
        # Set badge styling based on theme
        if badge_theme == "danger":
            self.lbl_status.setStyleSheet("background-color: #da3633; color: #ffffff; border-radius: 4px; padding: 2px 8px; font-weight: bold; font-size: 11px;")
        elif badge_theme == "warning":
            self.lbl_status.setStyleSheet("background-color: #d29922; color: #1c1e24; border-radius: 4px; padding: 2px 8px; font-weight: bold; font-size: 11px;")
        elif badge_theme == "success":
            self.lbl_status.setStyleSheet("background-color: #238636; color: #ffffff; border-radius: 4px; padding: 2px 8px; font-weight: bold; font-size: 11px;")
        else:
            self.lbl_status.setStyleSheet("background-color: #388bfd; color: #ffffff; border-radius: 4px; padding: 2px 8px; font-weight: bold; font-size: 11px;")

    def mousePressEvent(self, event: QMouseEvent):
        if event.button() == Qt.MouseButton.LeftButton:
            self._drag_pos = event.globalPosition().toPoint() - self.frameGeometry().topLeft()
            event.accept()

    def mouseMoveEvent(self, event: QMouseEvent):
        if event.buttons() == Qt.MouseButton.LeftButton and not self._drag_pos.isNull():
            self.move(event.globalPosition().toPoint() - self._drag_pos)
            event.accept()
