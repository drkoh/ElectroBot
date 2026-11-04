import os
import sys
from datetime import datetime
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QFrame,
    QTableWidget, QTableWidgetItem, QHeaderView, QPushButton,
    QTextEdit
)
from PyQt6.QtGui import QColor, QFont
from PyQt6.QtCore import pyqtSignal, Qt
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from core.models import DailySchedule, PowerStatus
from core.power_calc import calculate_power_status
from core.config import config
from ui.context_menu_helper import attach_table_context_menu

class AllQueuesScreen(QWidget):
    queue_selected = pyqtSignal(str, str)
    log_message = pyqtSignal(str, str)
    notify_toast = pyqtSignal(str, str, str)

    def __init__(self, schedule: DailySchedule = None, parent=None):
        super().__init__(parent)
        self.schedule = schedule
        
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(12)
        
        # 1. Header info & Purpose card
        card_info = QFrame()
        card_info.setObjectName("CardFrame")
        info_layout = QVBoxLayout(card_info)
        info_layout.setContentsMargins(16, 12, 16, 12)
        info_layout.setSpacing(8)
        
        h_info = QHBoxLayout()
        lbl_title = QLabel("📋 Зведена таблиця черг погодинних відключень (ГПВ)")
        lbl_title.setStyleSheet("font-size: 15px; font-weight: 700; color: #58a6ff;")
        
        self.lbl_date = QLabel("Оновлено: --")
        self.lbl_date.setStyleSheet("color: #9ba3b4; font-size: 12px;")
        
        h_info.addWidget(lbl_title)
        h_info.addStretch()
        h_info.addWidget(self.lbl_date)
        info_layout.addLayout(h_info)
        
        lbl_hint = QLabel("💡 <b>Навіщо обирати чергу:</b> оберіть вашу чергу кнопкою «🏠 Зробити моєю», щоб ElectroBot вів персональний зворотний відлік на головному екрані («До включення/вимкнення залишилось...») та надсилав сповіщення саме для вашого будинку.")
        lbl_hint.setStyleSheet("font-size: 12px; color: #9ba3b4;")
        lbl_hint.setWordWrap(True)
        info_layout.addWidget(lbl_hint)
        
        layout.addWidget(card_info)
        
        # Emergency Banner (M3 Semantic Themed)
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
        layout.addWidget(self.banner_emergency)
        self.banner_emergency.setVisible(False)
        
        # Table of All Queues
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels([
            "Черга", "Поточний стан", "Години відключень (ГПВ)", "Без світла", "🎯 Мій моніторинг"
        ])
        self.table.setAlternatingRowColors(True)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.verticalHeader().setVisible(False)
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(4, QHeaderView.ResizeMode.ResizeToContents)
        attach_table_context_menu(self.table, on_copied=lambda msg: self.notify_toast.emit("📋 Буфер", msg, "SUCCESS"))
        layout.addWidget(self.table)
        
        # Raw Post Card
        card_raw = QFrame()
        card_raw.setObjectName("CardFrame")
        raw_layout = QVBoxLayout(card_raw)
        raw_layout.setContentsMargins(14, 10, 14, 10)
        raw_layout.setSpacing(6)
        
        lbl_raw_title = QLabel("📢 Офіційне повідомлення АТ «Харківобленерго» (Telegram-першоджерело):")
        lbl_raw_title.setStyleSheet("font-weight: 600; font-size: 12px; color: #9ba3b4;")
        raw_layout.addWidget(lbl_raw_title)
        
        self.txt_raw = QTextEdit()
        self.txt_raw.setObjectName("RawMessageText")
        self.txt_raw.setReadOnly(True)
        self.txt_raw.setFixedHeight(90)
        raw_layout.addWidget(self.txt_raw)
        
        layout.addWidget(card_raw)
        
        if self.schedule:
            self.set_schedule(self.schedule)

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
        
        self.refresh_table_rows()

    def refresh_table_rows(self):
        if not self.schedule:
            return
            
        now = datetime.now()
        subqueues = ["1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"]
        self.table.setRowCount(len(subqueues))
        active_q = config.get("active_queue", "2.1")
        
        for idx, q_name in enumerate(subqueues):
            invs = self.schedule.queues.get(q_name, [])
            status = calculate_power_status(q_name, self.schedule, now)
            
            # 1. Queue Name
            item_name = QTableWidgetItem(f"Черга {q_name}")
            item_name.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            f_bold = QFont()
            f_bold.setBold(True)
            item_name.setFont(f_bold)
            
            # 2. Status Badge
            if status.status == PowerStatus.ON:
                status_text = "🟢 Світло є"
                status_color = "#3fb950"
            elif status.status == PowerStatus.SWITCHING:
                status_text = "🟡 Перемикання"
                status_color = "#d29922"
            else:
                status_text = "🔴 Відключено"
                status_color = "#f85149"
            item_st = QTableWidgetItem(status_text)
            item_st.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            item_st.setFont(f_bold)
            item_st.setForeground(QColor(status_color))
            
            # 3. Hours formatted
            hours_str = ";  ".join([f"{i.start_str} - {i.end_str}" for i in invs]) if invs else "Відключень не заплановано"
            item_hours = QTableWidgetItem(hours_str)
            
            # 4. Total duration
            total_off = sum((i.end_minute - i.start_minute) for i in invs)
            dur_str = f"{total_off // 60}г {total_off % 60:02d}хв" if total_off > 0 else "0 год"
            item_dur = QTableWidgetItem(dur_str)
            item_dur.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            
            # 5. Monitoring Action / Badge
            if q_name == active_q:
                lbl_cur = QLabel("✅ Моя черга")
                lbl_cur.setAlignment(Qt.AlignmentFlag.AlignCenter)
                lbl_cur.setStyleSheet("""
                    QLabel {
                        color: #2ea043;
                        font-weight: 700;
                        font-size: 11px;
                        background-color: rgba(35, 134, 54, 0.18);
                        border: 1px solid #238636;
                        border-radius: 6px;
                        padding: 4px 8px;
                    }
                """)
                lbl_cur.setToolTip(f"Черга {q_name} зараз обрана як ваша активна адреса. ElectroBot показує її таймер на головному екрані.")
                self.table.setCellWidget(idx, 4, lbl_cur)
            else:
                btn_pick = QPushButton("🏠 Зробити моєю")
                btn_pick.setObjectName("PrimaryButton")
                btn_pick.setFixedHeight(28)
                btn_pick.setStyleSheet("padding: 2px 10px; font-size: 11px;")
                btn_pick.setToolTip(f"Натисніть, щоб встановити Чергу {q_name} як вашу основну для таймера та сповіщень")
                btn_pick.clicked.connect(lambda ch, q=q_name: self._on_pick_queue(q))
                self.table.setCellWidget(idx, 4, btn_pick)
            
            self.table.setItem(idx, 0, item_name)
            self.table.setItem(idx, 1, item_st)
            self.table.setItem(idx, 2, item_hours)
            self.table.setItem(idx, 3, item_dur)

    def _on_pick_queue(self, queue_name: str):
        config.set("active_queue", queue_name)
        cur_addr = config.get("active_address", "")
        # Keep real street address if set, otherwise set generic queue address
        if not cur_addr or "черга" in cur_addr.lower():
            addr = f"Харків (Черга {queue_name})"
            config.set("active_address", addr)
        else:
            addr = cur_addr
            
        self.log_message.emit(f"Перемкнуто на чергу: {queue_name}", "SUCCESS")
        self.notify_toast.emit("🎯 Чергу обрано!", f"Встановлено Чергу {queue_name} як вашу основну для моніторингу.", "SUCCESS")
        self.queue_selected.emit(queue_name, addr)
        self.refresh_table_rows()
