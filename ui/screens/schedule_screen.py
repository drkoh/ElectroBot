import os
import sys
from datetime import datetime
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QFrame,
    QComboBox, QPushButton, QTableWidget, QTableWidgetItem, QHeaderView,
    QMenu, QApplication, QScrollArea, QSizePolicy
)
from PyQt6.QtCore import QTimer, Qt, pyqtSignal, QPoint
from PyQt6.QtGui import QAction

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from core.models import DailySchedule, PowerStatus
from core.status_resolver import resolve_unified_status, UnifiedStatus
from core.config import config
from core.ping_detector import ping_host
from core.async_workers import run_async
from ui.timeline_widget import TimelineWidget
from ui.context_menu_helper import attach_table_context_menu

class ScheduleScreen(QWidget):
    edit_address_requested = pyqtSignal()
    notify_toast = pyqtSignal(str, str, str) # title, message, type

    def __init__(self, schedule: DailySchedule = None, parent=None):
        super().__init__(parent)
        self.schedule = schedule
        self.active_queue = config.get("active_queue", "2.1")
        self.active_address = config.get("active_address", "Харків, вул. Дудинської")
        self.last_unified_status: UnifiedStatus = None
        
        # Root layout holding the smooth scroll area
        root_layout = QVBoxLayout(self)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)
        
        scroll = QScrollArea(self)
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        
        content_container = QWidget()
        content_container.setObjectName("ScrollContentContainer")
        main_layout = QVBoxLayout(content_container)
        main_layout.setContentsMargins(20, 16, 20, 20)
        main_layout.setSpacing(14)
        
        # 1. Top Bar: Queue Selector & Date info & Router Status
        top_bar = QHBoxLayout()
        top_bar.setSpacing(10)
        
        lbl_q_select = QLabel("Черга:")
        lbl_q_select.setStyleSheet("font-weight: 700;")
        
        self.combo_queue = QComboBox()
        for q in ["1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"]:
            self.combo_queue.addItem(f"Черга {q}", q)
        idx = self.combo_queue.findData(self.active_queue)
        if idx >= 0:
            self.combo_queue.setCurrentIndex(idx)
        self.combo_queue.currentIndexChanged.connect(self._on_queue_changed)
        
        top_bar.addWidget(lbl_q_select)
        top_bar.addWidget(self.combo_queue)
        
        # Active Address Chip with Edit Button
        self.btn_addr_chip = QPushButton(f"📍 {self.active_address} ✏️")
        self.btn_addr_chip.setObjectName("TonalButton")
        self.btn_addr_chip.setToolTip("Клікніть, щоб відредагувати адресу або номер будинку")
        self.btn_addr_chip.clicked.connect(self.edit_address_requested.emit)
        top_bar.addWidget(self.btn_addr_chip)
        
        top_bar.addStretch()
        
        # Router ping badge
        self.lbl_router_ping = QLabel("📶 Роутер: перевірка...")
        self.lbl_router_ping.setObjectName("BadgePill")
        top_bar.addWidget(self.lbl_router_ping)
        
        self.lbl_schedule_date = QLabel("📅 Завантаження...")
        self.lbl_schedule_date.setStyleSheet("font-size: 12px; font-weight: 600;")
        top_bar.addWidget(self.lbl_schedule_date)
        
        main_layout.addLayout(top_bar)
        
        # 1562 / GAV Emergency Alert Banner (semantic ID: EmergencyBannerFrame)
        self.banner_emergency = QFrame()
        self.banner_emergency.setObjectName("EmergencyBannerFrame")
        b_layout = QVBoxLayout(self.banner_emergency)
        b_layout.setContentsMargins(12, 10, 12, 10)
        b_layout.setSpacing(4)
        
        self.lbl_emerg_title = QLabel("🚨 ДІЮТЬ АВАРІЙНІ ВІДКЛЮЧЕННЯ (ГАВ)")
        self.lbl_emerg_title.setObjectName("EmergencyBannerTitle")
        
        self.lbl_emerg_desc = QLabel("Зафіксовано аварійні роботи за адресою або спеціальні відключення НЕК «Укренерго».")
        self.lbl_emerg_desc.setObjectName("EmergencyBannerDesc")
        self.lbl_emerg_desc.setWordWrap(True)
        
        b_layout.addWidget(self.lbl_emerg_title)
        b_layout.addWidget(self.lbl_emerg_desc)
        main_layout.addWidget(self.banner_emergency)
        self.banner_emergency.setVisible(False)
        
        # 2. Hero Status Card (Live Power Status & Big Countdown)
        self.card_hero = QFrame()
        self.card_hero.setObjectName("CardFrame")
        self.card_hero.setMinimumHeight(190)
        self.card_hero.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum)
        self.card_hero.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.card_hero.customContextMenuRequested.connect(self._show_hero_context_menu)
        
        hero_layout = QVBoxLayout(self.card_hero)
        hero_layout.setContentsMargins(20, 16, 20, 16)
        hero_layout.setSpacing(10)
        
        # Row 1: Status Pill + Current Time
        h_row1 = QHBoxLayout()
        self.lbl_hero_badge = QLabel("🟢 СВІТЛО Є ЗА ГРАФІКОМ")
        self.lbl_hero_badge.setObjectName("StatusOnBadge")
        
        self.lbl_hero_clock = QLabel(datetime.now().strftime("Поточний час: %H:%M:%S"))
        self.lbl_hero_clock.setStyleSheet("font-size: 13px; font-weight: 600;")
        
        h_row1.addWidget(self.lbl_hero_badge)
        h_row1.addStretch()
        h_row1.addWidget(self.lbl_hero_clock)
        hero_layout.addLayout(h_row1)
        
        # Row 2: Headline & Big Countdown
        h_row2 = QHBoxLayout()
        self.lbl_hero_action = QLabel("До увімкнення:")
        self.lbl_hero_action.setStyleSheet("font-size: 16px; font-weight: 700;")
        
        self.lbl_hero_countdown = QLabel("00:00:00")
        self.lbl_hero_countdown.setStyleSheet("font-size: 32px; font-weight: 800; font-family: monospace;")
        
        h_row2.addWidget(self.lbl_hero_action)
        h_row2.addWidget(self.lbl_hero_countdown)
        h_row2.addStretch()
        hero_layout.addLayout(h_row2)
        
        # Row 3: Details text & Subtext
        self.lbl_hero_details = QLabel("Очікується статус...")
        self.lbl_hero_details.setStyleSheet("font-size: 13px;")
        self.lbl_hero_details.setWordWrap(True)
        hero_layout.addWidget(self.lbl_hero_details)
        
        self.lbl_hero_subtext = QLabel("")
        self.lbl_hero_subtext.setStyleSheet("font-size: 12px; font-style: italic;")
        self.lbl_hero_subtext.setWordWrap(True)
        hero_layout.addWidget(self.lbl_hero_subtext)
        
        # Row 4: Battery Charge Window Card (Smart Blackout Assistant)
        self.card_charge = QFrame()
        self.card_charge.setObjectName("CardFrame")
        self.card_charge.setMinimumHeight(44)
        charge_layout = QHBoxLayout(self.card_charge)
        charge_layout.setContentsMargins(12, 8, 12, 8)
        
        self.lbl_charge_info = QLabel("⚡ Вікно для зарядки: розраховується...")
        self.lbl_charge_info.setStyleSheet("font-weight: 700; font-size: 13px;")
        charge_layout.addWidget(self.lbl_charge_info)
        charge_layout.addStretch()
        
        btn_copy_status = QPushButton("📋 Скопіювати статус")
        btn_copy_status.setObjectName("SecondaryButton")
        btn_copy_status.clicked.connect(self._copy_current_status)
        charge_layout.addWidget(btn_copy_status)
        
        hero_layout.addWidget(self.card_charge)
        main_layout.addWidget(self.card_hero)
        
        # 3. Interactive Timeline Card
        card_timeline = QFrame()
        card_timeline.setObjectName("CardFrame")
        card_timeline.setMinimumHeight(105)
        tl_layout = QVBoxLayout(card_timeline)
        tl_layout.setContentsMargins(16, 12, 16, 12)
        tl_layout.setSpacing(8)
        
        h_tl_title = QHBoxLayout()
        lbl_tl_title = QLabel("📊 Добовий графік (24 години)")
        lbl_tl_title.setStyleSheet("font-weight: 700; font-size: 14px;")
        
        lbl_legend = QLabel("🟩 Світло є  |  🟥 Відключення  |  🔹 Поточний час")
        lbl_legend.setStyleSheet("font-size: 11px;")
        
        h_tl_title.addWidget(lbl_tl_title)
        h_tl_title.addStretch()
        h_tl_title.addWidget(lbl_legend)
        tl_layout.addLayout(h_tl_title)
        
        self.timeline = TimelineWidget()
        tl_layout.addWidget(self.timeline)
        main_layout.addWidget(card_timeline)
        
        # 4. Summary Stats & Intervals Table
        bottom_grid = QHBoxLayout()
        bottom_grid.setSpacing(16)
        
        # Left Stats Card
        card_stats = QFrame()
        card_stats.setObjectName("CardFrame")
        card_stats.setFixedWidth(270)
        card_stats.setMinimumHeight(140)
        card_stats.setMaximumHeight(190)
        st_layout = QVBoxLayout(card_stats)
        st_layout.setContentsMargins(16, 14, 16, 14)
        st_layout.setSpacing(10)
        
        lbl_st_title = QLabel("📈 Підсумки доби:")
        lbl_st_title.setStyleSheet("font-weight: 700; font-size: 13px;")
        st_layout.addWidget(lbl_st_title)
        
        self.lbl_stat_off = QLabel("🟥 Без світла: -- год")
        self.lbl_stat_off.setStyleSheet("font-size: 13px; font-weight: 600;")
        self.lbl_stat_on = QLabel("🟩 Зі світлом: -- год")
        self.lbl_stat_on.setStyleSheet("font-size: 13px; font-weight: 600;")
        self.lbl_stat_count = QLabel("🔄 Періодів відключень: --")
        self.lbl_stat_count.setStyleSheet("font-size: 13px; font-weight: 600;")
        
        st_layout.addWidget(self.lbl_stat_off)
        st_layout.addWidget(self.lbl_stat_on)
        st_layout.addWidget(self.lbl_stat_count)
        st_layout.addStretch()
        bottom_grid.addWidget(card_stats)
        
        # Right Intervals Table
        self.table_intervals = QTableWidget()
        self.table_intervals.setColumnCount(4)
        self.table_intervals.setHorizontalHeaderLabels(["Інтервал", "Початок", "Кінець", "Тривалість"])
        self.table_intervals.setAlternatingRowColors(True)
        self.table_intervals.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table_intervals.verticalHeader().setVisible(False)
        self.table_intervals.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.table_intervals.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.table_intervals.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        self.table_intervals.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        self.table_intervals.setMinimumHeight(140)
        self.table_intervals.setMaximumHeight(190)
        
        attach_table_context_menu(self.table_intervals, on_copied=lambda msg: self.notify_toast.emit("📋 Буфер обміну", msg, "SUCCESS"))
        bottom_grid.addWidget(self.table_intervals)
        
        main_layout.addLayout(bottom_grid)
        main_layout.addStretch()
        
        scroll.setWidget(content_container)
        root_layout.addWidget(scroll)
        
        # Timers
        self.second_timer = QTimer(self)
        self.second_timer.timeout.connect(self._on_second_tick)
        self.second_timer.start(1000)
        
        # Background Router Ping Timer (every 30 seconds)
        self.router_timer = QTimer(self)
        self.router_timer.timeout.connect(self._trigger_router_ping)
        self.router_timer.start(30000)
        self._trigger_router_ping()

    def _show_hero_context_menu(self, pos: QPoint):
        menu = QMenu(self.card_hero)
        act_copy = menu.addAction("📋 Скопіювати інформацію про стан електропостачання")
        act_copy.triggered.connect(self._copy_current_status)
        act_edit = menu.addAction("✏️ Змінити активну адресу")
        act_edit.triggered.connect(self.edit_address_requested.emit)
        menu.exec(self.card_hero.mapToGlobal(pos))

    def _copy_current_status(self):
        u = self.last_unified_status
        if not u:
            return
        text = (
            f"⚡ Стан світла в Харкові ({self.active_address}, Черга {self.active_queue}):\n"
            f"📌 Статус: {u.badge_text}\n"
            f"⏱️ {self.lbl_hero_action.text()} {u.target_time_str} (залишилось {u.countdown_hms})\n"
            f"ℹ️ {u.details}\n"
            f"{u.charge_window_str}"
        )
        QApplication.clipboard().setText(text)
        self.notify_toast.emit("📋 Скопійовано!", "Інформацію про стан скопійовано у буфер обміну.", "SUCCESS")

    def _trigger_router_ping(self):
        if not config.get("router_ping_enabled", True):
            self.lbl_router_ping.setText("📶 Пінг вимкнено")
            return
            
        target_ip = config.get("router_ip", "")
        run_async(lambda: ping_host(target_ip), on_result=self._on_router_ping_result)

    def _on_router_ping_result(self, res):
        if res.is_reachable:
            lat = f"{res.latency_ms:.0f}мс" if res.latency_ms is not None else "1мс"
            self.lbl_router_ping.setText(f"🟢 Роутер онлайн ({lat})")
            self.lbl_router_ping.setToolTip(f"Локальний шлюз: {res.ip_address} доступний (затримка {lat})")
        else:
            self.lbl_router_ping.setText(f"🔴 Роутер офлайн")
            self.lbl_router_ping.setToolTip(f"Локальний шлюз: {res.ip_address} недоступний — можливе фізичне знеструмлення")

    def set_schedule(self, schedule: DailySchedule):
        self.schedule = schedule
        if schedule:
            self.lbl_schedule_date.setText(f"📅 {schedule.date_str}")
        self.refresh_view()

    def set_active_queue(self, queue_name: str, address_text: str = ""):
        self.active_queue = queue_name
        if address_text:
            self.active_address = address_text
            self.btn_addr_chip.setText(f"📍 {address_text} ✏️")
        idx = self.combo_queue.findData(queue_name)
        if idx >= 0:
            self.combo_queue.setCurrentIndex(idx)
        self.refresh_view()

    def _on_queue_changed(self, index: int):
        q = self.combo_queue.currentData()
        if q:
            self.active_queue = q
            config.set("active_queue", q)
            self.refresh_view()

    def _on_second_tick(self):
        now = datetime.now()
        self.lbl_hero_clock.setText(now.strftime("Поточний час: %H:%M:%S"))
        
        if not self.isVisible():
            return
            
        u_status = resolve_unified_status(self.active_address, self.active_queue, self.schedule, now)
        self.last_unified_status = u_status
        self.lbl_hero_countdown.setText(u_status.countdown_hms)

    def refresh_view(self):
        now = datetime.now()
        u_status = resolve_unified_status(self.active_address, self.active_queue, self.schedule, now)
        self.last_unified_status = u_status
        
        # 1. Update Hero Badge & Action
        self.lbl_hero_badge.setText(u_status.badge_text)
        if u_status.badge_theme == "danger":
            self.lbl_hero_badge.setObjectName("StatusOffBadge")
            self.card_hero.setObjectName("CardFrameDanger")
            self.lbl_hero_countdown.setStyleSheet("font-size: 32px; font-weight: 800; font-family: monospace; color: #da3633;")
        elif u_status.badge_theme == "warning":
            self.lbl_hero_badge.setObjectName("StatusWarningBadge")
            self.card_hero.setObjectName("CardFrameWarning")
            self.lbl_hero_countdown.setStyleSheet("font-size: 32px; font-weight: 800; font-family: monospace; color: #d29922;")
        else:
            self.lbl_hero_badge.setObjectName("StatusOnBadge")
            self.card_hero.setObjectName("CardFrame")
            self.lbl_hero_countdown.setStyleSheet("font-size: 32px; font-weight: 800; font-family: monospace; color: #1f6feb;")
            
        self.lbl_hero_badge.style().unpolish(self.lbl_hero_badge)
        self.lbl_hero_badge.style().polish(self.lbl_hero_badge)
        self.card_hero.style().unpolish(self.card_hero)
        self.card_hero.style().polish(self.card_hero)
        
        if u_status.is_outage:
            self.lbl_hero_action.setText(f"Орієнтовне увімкнення ({u_status.target_time_str}):")
        else:
            self.lbl_hero_action.setText(f"До відключення за графіком ({u_status.target_time_str}):")
            
        self.lbl_hero_countdown.setText(u_status.countdown_hms)
        self.lbl_hero_details.setText(u_status.details)
        self.lbl_hero_subtext.setText(u_status.subtext)
        self.lbl_charge_info.setText(u_status.charge_window_str)
        
        # Emergency Banner (Only for regional alerts GAV, as card_hero fully presents address-specific 1562 emergencies)
        if u_status.is_emergency and u_status.state_type == "EMERGENCY_GAV":
            self.banner_emergency.setVisible(True)
            self.lbl_emerg_title.setText(u_status.headline)
            self.lbl_emerg_desc.setText(u_status.details + "\n" + u_status.subtext)
        else:
            self.banner_emergency.setVisible(False)
            
        # 2. Update Timeline
        invs = self.schedule.queues.get(self.active_queue, []) if self.schedule else []
        self.timeline.set_intervals(invs)
        
        # 3. Update Stats
        total_off_mins = sum((i.end_minute - i.start_minute) for i in invs)
        total_on_mins = 1440 - total_off_mins
        self.lbl_stat_off.setText(f"🟥 Без світла: {total_off_mins // 60} год {total_off_mins % 60:02d} хв")
        self.lbl_stat_on.setText(f"🟩 Зі світлом: {total_on_mins // 60} год {total_on_mins % 60:02d} хв")
        self.lbl_stat_count.setText(f"🔄 Періодів відключень: {len(invs)}")
        
        # 4. Update Table
        self.table_intervals.setRowCount(len(invs))
        for idx, inv in enumerate(invs):
            dur_mins = inv.end_minute - inv.start_minute
            dur_str = f"{dur_mins // 60} год {dur_mins % 60:02d} хв"
            
            item_num = QTableWidgetItem(f"Період {idx + 1}")
            item_num.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            item_start = QTableWidgetItem(inv.start_str)
            item_start.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            item_end = QTableWidgetItem(inv.end_str)
            item_end.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            item_dur = QTableWidgetItem(dur_str)
            item_dur.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            
            self.table_intervals.setItem(idx, 0, item_num)
            self.table_intervals.setItem(idx, 1, item_start)
            self.table_intervals.setItem(idx, 2, item_end)
            self.table_intervals.setItem(idx, 3, item_dur)
