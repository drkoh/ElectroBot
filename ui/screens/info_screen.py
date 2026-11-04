from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QFrame,
    QPushButton, QScrollArea
)
from PyQt6.QtGui import QDesktopServices
from PyQt6.QtCore import QUrl

class InfoScreen(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(20, 20, 20, 20)
        main_layout.setSpacing(16)
        
        lbl_title = QLabel("ℹ️ Офіційні джерела даних та контакти")
        lbl_title.setStyleSheet("font-size: 16px; font-weight: 700; color: #58a6ff;")
        main_layout.addWidget(lbl_title)
        
        # Card 1: Official Sources
        card_sources = QFrame()
        card_sources.setObjectName("CardFrame")
        s_layout = QVBoxLayout(card_sources)
        s_layout.setContentsMargins(18, 16, 18, 16)
        s_layout.setSpacing(12)
        
        lbl_s_head = QLabel("🌐 Використовувані першоджерела даних:")
        lbl_s_head.setStyleSheet("font-weight: 700; font-size: 14px;")
        s_layout.addWidget(lbl_s_head)
        
        items = [
            ("📢 Офіційний Telegram-канал АТ «Харківобленерго»",
             "Щоденні оперативні публікації погодинних графіків (ГПВ), аварійні зміни від НЕК «Укренерго».",
             "https://t.me/s/kharkivenergy", "Відкрити Telegram"),
            ("📄 Офіційний реєстр адрес та черг (2026–2027 рр.)",
             "Документ АТ «Харківобленерго» з розподілом споживачів по 12 підчергам на період 01.10.2026 - 01.04.2027 рр.",
             "https://oblenergo.kharkiv.ua/sites/default/files/pdf/%D0%90%D0%B4%D1%80%D0%B5%D1%81%D0%B8%20%D1%82%D0%B0%20%D1%87%D0%B5%D1%80%D0%B3%D0%B8%2026-27.pdf", "Завантажити PDF"),
            ("💻 Офіційний сайт АТ «Харківобленерго»",
             "Розділ перевірки відключень за адресою, особистий кабінет споживача, новини та аварійні роботи.",
             "https://www.oblenergo.kharkiv.ua/gaoinfo", "Перейти на сайт"),
            ("🤖 Telegram-бот АСДС 1562 м. Харкова (@Kharkiv1562Bot)",
             "Офіційний чат-бот міської диспетчерської служби 1562 щодо аварійних та планових відключень світла, опалення та ЖКГ.",
             "https://t.me/Kharkiv1562Bot", "Відкрити бота"),
            ("🏢 Портал АСДС «1562» (Оголошення та аварії)",
             "Реєстр усіх поточних аварійних робіт та знеструмлень за конкретними будинками Харкова.",
             "https://1562.kharkivrada.gov.ua/perelik-vsi/", "Відкрити 1562"),
        ]
        
        for name, desc, url, btn_text in items:
            row = QHBoxLayout()
            row.setSpacing(12)
            
            v = QVBoxLayout()
            v.setSpacing(2)
            lbl_name = QLabel(name)
            lbl_name.setStyleSheet("font-weight: 600; font-size: 13px; color: #58a6ff;")
            lbl_desc = QLabel(desc)
            lbl_desc.setStyleSheet("color: #9ba3b4; font-size: 12px;")
            lbl_desc.setWordWrap(True)
            v.addWidget(lbl_name)
            v.addWidget(lbl_desc)
            
            btn = QPushButton(btn_text)
            btn.setObjectName("SecondaryButton")
            btn.setFixedHeight(28)
            btn.clicked.connect(lambda ch, u=url: QDesktopServices.openUrl(QUrl(u)))
            
            row.addLayout(v)
            row.addStretch()
            row.addWidget(btn)
            s_layout.addLayout(row)
            
        main_layout.addWidget(card_sources)
        
        # Card 2: Emergency & Support
        card_contacts = QFrame()
        card_contacts.setObjectName("CardFrame")
        c_layout = QVBoxLayout(card_contacts)
        c_layout.setContentsMargins(18, 16, 18, 16)
        c_layout.setSpacing(10)
        
        lbl_c_head = QLabel("📞 Кол-центр АТ «Харківобленерго» (Цілодобово):")
        lbl_c_head.setStyleSheet("font-weight: 700; font-size: 14px;")
        c_layout.addWidget(lbl_c_head)
        
        lbl_phones = QLabel(
            "• 057-34-24-413,  067-23-40-413,  050-05-40-413,  063-05-40-413\n"
            "• Мобільний додаток: Kharkivoblenergo mobile\n"
            "• Офіційний чат-бот у Telegram: @KharkivOblEnergobot"
        )
        lbl_phones.setStyleSheet("font-size: 13px; line-height: 1.5;")
        c_layout.addWidget(lbl_phones)
        
        main_layout.addWidget(card_contacts)
        
        # Card 3: Notice
        card_notice = QFrame()
        card_notice.setObjectName("CardFrame")
        n_layout = QVBoxLayout(card_notice)
        n_layout.setContentsMargins(18, 14, 18, 14)
        n_layout.setSpacing(6)
        
        lbl_note_t = QLabel("⚠️ Зверніть увагу щодо застосування графіків:")
        lbl_note_t.setStyleSheet("font-weight: 700; font-size: 13px; color: #d29922;")
        n_layout.addWidget(lbl_note_t)
        
        lbl_note = QLabel(
            "Графіки погодинних відключень (ГПВ) застосовуються виключно за розпорядженням НЕК «Укренерго». "
            "Залежно від поточної ситуації в енергосистемі України, час відключень може коригуватися "
            "або відключення можуть застосовуватися не в повному обсязі."
        )
        lbl_note.setStyleSheet("color: #9ba3b4; font-size: 12px;")
        lbl_note.setWordWrap(True)
        n_layout.addWidget(lbl_note)
        
        main_layout.addWidget(card_notice)
        main_layout.addStretch()
