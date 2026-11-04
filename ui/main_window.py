import os
import sys
from datetime import datetime
from PyQt6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QStackedWidget,
    QApplication, QMessageBox
)
from PyQt6.QtGui import QIcon, QCloseEvent, QResizeEvent
from PyQt6.QtCore import QTimer, pyqtSlot, Qt

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from core.models import DailySchedule, PowerStatus
from core.telegram_schedule import fetch_schedule_from_telegram
from core.status_resolver import resolve_unified_status, UnifiedStatus
from core.config import config
from core.async_workers import run_async
from ui.styles import generate_stylesheet
from ui.header_bar import HeaderBar
from ui.sidebar import Sidebar
from ui.log_drawer import LogDrawer
from ui.tray_manager import TrayManager
from ui.desktop_widget import DesktopWidget
from ui.toast_overlay import InAppToast
from ui.screens.search_screen import SearchScreen, EditAddressDialog
from ui.screens.schedule_screen import ScheduleScreen
from ui.screens.all_queues_screen import AllQueuesScreen
from ui.screens.service_1562_screen import Service1562Screen
from ui.screens.settings_screen import SettingsScreen
from ui.screens.info_screen import InfoScreen

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        
        self.setWindowTitle("ElectroBot — Моніторинг відключень світла (Харків)")
        self.resize(config.get("window_width", 1080), config.get("window_height", 720))
        self.setMinimumSize(920, 600)
        
        self.current_theme = config.get("theme", "dark")
        self.schedule: DailySchedule | None = None
        self.last_notified_interval = None
        self.last_status_state: str = ""
        
        # Central Widget & Root Layout
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        root_layout = QVBoxLayout(central_widget)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)
        
        # 1. Header Bar
        self.header = HeaderBar(self)
        self.header.theme_toggled.connect(self.toggle_theme)
        self.header.console_toggled.connect(self.toggle_console)
        self.header.refresh_requested.connect(self.refresh_schedule_data)
        self.header.mini_widget_toggled.connect(self.toggle_mini_widget)
        self.header.edit_address_requested.connect(self.open_quick_edit_dialog)
        self.header.set_theme_button_text(self.current_theme == "dark")
        root_layout.addWidget(self.header)
        
        # 2. Middle Row: Sidebar + Workspace Stack
        middle_layout = QHBoxLayout()
        middle_layout.setContentsMargins(0, 0, 0, 0)
        middle_layout.setSpacing(0)
        
        self.sidebar = Sidebar(self)
        self.sidebar.page_changed.connect(self.on_nav_changed)
        middle_layout.addWidget(self.sidebar)
        
        self.stack = QStackedWidget(self)
        
        # Screens
        self.screen_search = SearchScreen(self)
        self.screen_search.queue_selected.connect(self.on_queue_selected)
        self.screen_search.log_message.connect(self.log)
        self.screen_search.notify_toast.connect(self.show_toast)
        
        self.screen_schedule = ScheduleScreen(self.schedule, self)
        self.screen_schedule.edit_address_requested.connect(self.open_quick_edit_dialog)
        self.screen_schedule.notify_toast.connect(self.show_toast)
        
        self.screen_all = AllQueuesScreen(self.schedule, self)
        self.screen_all.queue_selected.connect(self.on_queue_selected)
        self.screen_all.log_message.connect(self.log)
        self.screen_all.notify_toast.connect(self.show_toast)
        
        self.screen_1562 = Service1562Screen(self)
        self.screen_1562.log_message.connect(self.log)
        self.screen_1562.notify_toast.connect(self.show_toast)
        
        self.screen_settings = SettingsScreen(self)
        self.screen_settings.test_notification_requested.connect(self.test_notification)
        self.screen_settings.log_message.connect(self.log)
        self.screen_settings.notify_toast.connect(self.show_toast)
        self.screen_settings.settings_saved.connect(self.screen_search.refresh_favorites_chips)
        
        self.screen_info = InfoScreen(self)
        
        self.stack.addWidget(self.screen_schedule)  # 0: Графік та таймлайн (Головна)
        self.stack.addWidget(self.screen_all)       # 1: Всі черги
        self.stack.addWidget(self.screen_search)    # 2: Пошук адреси
        self.stack.addWidget(self.screen_1562)      # 3: Аварії 1562
        self.stack.addWidget(self.screen_settings)  # 4: Налаштування
        self.stack.addWidget(self.screen_info)      # 5: Джерела даних
        
        middle_layout.addWidget(self.stack)
        root_layout.addLayout(middle_layout)
        
        # 3. Log Drawer
        self.log_drawer = LogDrawer(self)
        self.log_drawer.close_requested.connect(lambda: self.log_drawer.setVisible(False))
        root_layout.addWidget(self.log_drawer)
        self.log_drawer.setVisible(False)
        
        # 4. System Tray Manager
        self.tray = TrayManager(self)
        self.tray.show_window_requested.connect(self.restore_window)
        self.tray.refresh_requested.connect(self.refresh_schedule_data)
        self.tray.mini_widget_requested.connect(self.toggle_mini_widget)
        self.tray.exit_requested.connect(self.force_exit)
        
        # 5. Desktop Mini-Widget (Floating Always-On-Top bar)
        self.mini_widget = DesktopWidget()
        self.mini_widget.open_main_requested.connect(self.restore_window)
        if config.get("desktop_widget_enabled", False):
            self.mini_widget.show()
            
        # 6. In-App Floating Toast
        self.toast = InAppToast(self)
        
        # Timers
        self.timer_status = QTimer(self)
        self.timer_status.timeout.connect(self.check_power_status)
        self.timer_status.start(10000)
        
        self.timer_refresh = QTimer(self)
        self.timer_refresh.timeout.connect(self.refresh_schedule_data)
        refresh_ms = config.get("auto_refresh_minutes", 15) * 60 * 1000
        self.timer_refresh.start(refresh_ms)
        
        # Apply initial settings
        active_q = config.get("active_queue", "2.1")
        active_a = config.get("active_address", "вул. Дудинської, 9")
        self.sidebar.update_active_queue_info(active_q, active_a)
        self.header.set_active_address_text(active_a)
        
        # Initial schedule fetch asynchronously
        self.log("ElectroBot v2.0 (Material Design 3) запущено.", "INFO")
        self.refresh_schedule_data()

    def show_toast(self, title: str, message: str, toast_type: str = "INFO"):
        self.toast.show_toast(title, message, toast_type, is_dark=(self.current_theme == "dark"))

    def resizeEvent(self, event: QResizeEvent):
        super().resizeEvent(event)
        self.toast.reposition()

    def open_quick_edit_dialog(self):
        active_q = config.get("active_queue", "2.1")
        active_a = config.get("active_address", "вул. Дудинської, 9")
        
        dlg = EditAddressDialog(title="Поточна адреса", address=active_a, queue=active_q, parent=self)
        if dlg.exec():
            data = dlg.get_data()
            new_addr = data["address"]
            new_q = data["queue"]
            if not new_addr:
                return
            config.set("active_address", new_addr)
            config.set("active_queue", new_q)
            self.on_queue_selected(new_q, new_addr)
            self.show_toast("✅ Адресу оновлено!", f"Встановлено: {new_addr} (Черга {new_q})", "SUCCESS")

    def log(self, message: str, level: str = "INFO"):
        self.log_drawer.log(message, level)

    def toggle_console(self):
        is_vis = self.log_drawer.isVisible()
        self.log_drawer.setVisible(not is_vis)

    def toggle_mini_widget(self):
        if self.mini_widget.isVisible():
            self.mini_widget.hide()
            config.set("desktop_widget_enabled", False)
            self.show_toast("📌 Міні-віджет", "Плаваючий віджет приховано.", "INFO")
        else:
            self.mini_widget.show()
            config.set("desktop_widget_enabled", True)
            self.show_toast("📌 Міні-віджет", "Плаваючий віджет закріплено на екрані.", "SUCCESS")
        self.check_power_status()

    def toggle_theme(self):
        new_theme = "light" if self.current_theme == "dark" else "dark"
        self.current_theme = new_theme
        config.set("theme", new_theme)
        
        app = QApplication.instance()
        if app:
            app.setStyleSheet(generate_stylesheet(new_theme))
        self.header.set_theme_button_text(new_theme == "dark")
        self.show_toast("🎨 Тема оформлення", f"Застосовано {new_theme.upper()} (Material Design 3)", "INFO")

    def on_nav_changed(self, index: int, breadcrumb: str):
        self.stack.setCurrentIndex(index)
        self.header.set_breadcrumb(breadcrumb)
        if index == 1:
            self.screen_schedule.refresh_view()

    def on_queue_selected(self, queue_name: str, address_text: str):
        config.set("active_queue", queue_name)
        config.set("active_address", address_text)
        self.sidebar.update_active_queue_info(queue_name, address_text)
        self.header.set_active_address_text(address_text)
        self.screen_schedule.set_active_queue(queue_name, address_text)
        self.screen_search.refresh_favorites_chips()
        self.screen_settings.refresh_favorites_table()
        self.check_power_status()

    def refresh_schedule_data(self):
        self.header.btn_refresh.setEnabled(False)
        self.header.btn_refresh.setText("⏳ Оновлення...")
        self.log("Фонове опитування Telegram-каналу Харківобленерго (t.me/s/kharkivenergy)...", "INFO")
        
        run_async(
            fetch_schedule_from_telegram,
            on_result=self._on_schedule_loaded,
            on_error=self._on_schedule_error,
            on_finished=lambda: (self.header.btn_refresh.setEnabled(True), self.header.btn_refresh.setText("🔄 Оновити"))
        )

    def _on_schedule_loaded(self, sched: DailySchedule):
        self.schedule = sched
        self.screen_schedule.set_schedule(sched)
        self.screen_all.set_schedule(sched)
        self.log(f"Отримано актуальний графік на: {sched.date_str} ({len(sched.queues)} підчерг)", "SUCCESS")
        self.check_power_status()

    def _on_schedule_error(self, err_msg: str):
        self.log(f"Помилка завантаження графіку: {err_msg}", "ERROR")

    def check_power_status(self):
        active_q = config.get("active_queue", "2.1")
        active_a = config.get("active_address", "вул. Дудинської, 9")
        now = datetime.now()
        
        # Resolve unified state
        u_status = resolve_unified_status(active_a, active_q, self.schedule, now)
        
        # Update Header
        self.header.update_status(not u_status.is_outage, active_q, u_status.badge_text)
        self.header.set_active_address_text(active_a)
        
        # Update System Tray
        tray_power_status = PowerStatus.OFF if u_status.is_outage else PowerStatus.ON
        self.tray.update_tray(tray_power_status, active_q, f"{u_status.badge_text} ({u_status.target_time_str})")
        
        # Update Desktop Mini-Widget
        if self.mini_widget.isVisible():
            self.mini_widget.update_data(
                active_q,
                u_status.badge_text,
                u_status.badge_theme,
                u_status.countdown_hms
            )
            
        # Notify user on major state change (e.g. into emergency or back on)
        if self.last_status_state and self.last_status_state != u_status.state_type:
            if u_status.state_type == "EMERGENCY_1562":
                msg = f"За адресою {active_a} зафіксовано аварійні роботи до {u_status.target_time_str}."
                self.tray.show_notification("🚨 АСДС 1562: Аварійне знеструмлення!", msg, is_warning=True)
                self.show_toast("🚨 Аварія 1562!", msg, "ERROR")
            elif u_status.state_type == "EMERGENCY_GAV":
                msg = "Укренерго: Введено аварійні відключення (ГАВ)! Погодинні графіки призупинено."
                self.tray.show_notification("🚨 Екстрені відключення (ГАВ)!", msg, is_warning=True)
                self.show_toast("🚨 Графіки ГАВ", msg, "ERROR")
            elif u_status.state_type == "SCHEDULED_ON":
                msg = f"Світло увімкнено за графіком (Черга {active_q}). {u_status.charge_window_str}"
                self.tray.show_notification("🟢 Електроенергію подано!", msg)
                self.show_toast("🟢 Світло є!", msg, "SUCCESS")
        self.last_status_state = u_status.state_type
        
        # Check upcoming scheduled outages
        if config.get("notify_enabled", True) and not u_status.is_outage and self.schedule:
            warn_mins = config.get("notify_minutes_before", 15)
            current_mins = now.hour * 60 + now.minute
            invs = self.schedule.queues.get(active_q, [])
            for inv in sorted(invs, key=lambda i: i.start_minute):
                if 0 <= (inv.start_minute - current_mins) <= warn_mins:
                    interval_key = f"{active_q}_{inv.start_str}"
                    if self.last_notified_interval != interval_key:
                        self.last_notified_interval = interval_key
                        mins_left = inv.start_minute - current_mins
                        title = f"⚠️ Увага: Відключення світла (Черга {active_q})!"
                        msg = f"За графіком відключення розпочнеться о {inv.start_str} (через {mins_left} хв) і триватиме до {inv.end_str}."
                        self.tray.show_notification(title, msg, is_warning=True)
                        self.show_toast(title, msg, "WARNING")
                        self.log(f"Надіслано сповіщення: {msg}", "WARNING")
                    break

    def test_notification(self):
        q = config.get("active_queue", "2.1")
        self.show_toast("🔔 Тестове сповіщення", f"Сповіщення ElectroBot налаштовані ідеально! Ваша черга: {q}.", "SUCCESS")
        self.tray.show_notification(
            f"⚡ Тестове сповіщення ElectroBot",
            f"Сповіщення Windows налаштовані правильно! Ваша черга: {q}."
        )
        self.log("Надіслано тестове сповіщення.", "SUCCESS")

    def restore_window(self):
        self.show()
        self.setWindowState(self.windowState() & ~Qt.WindowState.WindowMinimized | Qt.WindowState.WindowActive)
        self.activateWindow()
        self.raise_()

    def closeEvent(self, event: QCloseEvent):
        if config.get("minimize_to_tray_on_close", True):
            event.ignore()
            self.hide()
            self.show_toast("⚡ ElectroBot у треї", "Додаток продовжує моніторити мережу у фоні.", "INFO")
        else:
            self.force_exit()

    def force_exit(self):
        config.set("window_width", self.width())
        config.set("window_height", self.height())
        if hasattr(self, 'mini_widget') and self.mini_widget:
            self.mini_widget.hide()
            self.mini_widget.close()
        if hasattr(self, 'tray') and self.tray:
            self.tray.tray_icon.hide()
        if hasattr(self, 'timer_status'):
            self.timer_status.stop()
        if hasattr(self, 'timer_refresh'):
            self.timer_refresh.stop()
        QApplication.instance().quit()
        os._exit(0)
