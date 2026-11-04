import os
import sys
from datetime import datetime
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QFrame,
    QTableWidget, QTableWidgetItem, QHeaderView, QPushButton,
    QTextEdit, QScrollArea, QInputDialog, QMessageBox
)
from PyQt6.QtGui import QColor, QFont
from PyQt6.QtCore import pyqtSignal, Qt

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from core.models import DailySchedule, PowerStatus
from core.power_calc import calculate_power_status
from core.status_resolver import resolve_unified_status
from core.queue_detector import detect_queue_for_address
from core.config import config
from ui.context_menu_helper import attach_table_context_menu

class AllQueuesScreen(QWidget):
    queue_selected = pyqtSignal(str, str) # queue_name, address_text
    log_message = pyqtSignal(str, str)
    notify_toast = pyqtSignal(str, str, str)

    def __init__(self, schedule: DailySchedule = None, parent=None):
        super().__init__(parent)
        self.schedule = schedule
        
        root_layout = QVBoxLayout(self)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)
        
        scroll = QScrollArea(self)
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        
        content = QWidget()
        content.setObjectName("ScrollContentContainer")
        self.main_layout = QVBoxLayout(content)
        self.main_layout.setContentsMargins(20, 16, 20, 20)
        self.main_layout.setSpacing(14)
        
        # 1. Header Card
        card_hdr = QFrame()
        card_hdr.setObjectName("CardFrame")
        h_layout = QVBoxLayout(card_hdr)
        h_layout.setContentsMargins(16, 12, 16, 12)
        h_layout.setSpacing(8)
        
        row_title = QHBoxLayout()
        lbl_title = QLabel("📍 Мої збережені адреси та графіки")
        lbl_title.setStyleSheet("font-size: 16px; font-weight: 700; color: #58a6ff;")
        
        self.lbl_date = QLabel("Оновлено: --")
        self.lbl_date.setStyleSheet("color: #9ba3b4; font-size: 12px;")
        
        row_title.addWidget(lbl_title)
        row_title.addStretch()
        row_title.addWidget(self.lbl_date)
        h_layout.addLayout(row_title)
        
        row_desc = QHBoxLayout()
        lbl_desc = QLabel("Відстежуйте стан електропостачання та графіки тільки для ваших особистих локацій (дім, робота, батьки):")
        lbl_desc.setStyleSheet("font-size: 12px; color: #9ba3b4;")
        lbl_desc.setWordWrap(True)
        row_desc.addWidget(lbl_desc, stretch=1)
        
        btn_add = QPushButton("➕ Додати адресу")
        btn_add.setObjectName("PrimaryButton")
        btn_add.clicked.connect(self._on_add_new_address)
        row_desc.addWidget(btn_add)
        h_layout.addLayout(row_desc)
        
        self.main_layout.addWidget(card_hdr)
        
        # 2. My Addresses Cards Container
        self.cards_container = QVBoxLayout()
        self.cards_container.setSpacing(10)
        self.main_layout.addLayout(self.cards_container)
        
        # 3. Emergency Alert Banner (if GAV is in effect)
        self.banner_emergency = QFrame()
        self.banner_emergency.setObjectName("EmergencyBannerFrame")
        b_box = QVBoxLayout(self.banner_emergency)
        b_box.setContentsMargins(12, 10, 12, 10)
        b_box.setSpacing(4)
        lbl_e1 = QLabel("🚨 УВАГА! ДІЮТЬ ГРАФІКИ АВАРІЙНИХ ВІДКЛЮЧЕНЬ (ГАВ)")
        lbl_e1.setObjectName("EmergencyBannerTitle")
        self.lbl_e2 = QLabel("Через пошкодження енергетичних об'єктів відключення відбуваються поза графіком.")
        self.lbl_e2.setObjectName("EmergencyBannerDesc")
        self.lbl_e2.setWordWrap(True)
        b_box.addWidget(lbl_e1)
        b_box.addWidget(self.lbl_e2)
        self.main_layout.addWidget(self.banner_emergency)
        self.banner_emergency.setVisible(False)
        
        # 4. Collapsible 12-Queues Reference Section
        card_city = QFrame()
        card_city.setObjectName("CardFrame")
        city_layout = QVBoxLayout(card_city)
        city_layout.setContentsMargins(16, 12, 16, 12)
        city_layout.setSpacing(10)
        
        row_city_hdr = QHBoxLayout()
        lbl_city_title = QLabel("📋 Довідкова таблиця всіх 12 підчерг Харкова (ГПВ)")
        lbl_city_title.setStyleSheet("font-weight: 700; font-size: 13px;")
        
        self.btn_toggle_table = QPushButton("Показати таблицю ▾")
        self.btn_toggle_table.setObjectName("SecondaryButton")
        self.btn_toggle_table.setCheckable(True)
        self.btn_toggle_table.setChecked(False)
        self.btn_toggle_table.clicked.connect(self._toggle_city_table)
        
        row_city_hdr.addWidget(lbl_city_title)
        row_city_hdr.addStretch()
        row_city_hdr.addWidget(self.btn_toggle_table)
        city_layout.addLayout(row_city_hdr)
        
        # Table of All Queues (initially hidden)
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels([
            "Черга", "Поточний стан", "Години відключень (ГПВ)", "Без світла", "🎯 Дія"
        ])
        self.table.setAlternatingRowColors(True)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.verticalHeader().setVisible(False)
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(4, QHeaderView.ResizeMode.ResizeToContents)
        self.table.setMinimumHeight(180)
        self.table.setMaximumHeight(260)
        attach_table_context_menu(self.table, on_copied=lambda msg: self.notify_toast.emit("📋 Буфер", msg, "SUCCESS"))
        
        city_layout.addWidget(self.table)
        self.table.setVisible(False)
        
        self.main_layout.addWidget(card_city)
        
        # 5. Raw Telegram Post Card
        card_raw = QFrame()
        card_raw.setObjectName("CardFrame")
        raw_layout = QVBoxLayout(card_raw)
        raw_layout.setContentsMargins(14, 10, 14, 10)
        raw_layout.setSpacing(6)
        
        lbl_raw_title = QLabel("📢 Офіційне повідомлення АТ «Харківобленерго» (Telegram):")
        lbl_raw_title.setStyleSheet("font-weight: 600; font-size: 12px; color: #9ba3b4;")
        raw_layout.addWidget(lbl_raw_title)
        
        self.txt_raw = QTextEdit()
        self.txt_raw.setObjectName("RawMessageText")
        self.txt_raw.setReadOnly(True)
        self.txt_raw.setFixedHeight(80)
        raw_layout.addWidget(self.txt_raw)
        
        self.main_layout.addWidget(card_raw)
        self.main_layout.addStretch()
        
        scroll.setWidget(content)
        root_layout.addWidget(scroll)
        
        self.refresh_address_cards()
        if self.schedule:
            self.set_schedule(self.schedule)

    def _toggle_city_table(self):
        show = self.btn_toggle_table.isChecked()
        self.table.setVisible(show)
        self.btn_toggle_table.setText("Приховати таблицю ▴" if show else "Показати таблицю ▾")

    def _on_add_new_address(self):
        addr, ok1 = QInputDialog.getText(self, "Додати адресу", "Введіть вулицю та номер будинку (наприклад: вул. Сумська, 10):")
        if not ok1 or not addr.strip():
            return
        addr = addr.strip()
        auto_q = detect_queue_for_address(addr)
        
        title, ok2 = QInputDialog.getText(self, "Назва адреси", f"Введіть назву для цієї адреси (авто-визначено Чергу {auto_q}):", text="Моя адреса")
        if not ok2 or not title.strip():
            title = "Моя адреса"
            
        favs = config.get("favorites", [])
        new_fav = {
            "id": str(int(datetime.now().timestamp() * 1000)),
            "title": title.strip(),
            "address": addr,
            "queue": auto_q
        }
        favs.append(new_fav)
        config.set("favorites", favs)
        self.refresh_address_cards()
        self.notify_toast.emit("⭐ Адреса збережена", f"Додано: {title} ({addr}) — автоматично Черга {auto_q}!", "SUCCESS")

    def refresh_address_cards(self):
        # Clear existing card widgets
        while self.cards_container.count():
            item = self.cards_container.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
                
        favs = config.get("favorites", [])
        if not favs:
            # Add default home if empty
            favs = [{
                "id": "fav_home",
                "title": "Дом",
                "address": "Харків, вул. Дудинської, 9",
                "queue": "1.1"
            }]
            config.set("favorites", favs)
            
        cur_active_addr = config.get("active_address", "Харків, вул. Дудинської, 9")
        cur_active_queue = config.get("active_queue", "1.1")
        now = datetime.now()
        
        for f in favs:
            addr_text = f.get("address", "")
            q_name = f.get("queue") or detect_queue_for_address(addr_text)
            f_title = f.get("title", "Адреса")
            is_active = (addr_text.strip() == cur_active_addr.strip())
            
            # Resolve unified status
            u_status = resolve_unified_status(addr_text, q_name, self.schedule, now)
            
            card = QFrame()
            card.setObjectName("CardFrameDanger" if u_status.is_outage else "CardFrame")
            card_lay = QVBoxLayout(card)
            card_lay.setContentsMargins(16, 12, 16, 12)
            card_lay.setSpacing(6)
            
            # Top row: Title + Queue + Status Pill
            row_top = QHBoxLayout()
            lbl_name = QLabel(f"🏠 {f_title}: {addr_text}")
            lbl_name.setStyleSheet("font-size: 14px; font-weight: 700;")
            
            lbl_q = QLabel(f"Черга {q_name}")
            lbl_q.setStyleSheet("background-color: #223249; color: #58a6ff; font-weight: 700; border-radius: 6px; padding: 3px 8px; font-size: 11px;")
            
            lbl_badge = QLabel(u_status.badge_text)
            lbl_badge.setObjectName("StatusOffBadge" if u_status.is_outage else "StatusOnBadge")
            
            row_top.addWidget(lbl_name)
            row_top.addStretch()
            row_top.addWidget(lbl_q)
            row_top.addWidget(lbl_badge)
            card_lay.addLayout(row_top)
            
            # Middle row: Countdown + Schedule intervals
            row_mid = QHBoxLayout()
            time_info = f"Орієнтовне увімкнення о {u_status.target_time_str}" if u_status.is_outage else f"До відключення о {u_status.target_time_str}"
            lbl_countdown = QLabel(f"⏱️ {time_info} (через {u_status.countdown_hms})")
            lbl_countdown.setStyleSheet("font-size: 12px; font-weight: 600; color: #e6edf3;")
            
            row_mid.addWidget(lbl_countdown)
            row_mid.addStretch()
            
            if is_active:
                lbl_cur = QLabel("✓ Поточна активна адреса")
                lbl_cur.setStyleSheet("color: #3fb950; font-weight: 700; font-size: 12px;")
                row_mid.addWidget(lbl_cur)
            else:
                btn_activate = QPushButton("🏠 Зробити активною")
                btn_activate.setObjectName("PrimaryButton")
                btn_activate.clicked.connect(lambda ch, q=q_name, a=addr_text: self._activate_address(q, a))
                row_mid.addWidget(btn_activate)
                
            card_lay.addLayout(row_mid)
            
            # Schedule intervals line
            invs = self.schedule.queues.get(q_name, []) if self.schedule else []
            if invs:
                inv_strs = [f"{i.start_str}–{i.end_str}" for i in invs]
                lbl_invs = QLabel(f"Години відключень сьогодні: 🟥 {'; '.join(inv_strs)}")
            else:
                lbl_invs = QLabel("Години відключень сьогодні: завантаження графіка...")
            lbl_invs.setStyleSheet("color: #9ba3b4; font-size: 11px;")
            card_lay.addWidget(lbl_invs)
            
            self.cards_container.addWidget(card)

    def _activate_address(self, queue_name: str, addr: str):
        self.queue_selected.emit(queue_name, addr)
        self.refresh_address_cards()
        self.notify_toast.emit("📍 Активна адреса змінена", f"Встановлено: {addr} (Черга {queue_name})", "SUCCESS")

    def set_schedule(self, schedule: DailySchedule):
        self.schedule = schedule
        if not schedule:
            return
            
        self.lbl_date.setText(f"📅 {schedule.date_str} ({schedule.fetched_at[:16]})")
        self.txt_raw.setPlainText(schedule.raw_post_text)
        
        if schedule.is_emergency:
            self.banner_emergency.setVisible(True)
            if schedule.emergency_alert_text:
                self.lbl_e2.setText(schedule.emergency_alert_text)
        else:
            self.banner_emergency.setVisible(False)
            
        self.refresh_address_cards()
        self.refresh_table_rows()

    def refresh_table_rows(self):
        if not self.schedule:
            return
            
        now = datetime.now()
        subqueues = ["1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"]
        self.table.setRowCount(len(subqueues))
        active_q = config.get("active_queue", "1.1")
        
        for idx, q_name in enumerate(subqueues):
            invs = self.schedule.queues.get(q_name, [])
            status = calculate_power_status(q_name, self.schedule, now)
            
            item_q = QTableWidgetItem(f"Черга {q_name}")
            item_q.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            if q_name == active_q:
                item_q.setBackground(QColor("#1f6feb"))
                item_q.setForeground(QColor("#ffffff"))
                font = item_q.font()
                font.setBold(True)
                item_q.setFont(font)
            self.table.setItem(idx, 0, item_q)
            
            # Status
            item_st = QTableWidgetItem("🔴 Відключено" if status.status == PowerStatus.OFF else "🟢 Світло є")
            item_st.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            self.table.setItem(idx, 1, item_st)
            
            # Intervals
            inv_str = "; ".join([f"{i.start_str}–{i.end_str}" for i in invs]) if invs else "Немає відключень"
            self.table.setItem(idx, 2, QTableWidgetItem(inv_str))
            
            # Off hours
            tot_off = sum((i.end_minute - i.start_minute) for i in invs)
            self.table.setItem(idx, 3, QTableWidgetItem(f"{tot_off // 60} год {tot_off % 60:02d} хв"))
            
            # Action button
            if q_name == active_q:
                lbl_act = QLabel("✓ Моя черга")
                lbl_act.setAlignment(Qt.AlignmentFlag.AlignCenter)
                lbl_act.setStyleSheet("color: #3fb950; font-weight: bold; font-size: 11px;")
                self.table.setCellWidget(idx, 4, lbl_act)
            else:
                btn = QPushButton("Обрати")
                btn.setObjectName("SecondaryButton")
                btn.clicked.connect(lambda ch, q=q_name: self._on_select_queue_clicked(q))
                self.table.setCellWidget(idx, 4, btn)

    def _on_select_queue_clicked(self, q_name: str):
        cur_addr = config.get("active_address", "м. Харків")
        self.queue_selected.emit(q_name, cur_addr)
        self.refresh_address_cards()
        self.refresh_table_rows()
