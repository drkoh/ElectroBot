from PyQt6.QtWidgets import (
    QFrame, QVBoxLayout, QPushButton, QLabel, QSpacerItem, QSizePolicy
)
from PyQt6.QtCore import pyqtSignal

class Sidebar(QFrame):
    page_changed = pyqtSignal(int, str) # index, title

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("Sidebar")
        self.setFixedWidth(220)
        
        layout = QVBoxLayout(self)
        layout.setContentsMargins(10, 16, 10, 16)
        layout.setSpacing(6)
        
        # Section title
        lbl_sec = QLabel("НАВІГАЦІЯ")
        lbl_sec.setStyleSheet("color: #687182; font-size: 11px; font-weight: 700; margin-left: 8px; margin-bottom: 4px;")
        layout.addWidget(lbl_sec)
        
        self.nav_buttons = []
        
        items = [
            ("📅 Графік та таймлайн", "Мій графік на сьогодні"),
            ("📋 Всі черги (1–6)", "Огляд усіх черг та підчерг"),
            ("🔍 Пошук адреси", "Пошук адреси та черги"),
            ("🚨 Аварії 1562 (ЖКГ)", "Оперативні аварійні відключення 1562"),
            ("⚙️ Налаштування & Трей", "Налаштування моніторингу"),
            ("ℹ️ Джерела даних", "Офіційні ресурси та контакти"),
        ]
        
        for idx, (btn_text, breadcrumb_text) in enumerate(items):
            btn = QPushButton(btn_text)
            btn.setObjectName("SidebarButton")
            btn.setProperty("active", "false")
            btn.clicked.connect(lambda checked, i=idx, t=breadcrumb_text: self._on_btn_clicked(i, t))
            layout.addWidget(btn)
            self.nav_buttons.append((btn, breadcrumb_text))
            
        layout.addSpacerItem(QSpacerItem(20, 40, QSizePolicy.Policy.Minimum, QSizePolicy.Policy.Expanding))
        
        # Quick active queue indicator at the bottom of the sidebar
        self.card_quick = QFrame()
        self.card_quick.setObjectName("CardFrame")
        quick_layout = QVBoxLayout(self.card_quick)
        quick_layout.setContentsMargins(10, 10, 10, 10)
        quick_layout.setSpacing(4)
        
        self.lbl_quick_title = QLabel("АКТИВНА ЧЕРГА:")
        self.lbl_quick_title.setStyleSheet("color: #9ba3b4; font-size: 10px; font-weight: 700;")
        self.lbl_quick_val = QLabel("Черга 1.1")
        self.lbl_quick_val.setStyleSheet("font-size: 15px; font-weight: 800; color: #58a6ff;")
        self.lbl_quick_addr = QLabel("м. Харків")
        self.lbl_quick_addr.setStyleSheet("color: #687182; font-size: 11px;")
        self.lbl_quick_addr.setWordWrap(True)
        
        quick_layout.addWidget(self.lbl_quick_title)
        quick_layout.addWidget(self.lbl_quick_val)
        quick_layout.addWidget(self.lbl_quick_addr)
        layout.addWidget(self.card_quick)
        
        # Select first button
        self.set_active_index(0)

    def _on_btn_clicked(self, index: int, breadcrumb: str):
        self.set_active_index(index)
        self.page_changed.emit(index, breadcrumb)

    def set_active_index(self, index: int):
        for i, (btn, _) in enumerate(self.nav_buttons):
            is_active = (i == index)
            btn.setProperty("active", "true" if is_active else "false")
            btn.style().unpolish(btn)
            btn.style().polish(btn)

    def update_active_queue_info(self, queue_name: str, address_text: str):
        self.lbl_quick_val.setText(f"Черга {queue_name}")
        short_addr = (address_text[:35] + "...") if len(address_text) > 35 else address_text
        self.lbl_quick_addr.setText(short_addr)
