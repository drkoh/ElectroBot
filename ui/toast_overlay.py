from PyQt6.QtWidgets import QWidget, QHBoxLayout, QVBoxLayout, QLabel, QPushButton, QGraphicsDropShadowEffect
from PyQt6.QtCore import Qt, QTimer, QPropertyAnimation, QEasingCurve, QPoint
from PyQt6.QtGui import QColor

class InAppToast(QWidget):
    """
    Material 3 In-App floating Toast notification.
    Anchored to the bottom-right corner inside the parent window.
    Never clips, never says 'Python', auto-dismisses after 3.5 seconds.
    """
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowFlags(Qt.WindowType.SubWindow)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        
        self.container = QWidget(self)
        self.container.setObjectName("ToastContainer")
        
        c_layout = QHBoxLayout(self.container)
        c_layout.setContentsMargins(14, 10, 10, 10)
        c_layout.setSpacing(10)
        
        self.lbl_icon = QLabel("⚡")
        self.lbl_icon.setStyleSheet("font-size: 18px;")
        
        text_layout = QVBoxLayout()
        text_layout.setSpacing(2)
        
        self.lbl_title = QLabel("ElectroBot")
        self.lbl_title.setStyleSheet("font-weight: 700; font-size: 13px;")
        
        self.lbl_message = QLabel("Сповіщення")
        self.lbl_message.setStyleSheet("font-size: 12px;")
        self.lbl_message.setWordWrap(True)
        
        text_layout.addWidget(self.lbl_title)
        text_layout.addWidget(self.lbl_message)
        
        self.btn_close = QPushButton("✕")
        self.btn_close.setFixedSize(22, 22)
        self.btn_close.setStyleSheet("background: transparent; border: none; font-size: 11px; cursor: pointer;")
        self.btn_close.clicked.connect(self.hide_toast)
        
        c_layout.addWidget(self.lbl_icon)
        c_layout.addLayout(text_layout)
        c_layout.addWidget(self.btn_close)
        
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.addWidget(self.container)
        
        # Drop shadow
        shadow = QGraphicsDropShadowEffect(self)
        shadow.setBlurRadius(16)
        shadow.setColor(QColor(0, 0, 0, 90))
        shadow.setOffset(0, 4)
        self.container.setGraphicsEffect(shadow)
        
        self.timer = QTimer(self)
        self.timer.setSingleShot(True)
        self.timer.timeout.connect(self.hide_toast)
        
        self.hide()

    def show_toast(self, title: str, message: str, toast_type: str = "INFO", duration_ms: int = 4000, is_dark: bool = True):
        self.lbl_title.setText(title)
        self.lbl_message.setText(message)
        
        # Color styling based on toast_type and theme
        if toast_type == "SUCCESS":
            self.lbl_icon.setText("✅")
            bg = "#1c3d28" if is_dark else "#dcfce7"
            border = "#238636" if is_dark else "#86efac"
            text_c = "#3fb950" if is_dark else "#15803d"
            msg_c = "#e6edf3" if is_dark else "#14532d"
        elif toast_type == "WARNING":
            self.lbl_icon.setText("⚠️")
            bg = "#3d321a" if is_dark else "#fef3c7"
            border = "#d29922" if is_dark else "#fde68a"
            text_c = "#d29922" if is_dark else "#b45309"
            msg_c = "#e6edf3" if is_dark else "#78350f"
        elif toast_type == "ERROR":
            self.lbl_icon.setText("🚨")
            bg = "#3d1e22" if is_dark else "#fee2e2"
            border = "#da3633" if is_dark else "#fca5a5"
            text_c = "#f85149" if is_dark else "#991b1b"
            msg_c = "#e6edf3" if is_dark else "#7f1d1d"
        else: # INFO
            self.lbl_icon.setText("⚡")
            bg = "#223249" if is_dark else "#e0edff"
            border = "#388bfd" if is_dark else "#93c5fd"
            text_c = "#58a6ff" if is_dark else "#1d4ed8"
            msg_c = "#e6edf3" if is_dark else "#1e3a8a"
            
        self.container.setStyleSheet(f"""
            QWidget#ToastContainer {{
                background-color: {bg};
                border: 1.5px solid {border};
                border-radius: 12px;
            }}
        """)
        self.lbl_title.setStyleSheet(f"font-weight: 800; font-size: 13px; color: {text_c};")
        self.lbl_message.setStyleSheet(f"font-size: 12px; color: {msg_c};")
        self.btn_close.setStyleSheet(f"background: transparent; border: none; font-size: 11px; color: {text_c}; font-weight: bold;")
        
        self.adjustSize()
        self.reposition()
        self.show()
        self.raise_()
        self.timer.start(duration_ms)

    def hide_toast(self):
        self.timer.stop()
        self.hide()

    def reposition(self):
        if self.parent():
            p_w = self.parent().width()
            p_h = self.parent().height()
            # Position at bottom-right corner with 24px margins
            toast_w = max(300, min(self.width(), 440))
            self.resize(toast_w, self.height())
            x = p_w - toast_w - 24
            y = p_h - self.height() - 24
            self.move(max(10, x), max(10, y))
