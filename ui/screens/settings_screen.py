import os
import sys
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QFrame,
    QCheckBox, QSpinBox, QPushButton, QTableWidget, QTableWidgetItem,
    QHeaderView, QLineEdit, QComboBox, QMessageBox, QDialog, QFormLayout, QDialogButtonBox
)
from PyQt6.QtGui import QColor, QFont
from PyQt6.QtCore import pyqtSignal, Qt
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from core.config import config
from core.pdf_indexer import build_database_from_pdf
from core.github_updater import check_github_updates, CURRENT_VERSION
from core.async_workers import run_async
from core.power_station import PowerStationManager, StationTelemetry
from PyQt6.QtGui import QDesktopServices
from PyQt6.QtCore import QUrl

ALL_QUEUES = ["1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"]

class EditAddressDialog(QDialog):
    def __init__(self, title: str = "", address: str = "", queue: str = "1.1", parent=None):
        super().__init__(parent)
        self.setWindowTitle("Редагування адреси")
        self.setFixedWidth(420)
        
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 18)
        layout.setSpacing(12)
        
        form = QFormLayout()
        self.input_title = QLineEdit(title)
        self.input_addr = QLineEdit(address)
        self.combo_q = QComboBox()
        for q in ALL_QUEUES:
            self.combo_q.addItem(f"Черга {q}", q)
        idx = self.combo_q.findData(queue)
        if idx >= 0:
            self.combo_q.setCurrentIndex(idx)
            
        form.addRow("🏷️ Назва мітки:", self.input_title)
        form.addRow("📍 Повна адреса:", self.input_addr)
        form.addRow("⚡ Черга відключення:", self.combo_q)
        layout.addLayout(form)
        
        btn_box = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        btn_box.accepted.connect(self.accept)
        btn_box.rejected.connect(self.reject)
        layout.addWidget(btn_box)

    def get_data(self):
        return {
            "title": self.input_title.text().strip() or "Моя адреса",
            "address": self.input_addr.text().strip(),
            "queue": self.combo_q.currentData()
        }

class SettingsScreen(QWidget):
    test_notification_requested = pyqtSignal()
    settings_saved = pyqtSignal()
    log_message = pyqtSignal(str, str)
    notify_toast = pyqtSignal(str, str, str)

    def __init__(self, parent=None):
        super().__init__(parent)
        
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(20, 20, 20, 20)
        main_layout.setSpacing(16)
        
        lbl_screen_title = QLabel("⚙️ Налаштування моніторингу та збережених адрес")
        lbl_screen_title.setStyleSheet("font-size: 16px; font-weight: 700; color: #58a6ff;")
        main_layout.addWidget(lbl_screen_title)
        
        # 1. Monitoring Settings Card
        card_mon = QFrame()
        card_mon.setObjectName("CardFrame")
        m_layout = QVBoxLayout(card_mon)
        m_layout.setContentsMargins(16, 16, 16, 16)
        m_layout.setSpacing(12)
        
        lbl_mon_title = QLabel("🔔 Сповіщення та системний трей")
        lbl_mon_title.setStyleSheet("font-weight: 700; font-size: 13px;")
        m_layout.addWidget(lbl_mon_title)
        
        self.chk_notify = QCheckBox("Показувати спливаючі сповіщення Windows перед початком відключення світла")
        self.chk_notify.setChecked(config.get("notify_enabled", True))
        self.chk_notify.stateChanged.connect(self._save_settings)
        m_layout.addWidget(self.chk_notify)
        
        h_spin1 = QHBoxLayout()
        lbl_spin1 = QLabel("Попереджати за скільки хвилин до початку вимкнення:")
        lbl_spin1.setStyleSheet("color: #9ba3b4;")
        self.spin_minutes = QSpinBox()
        self.spin_minutes.setRange(5, 60)
        self.spin_minutes.setValue(config.get("notify_minutes_before", 15))
        self.spin_minutes.valueChanged.connect(self._save_settings)
        h_spin1.addWidget(lbl_spin1)
        h_spin1.addWidget(self.spin_minutes)
        h_spin1.addStretch()
        m_layout.addLayout(h_spin1)
        
        h_spin2 = QHBoxLayout()
        lbl_spin2 = QLabel("Періодичність автооновлення розкладу з Telegram (хвилин):")
        lbl_spin2.setStyleSheet("color: #9ba3b4;")
        self.spin_refresh = QSpinBox()
        self.spin_refresh.setRange(5, 120)
        self.spin_refresh.setValue(config.get("auto_refresh_minutes", 15))
        self.spin_refresh.valueChanged.connect(self._save_settings)
        h_spin2.addWidget(lbl_spin2)
        h_spin2.addWidget(self.spin_refresh)
        h_spin2.addStretch()
        m_layout.addLayout(h_spin2)
        
        self.chk_tray = QCheckBox("Згортати у системний трей при натисканні на хрестик (✕) замість закриття")
        self.chk_tray.setChecked(config.get("minimize_to_tray_on_close", True))
        self.chk_tray.stateChanged.connect(self._save_settings)
        m_layout.addWidget(self.chk_tray)
        
        # Router Ping Detector
        self.chk_ping = QCheckBox("📡 Фізичний детектор світла: періодично пінгувати роутер (як у СвітлоБоті)")
        self.chk_ping.setChecked(config.get("router_ping_enabled", True))
        self.chk_ping.stateChanged.connect(self._save_settings)
        m_layout.addWidget(self.chk_ping)
        
        h_router = QHBoxLayout()
        lbl_router = QLabel("IP роутера (за замовчуванням визначається автоматично):")
        lbl_router.setStyleSheet("color: #9ba3b4;")
        self.input_router_ip = QLineEdit(config.get("router_ip", ""))
        self.input_router_ip.setPlaceholderText("напр. 192.168.1.1 або пусто для авто")
        self.input_router_ip.setFixedWidth(200)
        self.input_router_ip.textChanged.connect(self._save_settings)
        h_router.addWidget(lbl_router)
        h_router.addWidget(self.input_router_ip)
        h_router.addStretch()
        m_layout.addLayout(h_router)
        
        # Desktop Mini-Widget
        self.chk_mini_widget = QCheckBox("📌 Показувати компактний плаваючий міні-віджет на робочому столі")
        self.chk_mini_widget.setChecked(config.get("desktop_widget_enabled", False))
        self.chk_mini_widget.stateChanged.connect(self._save_settings)
        m_layout.addWidget(self.chk_mini_widget)
        
        btn_test_notif = QPushButton("🔔 Перевірити сповіщення Windows (Тестовий виклик)")
        btn_test_notif.setObjectName("SecondaryButton")
        btn_test_notif.setFixedWidth(280)
        btn_test_notif.clicked.connect(self.test_notification_requested.emit)
        m_layout.addWidget(btn_test_notif)
        
        main_layout.addWidget(card_mon)
        
        # 1.5 Power Station Card (EcoFlow River Pro / Bluetti El30V2)
        card_station = QFrame()
        card_station.setObjectName("CardFrame")
        st_layout = QVBoxLayout(card_station)
        st_layout.setContentsMargins(16, 16, 16, 16)
        st_layout.setSpacing(10)

        lbl_st_title = QLabel("🔋 Зарядні станції (Без введення ключів чи паролів)")
        lbl_st_title.setStyleSheet("font-weight: 700; font-size: 13px; color: #58a6ff;")
        st_layout.addWidget(lbl_st_title)

        lbl_st_desc = QLabel(
            "Оскільки роутер підключений до станції 24/7, оберіть вашу модель або датчик у 1 клік — "
            "жодних реєстрацій, API ключів чи складних паролів:"
        )
        lbl_st_desc.setWordWrap(True)
        lbl_st_desc.setStyleSheet("color: #9ba3b4; font-size: 11px;")
        st_layout.addWidget(lbl_st_desc)

        h_st_controls = QHBoxLayout()
        
        lbl_b_sel = QLabel("Станція:")
        lbl_b_sel.setStyleSheet("color: #9ba3b4;")
        self.combo_st_brand = QComboBox()
        self.combo_st_brand.addItem("⚡ EcoFlow River Pro", "ECOFLOW")
        self.combo_st_brand.addItem("⚡ Bluetti El30V2", "BLUETTI")
        self.combo_st_brand.addItem("🔌 Датчик розетки 220В", "CHARGER")
        self.combo_st_brand.addItem("✕ Вимкнено", "NONE")
        
        cur_brand = PowerStationManager.get_selected_brand()
        b_idx = self.combo_st_brand.findData(cur_brand)
        if b_idx >= 0:
            self.combo_st_brand.setCurrentIndex(b_idx)
        self.combo_st_brand.currentIndexChanged.connect(self._on_station_changed)
        
        lbl_m_sel = QLabel("Стан мережі 220В:")
        lbl_m_sel.setStyleSheet("color: #9ba3b4; margin-left: 12px;")
        self.combo_st_mode = QComboBox()
        self.combo_st_mode.addItem("⚡ Авто (Фізичний датчик)", "AUTO")
        self.combo_st_mode.addItem("🟢 Світло є (+Вхід 220В)", "ON")
        self.combo_st_mode.addItem("🔴 Знеструмлено (0 Вт)", "OFF")
        
        cur_mode = PowerStationManager.get_grid_state()
        m_idx = self.combo_st_mode.findData(cur_mode)
        if m_idx >= 0:
            self.combo_st_mode.setCurrentIndex(m_idx)
        self.combo_st_mode.currentIndexChanged.connect(self._on_station_changed)
        
        btn_refresh_st = QPushButton("🔄 Оновити")
        btn_refresh_st.setObjectName("SecondaryButton")
        btn_refresh_st.clicked.connect(self._refresh_station_status)

        h_st_controls.addWidget(lbl_b_sel)
        h_st_controls.addWidget(self.combo_st_brand)
        h_st_controls.addWidget(lbl_m_sel)
        h_st_controls.addWidget(self.combo_st_mode)
        h_st_controls.addWidget(btn_refresh_st)
        h_st_controls.addStretch()
        st_layout.addLayout(h_st_controls)

        self.lbl_st_status = QLabel("Завантаження стану станції...")
        self.lbl_st_status.setStyleSheet("color: #e6edf3; font-size: 11px; background: #1f222b; border: 1px solid #383d4a; border-radius: 6px; padding: 8px;")
        st_layout.addWidget(self.lbl_st_status)

        main_layout.addWidget(card_station)
        
        # 2. GitHub Update Card
        card_update = QFrame()
        card_update.setObjectName("CardFrame")
        u_layout = QVBoxLayout(card_update)
        u_layout.setContentsMargins(16, 14, 16, 14)
        u_layout.setSpacing(10)
        
        lbl_u_title = QLabel("🚀 Оновлення ElectroBot (GitHub Releases)")
        lbl_u_title.setStyleSheet("font-weight: 700; font-size: 13px; color: #58a6ff;")
        
        lbl_u_ver = QLabel(f"Поточна версія: ElectroBot {CURRENT_VERSION}  |  Репозиторій: github.com/drkoh/ElectroBot")
        lbl_u_ver.setStyleSheet("color: #9ba3b4; font-size: 12px;")
        
        row_btn_u = QHBoxLayout()
        self.btn_check_update = QPushButton("🔄 Перевірити наявність оновлень")
        self.btn_check_update.setObjectName("PrimaryButton")
        self.btn_check_update.clicked.connect(self._check_for_updates)
        
        btn_open_gh = QPushButton("🌐 Сторінка релізів GitHub")
        btn_open_gh.setObjectName("SecondaryButton")
        btn_open_gh.clicked.connect(lambda: QDesktopServices.openUrl(QUrl("https://github.com/drkoh/ElectroBot/releases")))
        
        row_btn_u.addWidget(self.btn_check_update)
        row_btn_u.addWidget(btn_open_gh)
        row_btn_u.addStretch()
        
        u_layout.addWidget(lbl_u_title)
        u_layout.addWidget(lbl_u_ver)
        u_layout.addLayout(row_btn_u)
        main_layout.addWidget(card_update)
        
        # 2. Favorites Address Management Card
        card_favs = QFrame()
        card_favs.setObjectName("CardFrame")
        f_layout = QVBoxLayout(card_favs)
        f_layout.setContentsMargins(16, 16, 16, 16)
        f_layout.setSpacing(10)
        
        lbl_favs_title = QLabel("⭐ Мої збережені адреси:")
        lbl_favs_title.setStyleSheet("font-weight: 700; font-size: 13px;")
        f_layout.addWidget(lbl_favs_title)
        
        self.table_favs = QTableWidget()
        self.table_favs.setColumnCount(4)
        self.table_favs.setHorizontalHeaderLabels(["Мітка", "Адреса", "Черга", "Дії"])
        self.table_favs.setAlternatingRowColors(True)
        self.table_favs.verticalHeader().setVisible(False)
        self.table_favs.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.table_favs.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.table_favs.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.table_favs.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        f_layout.addWidget(self.table_favs)
        
        # Add favorite row
        h_add = QHBoxLayout()
        self.input_fav_title = QLineEdit()
        self.input_fav_title.setPlaceholderText("Мітка: напр. Мій дім")
        self.input_fav_title.setFixedWidth(160)
        
        self.input_fav_addr = QLineEdit()
        self.input_fav_addr.setPlaceholderText("Адреса: напр. вул. Дудинської, 10")
        
        self.combo_fav_q = QComboBox()
        for q in ALL_QUEUES:
            self.combo_fav_q.addItem(f"Черга {q}", q)
            
        btn_add_fav = QPushButton("+ Зберегти нову адресу")
        btn_add_fav.setObjectName("PrimaryButton")
        btn_add_fav.clicked.connect(self._add_favorite)
        
        h_add.addWidget(self.input_fav_title)
        h_add.addWidget(self.input_fav_addr)
        h_add.addWidget(self.combo_fav_q)
        h_add.addWidget(btn_add_fav)
        f_layout.addLayout(h_add)
        
        main_layout.addWidget(card_favs)
        
        self.refresh_favorites_table()
        self._on_station_changed()
        self._refresh_station_status()

    def _on_station_changed(self):
        brand = self.combo_st_brand.currentData()
        mode = self.combo_st_mode.currentData()
        PowerStationManager.set_selected_brand(brand)
        PowerStationManager.set_grid_state(mode)
        self._refresh_station_status()
        self.settings_saved.emit()

    def _refresh_station_status(self):
        tel = PowerStationManager.get_telemetry()
        if not tel.is_configured:
            self.lbl_st_status.setText("Моніторинг станції вимкнено.")
            return
            
        icon = "🟢" if tel.is_grid_online else "🔴"
        mode_str = "Мережа 220В активна (йде зарядка/байпас)" if tel.is_grid_online else "Знеструмлено (робота від станції)"
        rem = tel.format_remaining_time()
        self.lbl_st_status.setText(
            f"{icon} {tel.model_name} | {mode_str}\n"
            f"🔋 Заряд: {tel.battery_percent}% | ⚡ Вхід 220В: {tel.in_watts} Вт | 💡 Вихід: {tel.out_watts} Вт | ⏱️ Автономність: {rem}\n"
            f"ℹ️ {tel.status_details}"
        )

    def _save_settings(self):
        config.set("notify_enabled", self.chk_notify.isChecked())
        config.set("notify_minutes_before", self.spin_minutes.value())
        config.set("auto_refresh_minutes", self.spin_refresh.value())
        config.set("minimize_to_tray_on_close", self.chk_tray.isChecked())
        config.set("router_ping_enabled", self.chk_ping.isChecked())
        config.set("router_ip", self.input_router_ip.text().strip())
        config.set("desktop_widget_enabled", self.chk_mini_widget.isChecked())
        self.settings_saved.emit()
        self.settings_saved.emit()

    def refresh_favorites_table(self):
        favs = config.get_favorites()
        self.table_favs.setRowCount(len(favs))
        
        for idx, f in enumerate(favs):
            item_title = QTableWidgetItem(f.get("title", ""))
            item_addr = QTableWidgetItem(f.get("address", ""))
            
            item_q = QTableWidgetItem(f"Черга {f.get('queue', '')}")
            item_q.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            f_bold = QFont()
            f_bold.setBold(True)
            item_q.setFont(f_bold)
            item_q.setForeground(QColor("#58a6ff"))
            
            # Action buttons
            w_acts = QWidget()
            h_acts = QHBoxLayout(w_acts)
            h_acts.setContentsMargins(4, 2, 4, 2)
            h_acts.setSpacing(6)
            
            btn_edit = QPushButton("✏️ Змінити")
            btn_edit.setObjectName("SecondaryButton")
            btn_edit.setFixedHeight(24)
            btn_edit.setStyleSheet("padding: 2px 8px; font-size: 11px;")
            btn_edit.clicked.connect(lambda ch, item=f: self._edit_favorite(item))
            
            btn_del = QPushButton("🗑️ Видалити")
            btn_del.setObjectName("DangerButton")
            btn_del.setFixedHeight(24)
            btn_del.setStyleSheet("padding: 2px 8px; font-size: 11px;")
            fav_id = f.get("id")
            btn_del.clicked.connect(lambda ch, fid=fav_id: self._delete_favorite(fid))
            
            h_acts.addWidget(btn_edit)
            h_acts.addWidget(btn_del)
            
            self.table_favs.setItem(idx, 0, item_title)
            self.table_favs.setItem(idx, 1, item_addr)
            self.table_favs.setItem(idx, 2, item_q)
            self.table_favs.setCellWidget(idx, 3, w_acts)

    def _add_favorite(self):
        title = self.input_fav_title.text().strip() or "Моя адреса"
        addr = self.input_fav_addr.text().strip()
        q = self.combo_fav_q.currentData()
        
        if not addr:
            QMessageBox.warning(self, "Помилка", "Будь ласка, введіть повну адресу.")
            return
            
        config.add_favorite(title, addr, q)
        self.input_fav_title.clear()
        self.input_fav_addr.clear()
        self.refresh_favorites_table()
        self.log_message.emit(f"Додано нову адресу: '{title}' ({addr})", "SUCCESS")

    def _edit_favorite(self, fav_item: dict):
        dlg = EditAddressDialog(title=fav_item.get("title", ""),
                                address=fav_item.get("address", ""),
                                queue=fav_item.get("queue", "1.1"),
                                parent=self)
        if dlg.exec():
            data = dlg.get_data()
            config.edit_favorite(fav_item.get("id"), data["title"], data["address"], data["queue"])
            self.refresh_favorites_table()
            self.log_message.emit(f"Адресу '{data['title']}' оновлено.", "SUCCESS")

    def _delete_favorite(self, fav_id: str):
        config.remove_favorite(fav_id)
        self.refresh_favorites_table()
        self.log_message.emit("Адресу видалено.", "INFO")

    def _check_for_updates(self):
        self.btn_check_update.setEnabled(False)
        self.btn_check_update.setText("⏳ Перевірка GitHub...")
        run_async(
            check_github_updates,
            on_result=self._on_update_result,
            on_error=lambda err: (
                self.btn_check_update.setEnabled(True),
                self.btn_check_update.setText("🔄 Перевірити наявність оновлень"),
                self.notify_toast.emit("GitHub", f"Помилка перевірки оновлень: {err}", "ERROR")
            ),
            on_finished=lambda: (
                self.btn_check_update.setEnabled(True),
                self.btn_check_update.setText("🔄 Перевірити наявність оновлень")
            )
        )

    def _on_update_result(self, info):
        if info.has_update:
            msg = f"🚀 Доступна нова версія: {info.latest_version}!\n\nОпис оновлення:\n{info.release_notes[:350]}\n\nБажаєте завантажити оновлення?"
            ans = QMessageBox.question(self, "Доступне оновлення!", msg, QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No)
            if ans == QMessageBox.StandardButton.Yes:
                url = info.download_url_exe or info.html_url
                QDesktopServices.openUrl(QUrl(url))
        else:
            self.notify_toast.emit("✅ Оновлення", f"У вас встановлена остання версія ({CURRENT_VERSION}).", "SUCCESS")
