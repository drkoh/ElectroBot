import os
import sys
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QFrame, QCheckBox
)
from PyQt6.QtGui import QColor, QFont, QDesktopServices
from PyQt6.QtCore import pyqtSignal, Qt, QUrl

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from core.service_1562 import fetch_1562_announcements, EmergencyOutage1562
from core.address_utils import extract_street_and_building, is_building_in_list
from core.config import config
from core.async_workers import run_async
from ui.context_menu_helper import attach_line_edit_context_menu, attach_table_context_menu

class Service1562Screen(QWidget):
    log_message = pyqtSignal(str, str)
    notify_toast = pyqtSignal(str, str, str)

    def __init__(self, parent=None):
        super().__init__(parent)
        
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(12)
        
        # 1. Header Card
        card_hdr = QFrame()
        card_hdr.setObjectName("CardFrame")
        h_layout = QVBoxLayout(card_hdr)
        h_layout.setContentsMargins(16, 14, 16, 14)
        h_layout.setSpacing(10)
        
        row1 = QHBoxLayout()
        lbl_title = QLabel("🚨 Оперативна диспетчерська служба АСДС «1562» м. Харкова")
        lbl_title.setStyleSheet("font-size: 15px; font-weight: 700; color: #da3633;")
        
        btn_tg_bot = QPushButton("🤖 Бот @Kharkiv1562Bot")
        btn_tg_bot.setObjectName("SecondaryButton")
        btn_tg_bot.setStyleSheet("color: #58a6ff; font-weight: bold;")
        btn_tg_bot.setToolTip("Відкрити офіційний бот АСДС 1562 у Telegram")
        btn_tg_bot.clicked.connect(lambda: QDesktopServices.openUrl(QUrl("https://t.me/Kharkiv1562Bot")))
        
        btn_site = QPushButton("🌐 Портал 1562")
        btn_site.setObjectName("SecondaryButton")
        btn_site.setToolTip("Відкрити офіційний реєстр переліку робіт на сайті 1562")
        btn_site.clicked.connect(lambda: QDesktopServices.openUrl(QUrl("https://1562.kharkivrada.gov.ua/perelik-vsi/")))
        
        row1.addWidget(lbl_title)
        row1.addStretch()
        row1.addWidget(btn_tg_bot)
        row1.addWidget(btn_site)
        h_layout.addLayout(row1)
        
        lbl_desc = QLabel("Тут публікуються реальні позапланові аварії та відключення послуг ЖКГ за конкретними будинками Харкова. Введіть вашу вулицю або номер будинку для миттєвої перевірки:")
        lbl_desc.setStyleSheet("font-size: 12px; color: #9ba3b4;")
        lbl_desc.setWordWrap(True)
        h_layout.addWidget(lbl_desc)
        
        # Search & Filter row
        row_filter = QHBoxLayout()
        row_filter.setSpacing(8)
        
        self.input_filter = QLineEdit()
        self.input_filter.setPlaceholderText("Пошук за адресою (наприклад: Дудинської, 9 або Полтавський Шлях)...")
        self.input_filter.textChanged.connect(self.filter_table)
        attach_line_edit_context_menu(self.input_filter, on_copied=lambda msg: self.notify_toast.emit("📋 Буфер", msg, "INFO"))
        
        self.btn_my_addr = QPushButton("📍 Моя адреса")
        self.btn_my_addr.setObjectName("SecondaryButton")
        self.btn_my_addr.setToolTip("Вставити вашу активну збережену адресу для миттєвої перевірки аварій")
        self.btn_my_addr.clicked.connect(self._on_insert_my_address)
        
        self.chk_elec_only = QCheckBox("Тільки електрика")
        self.chk_elec_only.setChecked(True)
        self.chk_elec_only.setToolTip("Показувати тільки аварії електромереж та знеструмлення")
        self.chk_elec_only.stateChanged.connect(self.filter_table)
        
        self.btn_refresh = QPushButton("🔄 Оновити 1562")
        self.btn_refresh.setObjectName("PrimaryButton")
        self.btn_refresh.setToolTip("Отримати свіжі аварійні зведення з диспетчерської 1562")
        self.btn_refresh.clicked.connect(self.reload_data)
        
        row_filter.addWidget(self.input_filter, stretch=3)
        row_filter.addWidget(self.btn_my_addr)
        row_filter.addWidget(self.chk_elec_only)
        row_filter.addWidget(self.btn_refresh)
        h_layout.addLayout(row_filter)
        
        layout.addWidget(card_hdr)
        
        # 2. Status Feedback Card (M3 Diagnostic Banner)
        self.card_feedback = QFrame()
        self.card_feedback.setObjectName("CardFrame")
        self.layout_feedback = QVBoxLayout(self.card_feedback)
        self.layout_feedback.setContentsMargins(14, 10, 14, 10)
        self.layout_feedback.setSpacing(4)
        
        self.lbl_feedback_title = QLabel("")
        self.lbl_feedback_title.setStyleSheet("font-size: 13px; font-weight: 700;")
        self.lbl_feedback_body = QLabel("")
        self.lbl_feedback_body.setStyleSheet("font-size: 12px;")
        self.lbl_feedback_body.setWordWrap(True)
        
        self.layout_feedback.addWidget(self.lbl_feedback_title)
        self.layout_feedback.addWidget(self.lbl_feedback_body)
        layout.addWidget(self.card_feedback)
        self.card_feedback.setVisible(False)
        
        # 3. Table of 1562 Announcements
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels([
            "Початок", "Заплановано до", "Вулиці та будинки знеструмлення", "Характер робіт / Причина", "Служба"
        ])
        self.table.setAlternatingRowColors(True)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.verticalHeader().setVisible(False)
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(4, QHeaderView.ResizeMode.ResizeToContents)
        attach_table_context_menu(self.table, on_copied=lambda msg: self.notify_toast.emit("📋 Буфер", msg, "SUCCESS"))
        layout.addWidget(self.table)
        
        self.all_announcements: list[EmergencyOutage1562] = []
        self.reload_data()

    def _on_insert_my_address(self):
        active_addr = config.get("active_address", "вул. Дудинської, 9")
        # Clean prefix
        clean = active_addr.replace("Харків, ", "").replace("Харків (", "").replace(")", "").strip()
        self.input_filter.setText(clean)
        self.notify_toast.emit("📍 Фільтр", f"Застосовано перевірку для: {clean}", "INFO")

    def reload_data(self):
        self.btn_refresh.setEnabled(False)
        self.btn_refresh.setText("⏳ Завантаження...")
        self.log_message.emit("Завантаження оперативних аварійних зведень АСДС 1562 у фоні...", "INFO")
        run_async(
            lambda: fetch_1562_announcements(force_refresh=True),
            on_result=self._on_data_loaded,
            on_error=self._on_data_error,
            on_finished=lambda: (self.btn_refresh.setEnabled(True), self.btn_refresh.setText("🔄 Оновити 1562"))
        )

    def _on_data_loaded(self, results):
        self.all_announcements = results or []
        self.log_message.emit(f"Отримано {len(self.all_announcements)} оголошень 1562 по місту Харків.", "SUCCESS")
        self.filter_table()

    def _on_data_error(self, err_msg):
        self.log_message.emit(f"Помилка завантаження 1562: {err_msg}", "ERROR")

    def _set_feedback_card(self, variant: str, title: str, body: str):
        if variant == 'danger':
            self.card_feedback.setObjectName("FeedbackBannerDanger")
            self.lbl_feedback_title.setObjectName("FeedbackTitleDanger")
        elif variant == 'success':
            self.card_feedback.setObjectName("FeedbackBannerSuccess")
            self.lbl_feedback_title.setObjectName("FeedbackTitleSuccess")
        else:
            self.card_feedback.setObjectName("FeedbackBannerInfo")
            self.lbl_feedback_title.setObjectName("FeedbackTitleInfo")
            
        self.lbl_feedback_body.setObjectName("FeedbackBody")
        self.lbl_feedback_title.setText(title)
        self.lbl_feedback_body.setText(body)
        
        for w in (self.card_feedback, self.lbl_feedback_title, self.lbl_feedback_body):
            w.style().unpolish(w)
            w.style().polish(w)
        self.card_feedback.setVisible(True)

    def filter_table(self):
        raw_query = self.input_filter.text().strip()
        elec_only = self.chk_elec_only.isChecked()
        
        # Base filter by electricity
        base_pool = []
        for ann in self.all_announcements:
            if elec_only:
                raw_l = ann.raw_announcement.lower()
                if not any(k in raw_l for k in ["електро", "струм", "обленерго", "джерел", "знеструм"]):
                    continue
            base_pool.append(ann)
            
        if not raw_query:
            # No query: show all announcements, hide feedback banner
            self.card_feedback.setVisible(False)
            self._render_rows(base_pool)
            return

        # Parse street and building
        street_clean, bld_clean = extract_street_and_building(raw_query)
        search_stem = (street_clean or raw_query).lower().replace("вул.", "").replace("вулиця", "").replace("просп.", "").replace("пр.", "").strip()
        if len(search_stem) > 5:
            search_stem = search_stem[:5]

        exact_matches = []
        street_matches = []
        all_affected_buildings_on_street = []

        for ann in base_pool:
            matched_street_in_ann = False
            bld_matched_in_ann = False
            
            for st, b_list in ann.affected_streets.items():
                st_clean = st.lower().replace("вул.", "").replace("вулиця", "").replace("просп.", "").replace("пр.", "").strip()
                if search_stem in st_clean or st_clean in search_stem:
                    matched_street_in_ann = True
                    all_affected_buildings_on_street.extend(b_list)
                    if bld_clean and is_building_in_list(bld_clean, b_list):
                        bld_matched_in_ann = True
                        
            if not matched_street_in_ann and search_stem in ann.raw_announcement.lower():
                matched_street_in_ann = True
                
            if bld_matched_in_ann:
                exact_matches.append(ann)
            elif matched_street_in_ann:
                street_matches.append(ann)

        # Remove duplicate building numbers preserving order
        unique_blds = []
        for b in all_affected_buildings_on_street:
            if b not in unique_blds:
                unique_blds.append(b)

        # Decide feedback banner and table display
        if bld_clean:
            if exact_matches:
                first = exact_matches[0]
                self._set_feedback_card(
                    variant='danger',
                    title=f"🚨 УВАГА! Будинок № {bld_clean} ({street_clean or raw_query}) — АВАРІЙНЕ ЗНЕСТРУМЛЕННЯ!",
                    body=(
                        f"⏰ Початок: {first.start_time}  |  Плановане відновлення: {first.planned_end_time}\n"
                        f"⚡ Причина: Відсутня електроенергія на джерелі ({first.workType}).\n"
                        f"🏢 Виконавець: {first.service}. Деталі наведено нижче:"
                    )
                )
                self._render_rows(exact_matches, highlight_bld=bld_clean)
            elif street_matches:
                blds_str = ", ".join(unique_blds) if unique_blds else "інші номери"
                self._set_feedback_card(
                    variant='success',
                    title=f"🟢 Будинок № {bld_clean} ({street_clean or raw_query}): АВАРІЙНИХ ЗНЕСТРУМЛЕНЬ 1562 НЕМАЄ",
                    body=(
                        f"💡 Зверніть увагу: на вашій вулиці є аварійні відключення 1562, але вони стосуються ТІЛЬКИ сусідніх будинків: {blds_str}.\n"
                        f"Електропостачання вашого будинку № {bld_clean} подається у штатному режимі (або за плановим графіком Обленерго ГПВ / ГАВ).\n"
                        f"Нижче наведено інформацію про аварії на вашій вулиці для довідки:"
                    )
                )
                self._render_rows(street_matches, unaffected_bld=bld_clean)
            else:
                self._set_feedback_card(
                    variant='info',
                    title=f"ℹ️ За адресою «{raw_query}» аварійних робіт у 1562 не зафіксовано",
                    body=(
                        "Міська диспетчерська 1562 не фіксує локальних аварій за цією адресою.\n"
                        "Якщо у вас зараз немає світла, це планове погодинне відключення Харківобленерго (ГПВ) вашої черги або загальні екстрені аварійні графіки (ГАВ) Укренерго через обстріли."
                    )
                )
                self._render_empty_placeholder(f"Аварійних знеструмлень 1562 за адресою «{raw_query}» немає.")
        else:
            # No building specified in query
            if street_matches:
                blds_str = ", ".join(unique_blds[:15]) + ("..." if len(unique_blds) > 15 else "")
                self._set_feedback_card(
                    variant='info',
                    title=f"⚠️ Знайдено аварійні відключення 1562 по вулиці {street_clean or raw_query}",
                    body=(
                        f"Аварії зафіксовано для будинків: {blds_str}.\n"
                        f"Введіть також номер вашого будинку (наприклад: «{street_clean or raw_query}, 9»), щоб система точно перевірила, чи торкнулося це саме вашої оселі."
                    )
                )
                self._render_rows(street_matches)
            else:
                self._set_feedback_card(
                    variant='info',
                    title=f"ℹ️ По вулиці «{raw_query}» аварійних робіт 1562 не зафіксовано",
                    body="Якщо на вулиці немає світла, це діють планові черги відключень Обленерго або загальноміські графіки ГАВ."
                )
                self._render_empty_placeholder(f"По вулиці «{raw_query}» аварійних робіт 1562 не знайдено.")

    def _render_empty_placeholder(self, message: str):
        self.table.setRowCount(1)
        item_msg = QTableWidgetItem(f"✅ {message}")
        item_msg.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
        item_msg.setForeground(QColor("#3fb950"))
        f = QFont()
        f.setBold(True)
        item_msg.setFont(f)
        self.table.setItem(0, 2, item_msg)
        for c in [0, 1, 3, 4]:
            self.table.setItem(0, c, QTableWidgetItem("-"))

    def _render_rows(self, announcements: list[EmergencyOutage1562], highlight_bld: str = "", unaffected_bld: str = ""):
        self.table.setRowCount(len(announcements))
        f_bold = QFont()
        f_bold.setBold(True)
        
        for idx, ann in enumerate(announcements):
            # 1. Start
            item_start = QTableWidgetItem(ann.start_time)
            item_start.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            
            # 2. Planned end
            item_end = QTableWidgetItem(ann.planned_end_time)
            item_end.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            item_end.setFont(f_bold)
            item_end.setForeground(QColor("#da3633"))
            
            # 3. Affected Streets
            st_lines = []
            for st, b_list in ann.affected_streets.items():
                st_lines.append(f"{st} (буд. {', '.join(b_list)})")
            streets_display = ";  ".join(st_lines) if st_lines else ann.raw_announcement[:120]
            
            if highlight_bld:
                streets_display = f"🚨 [ВАШ БУДИНОК {highlight_bld} У СПИСКУ!] " + streets_display
            elif unaffected_bld:
                streets_display = f"ℹ️ [Буд. {unaffected_bld} не торкнулося] " + streets_display
                
            item_streets = QTableWidgetItem(streets_display)
            if highlight_bld:
                item_streets.setFont(f_bold)
                item_streets.setForeground(QColor("#f85149"))
            
            # 4. Reason / Type
            reason = "Відсутня електроенергія на джерелі (Аварійне знеструмлення)" if "джерел" in ann.raw_announcement.lower() else ann.work_type
            item_reason = QTableWidgetItem(reason)
            
            # 5. Performer
            item_exec = QTableWidgetItem(ann.service)
            
            self.table.setItem(idx, 0, item_start)
            self.table.setItem(idx, 1, item_end)
            self.table.setItem(idx, 2, item_streets)
            self.table.setItem(idx, 3, item_reason)
            self.table.setItem(idx, 4, item_exec)
