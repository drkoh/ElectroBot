import os
import sys
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QFrame, QScrollArea,
    QComboBox, QMessageBox, QDialog, QFormLayout, QDialogButtonBox, QApplication
)
from PyQt6.QtGui import QColor, QFont, QDesktopServices
from PyQt6.QtCore import pyqtSignal, Qt, QUrl

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from core.pdf_indexer import search_records
from core.config import config
from core.service_1562 import check_address_in_1562, EmergencyOutage1562
from core.address_utils import extract_street_and_building
from ui.context_menu_helper import attach_line_edit_context_menu, attach_table_context_menu

ALL_QUEUES = ["1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"]

class EditAddressDialog(QDialog):
    """Material 3 Dialog to edit or add an address."""
    def __init__(self, title: str = "", address: str = "", queue: str = "2.1", parent=None):
        super().__init__(parent)
        self.setWindowTitle("Редагування адреси та черги")
        self.setFixedWidth(460)
        self.setObjectName("CardFrame")
        
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(14)
        
        lbl_head = QLabel("📍 Налаштування адреси моніторингу")
        lbl_head.setStyleSheet("font-size: 15px; font-weight: 700;")
        layout.addWidget(lbl_head)
        
        form = QFormLayout()
        form.setSpacing(12)
        
        self.input_title = QLineEdit(title or "Мій дім")
        self.input_title.setPlaceholderText("Назва мітки: наприклад, Дім, Офіс")
        attach_line_edit_context_menu(self.input_title)
        
        self.input_addr = QLineEdit(address)
        self.input_addr.setPlaceholderText("Вулиця та номер: напр. вул. Дудинської, 9")
        attach_line_edit_context_menu(self.input_addr)
        
        self.combo_q = QComboBox()
        for q in ALL_QUEUES:
            self.combo_q.addItem(f"Черга {q}", q)
        idx = self.combo_q.findData(queue)
        if idx >= 0:
            self.combo_q.setCurrentIndex(idx)
            
        form.addRow("🏷️ Назва мітки:", self.input_title)
        form.addRow("📍 Адреса з будинком:", self.input_addr)
        form.addRow("⚡ Черга відключення:", self.combo_q)
        layout.addLayout(form)
        
        lbl_hint = QLabel("💡 Номер підчерги визначається графіком Харківобленерго (напр. 2.1, 1.1). Ви можете легко змінити його будь-коли.")
        lbl_hint.setStyleSheet("font-size: 11px; font-style: italic;")
        lbl_hint.setWordWrap(True)
        layout.addWidget(lbl_hint)
        
        btn_box = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        btn_box.button(QDialogButtonBox.StandardButton.Ok).setText("Зберегти")
        btn_box.button(QDialogButtonBox.StandardButton.Cancel).setText("Скасувати")
        btn_box.accepted.connect(self.accept)
        btn_box.rejected.connect(self.reject)
        layout.addWidget(btn_box)

    def get_data(self):
        return {
            "title": self.input_title.text().strip() or "Моя адреса",
            "address": self.input_addr.text().strip(),
            "queue": self.combo_q.currentData()
        }

class SearchScreen(QWidget):
    queue_selected = pyqtSignal(str, str) # queue_name, address_text
    log_message = pyqtSignal(str, str)
    notify_toast = pyqtSignal(str, str, str) # title, message, type

    def __init__(self, parent=None):
        super().__init__(parent)
        
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(20, 20, 20, 20)
        main_layout.setSpacing(14)
        
        # 1. Search Box Card
        card_search = QFrame()
        card_search.setObjectName("CardFrame")
        c_layout = QVBoxLayout(card_search)
        c_layout.setContentsMargins(18, 16, 18, 16)
        c_layout.setSpacing(10)
        
        lbl_title = QLabel("🔍 Пошук адреси та перевірка черги (Харків та область)")
        lbl_title.setStyleSheet("font-size: 15px; font-weight: 700;")
        c_layout.addWidget(lbl_title)
        
        lbl_sub = QLabel("Введіть назву вулиці та номер будинку (наприклад: Дудинської 9, Сумська 10, Клочківська 197):")
        lbl_sub.setStyleSheet("font-size: 12px;")
        lbl_sub.setWordWrap(True)
        c_layout.addWidget(lbl_sub)
        
        # Search input row
        h_search = QHBoxLayout()
        h_search.setSpacing(8)
        
        self.input_search = QLineEdit()
        self.input_search.setPlaceholderText("Введіть вулицю або об'єкт...")
        self.input_search.returnPressed.connect(self.perform_search)
        attach_line_edit_context_menu(self.input_search, on_copied=lambda msg: self.notify_toast.emit("📋 Буфер", msg, "INFO"))
        
        self.btn_search = QPushButton("Знайти вулицю")
        self.btn_search.setObjectName("PrimaryButton")
        self.btn_search.clicked.connect(self.perform_search)
        
        h_search.addWidget(self.input_search)
        h_search.addWidget(self.btn_search)
        c_layout.addLayout(h_search)
        
        # Search Result Visual Confirmation Banner (Visible feedback)
        self.banner_search_feedback = QFrame()
        self.banner_search_feedback.setObjectName("CardFrame")
        fb_layout = QHBoxLayout(self.banner_search_feedback)
        fb_layout.setContentsMargins(12, 6, 12, 6)
        self.lbl_search_feedback = QLabel("Введіть адресу для пошуку.")
        self.lbl_search_feedback.setStyleSheet("font-weight: 600; font-size: 12px;")
        fb_layout.addWidget(self.lbl_search_feedback)
        fb_layout.addStretch()
        c_layout.addWidget(self.banner_search_feedback)
        
        # Official oblenergo helper button
        h_obl = QHBoxLayout()
        h_obl.setSpacing(8)
        
        btn_obl = QPushButton("🌐 Офіційний сервіс «Відключення за адресою» на сайті Харківобленерго")
        btn_obl.setObjectName("SecondaryButton")
        btn_obl.setToolTip("Відкриває офіційну сторінку Харківобленерго для перевірки конкретного будинку")
        btn_obl.clicked.connect(lambda: QDesktopServices.openUrl(QUrl("https://www.oblenergo.kharkov.ua/gaoinfo")))
        h_obl.addWidget(btn_obl)
        h_obl.addStretch()
        c_layout.addLayout(h_obl)
        
        main_layout.addWidget(card_search)
        
        # 1562 Emergency Disruption Banner
        self.card_1562 = QFrame()
        self.card_1562.setObjectName("EmergencyBannerFrame")
        c_1562_box = QVBoxLayout(self.card_1562)
        c_1562_box.setContentsMargins(12, 10, 12, 10)
        c_1562_box.setSpacing(4)
        
        self.lbl_1562_head = QLabel("🚨 АСДС 1562: ЗА ЦІЄЮ АДРЕСОЮ ЗАФІКСОВАНО АКТИВНЕ АВАРІЙНЕ ЗНЕСТРУМЛЕННЯ!")
        self.lbl_1562_head.setObjectName("EmergencyBannerTitle")
        
        self.lbl_1562_body = QLabel("Деталі аварії завантажуються...")
        self.lbl_1562_body.setObjectName("EmergencyBannerDesc")
        self.lbl_1562_body.setWordWrap(True)
        
        c_1562_box.addWidget(self.lbl_1562_head)
        c_1562_box.addWidget(self.lbl_1562_body)
        main_layout.addWidget(self.card_1562)
        self.card_1562.setVisible(False)
        
        # 2. Results Table
        self.table_results = QTableWidget()
        self.table_results.setColumnCount(4)
        self.table_results.setHorizontalHeaderLabels(["Джерело", "Знайдена вулиця / Об'єкт", "Черга відключення", "Дії"])
        self.table_results.setAlternatingRowColors(True)
        self.table_results.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table_results.verticalHeader().setVisible(False)
        self.table_results.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.table_results.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.table_results.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.table_results.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        
        attach_table_context_menu(
            self.table_results,
            on_copied=lambda msg: self.notify_toast.emit("📋 Буфер", msg, "SUCCESS")
        )
        main_layout.addWidget(self.table_results)
        
        # 3. Bottom Favorites Management Card
        self.card_favs = QFrame()
        self.card_favs.setObjectName("CardFrame")
        fav_box = QVBoxLayout(self.card_favs)
        fav_box.setContentsMargins(14, 12, 14, 12)
        fav_box.setSpacing(8)
        
        h_fav_hdr = QHBoxLayout()
        lbl_fav_hdr = QLabel("⭐ Мої збережені адреси:")
        lbl_fav_hdr.setStyleSheet("font-weight: 700; font-size: 13px;")
        
        btn_add_manual = QPushButton("+ Додати нову адресу")
        btn_add_manual.setObjectName("SecondaryButton")
        btn_add_manual.setFixedHeight(28)
        btn_add_manual.clicked.connect(self._on_add_new_address_clicked)
        
        h_fav_hdr.addWidget(lbl_fav_hdr)
        h_fav_hdr.addStretch()
        h_fav_hdr.addWidget(btn_add_manual)
        fav_box.addLayout(h_fav_hdr)
        
        self.favs_container = QHBoxLayout()
        self.favs_container.setSpacing(8)
        fav_box.addLayout(self.favs_container)
        
        main_layout.addWidget(self.card_favs)
        
        self.refresh_favorites_chips()
        
        # Pre-fill query with real street address
        cur_addr = config.get("active_address", "вул. Дудинської, 9")
        if "черга" not in cur_addr.lower() and len(cur_addr.strip()) > 3:
            self.input_search.setText(cur_addr.replace("Харків, ", "").strip())
            self.perform_search()
        else:
            self.input_search.setPlaceholderText("Введіть назву вулиці та номер будинку (наприклад: Дудинської, 9)...")
            self.card_1562.setVisible(False)

    def perform_search(self):
        query = self.input_search.text().strip()
        if not query or query.lower() in ["харків", "харків (", "україна"]:
            self.lbl_search_feedback.setText("⚠️ Введіть назву вулиці у поле пошуку.")
            self.card_1562.setVisible(False)
            self.table_results.setRowCount(0)
            return
            
        street_clean, bld_clean = extract_street_and_building(query)
        search_term = street_clean if street_clean else query
        
        # Don't search if query is purely generic
        if "черга" in search_term.lower() or len(search_term) < 3:
            self.lbl_search_feedback.setText("⚠️ Введіть конкретну назву вулиці Харкова.")
            self.card_1562.setVisible(False)
            self.table_results.setRowCount(0)
            return

        self.log_message.emit(f"Пошук вулиці: '{search_term}' (будинок: '{bld_clean or 'всі'}')...", "INFO")
        
        # Check 1562 emergency announcements for this street and building
        try:
            active_1562 = check_address_in_1562(search_term, bld_clean)
            if active_1562:
                o = active_1562[0]
                streets_str = "; ".join([f"{s} (буд. {', '.join(b)})" for s, b in o.affected_streets.items()])
                msg_text = (
                    f"⚠️ Початок: {o.start_time}  |  Плановане відновлення: {o.planned_end_time}\n"
                    f"🏢 Будинки: {streets_str}\n"
                    f"⚡ Причина: Відсутня електроенергія на джерелі ({o.work_type}).\n"
                    f"Виконавець: {o.service}"
                )
                self.lbl_1562_body.setText(msg_text)
                self.card_1562.setVisible(True)
                self.notify_toast.emit("🚨 Аварія 1562!", f"За адресою {query} зафіксовано знеструмлення до {o.planned_end_time}", "ERROR")
            else:
                self.card_1562.setVisible(False)
        except Exception as e:
            print(f"Error checking 1562 in search: {e}")
            self.card_1562.setVisible(False)
            
        results = search_records(search_term, limit=50)
        self.table_results.setRowCount(len(results))
        
        if not results:
            self.lbl_search_feedback.setText(f"❌ За запитом '{query}' нічого не знайдено в міському реєстрі. Перевірте правильність написання.")
            self.notify_toast.emit("Пошук", f"Вулицю '{search_term}' не знайдено в базі.", "WARNING")
            return
            
        bld_info = f", будинок: {bld_clean}" if bld_clean else ""
        self.lbl_search_feedback.setText(f"✅ Вулицю '{search_term}' знайдено в офіційному реєстрі м. Харкова{bld_info}! Обрано результатів: {len(results)}.")
        
        # Check if user has a favorite for this address to auto-detect queue
        saved_fav = next((f for f in config.get_favorites() if search_term.lower() in f.get("address", "").lower()), None)
        auto_detected_q = saved_fav.get("queue") if saved_fav else config.get("active_queue", "2.1")
        
        for row_idx, r in enumerate(results):
            # Source
            item_src = QTableWidgetItem(r["source"])
            item_src.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            
            # Address
            addr_text = r["address"]
            if bld_clean and not any(kw in addr_text.lower() for kw in ["буд", "д.", "1", "2", "3", "4", "5", "6", "7", "8", "9"]):
                addr_text = f"{addr_text}, буд. {bld_clean}"
            item_addr = QTableWidgetItem(addr_text)
            
            # Queues Widget
            w_queue = QWidget()
            h_q = QHBoxLayout(w_queue)
            h_q.setContentsMargins(4, 2, 4, 2)
            h_q.setSpacing(6)
            
            combo_q = QComboBox()
            for q in ALL_QUEUES:
                combo_q.addItem(f"Черга {q}", q)
                
            if r["queues"]:
                primary_q = r["queues"][0]
                idx = combo_q.findData(primary_q)
                if idx >= 0:
                    combo_q.setCurrentIndex(idx)
                combo_q.setEnabled(False)
                lbl_q = QLabel(f"Черга {primary_q}")
                lbl_q.setStyleSheet("font-weight: bold;")
                h_q.addWidget(lbl_q)
            else:
                # Pre-select auto detected queue
                idx = combo_q.findData(auto_detected_q)
                if idx >= 0:
                    combo_q.setCurrentIndex(idx)
                combo_q.setToolTip("Оберіть номер вашої черги для цієї вулиці")
                h_q.addWidget(combo_q)
                
            # Actions widget
            w_actions = QWidget()
            h_act = QHBoxLayout(w_actions)
            h_act.setContentsMargins(4, 2, 4, 2)
            h_act.setSpacing(6)
            
            btn_select = QPushButton("🏠 Зробити моєю")
            btn_select.setObjectName("PrimaryButton")
            btn_select.setFixedHeight(28)
            btn_select.setToolTip("Встановити цю адресу як мою основну: ElectroBot показуватиме для неї графік, таймер та аварії")
            btn_select.clicked.connect(lambda ch, a=addr_text, cb=combo_q, rq=r["queues"]: self._on_select_row(a, cb, rq))
            
            btn_fav = QPushButton("⭐ Зберегти")
            btn_fav.setObjectName("SecondaryButton")
            btn_fav.setFixedHeight(28)
            btn_fav.setToolTip("Додати в «Мої збережені адреси» (Дім, Офіс, Батьки тощо) для перемикання в 1 клік")
            btn_fav.clicked.connect(lambda ch, a=addr_text, cb=combo_q, rq=r["queues"]: self._on_save_row(a, cb, rq))
            
            h_act.addWidget(btn_select)
            h_act.addWidget(btn_fav)
            
            self.table_results.setItem(row_idx, 0, item_src)
            self.table_results.setItem(row_idx, 1, item_addr)
            self.table_results.setCellWidget(row_idx, 2, w_queue)
            self.table_results.setCellWidget(row_idx, 3, w_actions)

    def _on_select_row(self, address_text: str, combo_q: QComboBox, raw_queues: list):
        chosen_queue = raw_queues[0] if raw_queues else combo_q.currentData()
        config.set("active_queue", chosen_queue)
        config.set("active_address", address_text)
        self.notify_toast.emit("✅ Адресу обрано!", f"Встановлено активну адресу: {address_text} (Черга {chosen_queue})", "SUCCESS")
        self.queue_selected.emit(chosen_queue, address_text)

    def _on_save_row(self, address_text: str, combo_q: QComboBox, raw_queues: list):
        chosen_queue = raw_queues[0] if raw_queues else combo_q.currentData()
        dlg = EditAddressDialog(title="Мій дім", address=address_text, queue=chosen_queue, parent=self)
        if dlg.exec():
            data = dlg.get_data()
            if not data["address"]:
                QMessageBox.warning(self, "Помилка", "Будь ласка, вкажіть адресу.")
                return
            config.add_favorite(data["title"], data["address"], data["queue"])
            self.refresh_favorites_chips()
            self.notify_toast.emit("⭐ Збережено!", f"Адресу '{data['title']}' додано до обраних.", "SUCCESS")

    def _on_add_new_address_clicked(self):
        cur_addr = config.get("active_address", "Харків, вул. Дудинської, 9")
        cur_q = config.get("active_queue", "2.1")
        dlg = EditAddressDialog(title="Моя адреса", address=cur_addr, queue=cur_q, parent=self)
        if dlg.exec():
            data = dlg.get_data()
            if not data["address"]:
                QMessageBox.warning(self, "Помилка", "Будь ласка, вкажіть адресу.")
                return
            config.add_favorite(data["title"], data["address"], data["queue"])
            self.refresh_favorites_chips()
            self.notify_toast.emit("⭐ Збережено!", f"Нову адресу '{data['title']}' збережено.", "SUCCESS")

    def _edit_favorite(self, fav_item: dict):
        dlg = EditAddressDialog(title=fav_item.get("title", ""), 
                                address=fav_item.get("address", ""), 
                                queue=fav_item.get("queue", "2.1"), 
                                parent=self)
        if dlg.exec():
            data = dlg.get_data()
            config.edit_favorite(fav_item.get("id"), data["title"], data["address"], data["queue"])
            self.refresh_favorites_chips()
            self.notify_toast.emit("✏️ Оновлено", f"Адресу '{data['title']}' успішно оновлено.", "INFO")

    def refresh_favorites_chips(self):
        while self.favs_container.count() > 0:
            child = self.favs_container.takeAt(0)
            if child.widget():
                child.widget().deleteLater()
                
        favs = config.get_favorites()
        if not favs:
            lbl_none = QLabel("ℹ️ Немає збережених адрес. Знайдіть свою вулицю вище та натисніть «⭐ Зберегти».")
            lbl_none.setStyleSheet("font-size: 12px; font-style: italic;")
            self.favs_container.addWidget(lbl_none)
        else:
            for f in favs:
                f_frame = QFrame()
                f_frame.setObjectName("CardFrame")
                f_box = QHBoxLayout(f_frame)
                f_box.setContentsMargins(6, 4, 6, 4)
                f_box.setSpacing(6)
                
                btn_act = QPushButton(f"⚡ {f['title']} (Черга {f['queue']})")
                btn_act.setObjectName("TonalButton")
                btn_act.setFixedHeight(28)
                btn_act.setToolTip(f"{f['address']}\n(Клікніть, щоб перемкнути ElectroBot на моніторинг цієї адреси)")
                btn_act.clicked.connect(lambda ch, q=f['queue'], a=f['address']: self._activate_favorite(q, a))
                
                btn_ed = QPushButton("✏️")
                btn_ed.setFixedSize(26, 26)
                btn_ed.setObjectName("SecondaryButton")
                btn_ed.setToolTip("Редагувати цю адресу або змінити чергу")
                btn_ed.clicked.connect(lambda ch, item=f: self._edit_favorite(item))
                
                btn_del = QPushButton("✕")
                btn_del.setFixedSize(26, 26)
                btn_del.setObjectName("SecondaryButton")
                btn_del.setStyleSheet("color: #da3633;")
                btn_del.setToolTip("Видалити з обраних")
                btn_del.clicked.connect(lambda ch, fid=f['id']: self._delete_fav(fid))
                
                f_box.addWidget(btn_act)
                f_box.addWidget(btn_ed)
                f_box.addWidget(btn_del)
                self.favs_container.addWidget(f_frame)
                
        self.favs_container.addStretch()

    def _activate_favorite(self, queue: str, address: str):
        config.set("active_queue", queue)
        config.set("active_address", address)
        self.notify_toast.emit("⚡ Активовано", f"Обрано: {address} (Черга {queue})", "SUCCESS")
        self.queue_selected.emit(queue, address)

    def _delete_fav(self, fav_id: str):
        config.remove_favorite(fav_id)
        self.refresh_favorites_chips()
        self.notify_toast.emit("✕ Видалено", "Адресу видалено зі списку обраних.", "INFO")
