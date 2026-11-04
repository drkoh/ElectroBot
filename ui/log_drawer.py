from PyQt6.QtWidgets import (
    QFrame, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QTextEdit
)
from PyQt6.QtGui import QTextCursor
from PyQt6.QtCore import pyqtSignal
from datetime import datetime

class LogDrawer(QFrame):
    close_requested = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("LogDrawer")
        self.setFixedHeight(140)
        
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 6, 12, 8)
        layout.setSpacing(6)
        
        # Header
        hdr = QHBoxLayout()
        hdr.setSpacing(8)
        
        lbl_title = QLabel("🖥️ Журнал подій та моніторингу")
        lbl_title.setStyleSheet("font-weight: 700; font-size: 12px; color: #9ba3b4;")
        
        self.btn_clear = QPushButton("Очистити")
        self.btn_clear.setObjectName("SecondaryButton")
        self.btn_clear.setFixedHeight(24)
        self.btn_clear.setStyleSheet("padding: 2px 8px; font-size: 11px;")
        self.btn_clear.clicked.connect(self.clear_logs)
        
        self.btn_close = QPushButton("✕")
        self.btn_close.setObjectName("SecondaryButton")
        self.btn_close.setFixedSize(24, 24)
        self.btn_close.setStyleSheet("padding: 0; font-size: 11px;")
        self.btn_close.clicked.connect(self.close_requested.emit)
        
        hdr.addWidget(lbl_title)
        hdr.addStretch()
        hdr.addWidget(self.btn_clear)
        hdr.addWidget(self.btn_close)
        layout.addLayout(hdr)
        
        # Text Console
        self.console = QTextEdit()
        self.console.setObjectName("LogConsole")
        self.console.setReadOnly(True)
        layout.addWidget(self.console)

    def log(self, message: str, level: str = "INFO"):
        time_str = datetime.now().strftime("%H:%M:%S")
        
        if level == "SUCCESS":
            color = "#3fb950"
            tag = "[SUCCESS]"
        elif level == "ERROR":
            color = "#f85149"
            tag = "[ERROR]"
        elif level == "WARNING":
            color = "#d29922"
            tag = "[WARNING]"
        else:
            color = "#58a6ff"
            tag = ">"
            
        html_msg = f'<span style="color:#687182;">[{time_str}]</span> <span style="color:{color}; font-weight:bold;">{tag}</span> {message}'
        self.console.append(html_msg)
        self.console.moveCursor(QTextCursor.MoveOperation.End)

    def clear_logs(self):
        self.console.clear()
