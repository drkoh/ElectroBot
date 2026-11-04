# ui/styles.py
# Google Material Design 3 (M3) & Modern Slate Design Tokens

DARK_THEME = {
    # M3 Tonal Elevation Levels
    "canvas": "#1c1e24",                    # Level 0
    "surface_lowest": "#16181d",            # Level 1
    "surface_low": "#1f222b",               # Level 2 (Sidebar/Header)
    "card": "#262933",                      # Level 3 (Container)
    "card_border": "#353946",
    "surface_high": "#2d313d",              # Level 4
    "input_bg": "#1f222b",                  # Level 5
    "input_border": "#383d4a",
    "input_focus": "#388bfd",
    
    # Text Tokens
    "text_primary": "#e6edf3",
    "text_secondary": "#9ba3b4",
    "text_muted": "#687182",
    
    # Accents
    "accent_primary": "#1f6feb",
    "accent_hover": "#388bfd",
    "accent_active": "#58a6ff",
    "tonal_bg": "#223249",
    "tonal_text": "#58a6ff",
    "tonal_border": "#2c4161",
    
    # Status Tones
    "success": "#238636",
    "success_bg": "#1c3d28",
    "success_border": "#235835",
    "success_text": "#3fb950",
    
    "danger": "#da3633",
    "danger_bg": "#3d1e22",
    "danger_border": "#5c2a30",
    "danger_text": "#f85149",
    
    "warning": "#d29922",
    "warning_bg": "#3d321a",
    "warning_border": "#5c4b24",
    "warning_text": "#d29922",
    
    # Tables
    "table_row_even": "#21242d",
    "table_row_odd": "#262934",
    "table_select_bg": "#2b4366",
    "table_select_text": "#ffffff",
    "table_header_bg": "#262934",
    "table_header_text": "#9da7b7",
    "table_grid": "#2c303c",
    
    # Badges & Misc
    "badge_bg": "#223249",
    "badge_text": "#58a6ff",
    "sidebar_hover": "#2a2e3a",
    "sidebar_active": "#293448",
    "log_bg": "#191b20",
    "scrollbar_handle": "#3d4251",
    "scrollbar_hover": "#4e5567"
}

LIGHT_THEME = {
    # M3 Tonal Elevation Levels (Office / Google M3 Light)
    "canvas": "#f8f9fa",                    # Soft light canvas
    "surface_lowest": "#ffffff",
    "surface_low": "#f0f4f9",               # Light Header/Sidebar
    "card": "#ffffff",                      # White Card container
    "card_border": "#e5e7eb",
    "surface_high": "#e9eef6",
    "input_bg": "#ffffff",
    "input_border": "#cbd5e1",
    "input_focus": "#0f6cbd",
    
    # Text Tokens (High contrast WCAG AAA)
    "text_primary": "#0f172a",              # Crisp dark graphite (never white!)
    "text_secondary": "#475569",
    "text_muted": "#64748b",
    
    # Accents
    "accent_primary": "#0f6cbd",
    "accent_hover": "#115ea3",
    "accent_active": "#0d4d87",
    "tonal_bg": "#e0edff",
    "tonal_text": "#0f6cbd",
    "tonal_border": "#b9d9ff",
    
    # Status Tones
    "success": "#107c41",
    "success_bg": "#dcfce7",
    "success_border": "#86efac",
    "success_text": "#15803d",
    
    "danger": "#d13438",
    "danger_bg": "#fee2e2",
    "danger_border": "#fca5a5",
    "danger_text": "#991b1b",
    
    "warning": "#d97706",
    "warning_bg": "#fef3c7",
    "warning_border": "#fde68a",
    "warning_text": "#b45309",
    
    # Tables
    "table_row_even": "#ffffff",
    "table_row_odd": "#f8fafc",
    "table_select_bg": "#e0edff",
    "table_select_text": "#0f6cbd",
    "table_header_bg": "#f1f5f9",
    "table_header_text": "#475569",
    "table_grid": "#e2e8f0",
    
    # Badges & Misc
    "badge_bg": "#e0edff",
    "badge_text": "#0f6cbd",
    "sidebar_hover": "#f1f5f9",
    "sidebar_active": "#e0edff",
    "log_bg": "#f8fafc",
    "scrollbar_handle": "#cbd5e1",
    "scrollbar_hover": "#94a3b8"
}

def generate_stylesheet(theme_mode: str = "dark") -> str:
    t = DARK_THEME if theme_mode == "dark" else LIGHT_THEME
    
    return f"""
    /* Base Window and Widgets */
    QMainWindow, QDialog {{
        background-color: {t["canvas"]};
        color: {t["text_primary"]};
        font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
        font-size: 13px;
    }}

    QWidget {{
        color: {t["text_primary"]};
        font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
    }}

    /* Header Bar */
    QFrame#HeaderBar {{
        background-color: {t["surface_low"]};
        border-bottom: 1px solid {t["card_border"]};
    }}

    /* Sidebar Navigation */
    QFrame#Sidebar {{
        background-color: {t["surface_low"]};
        border-right: 1px solid {t["card_border"]};
    }}
    QPushButton#NavButton {{
        background-color: transparent;
        color: {t["text_secondary"]};
        border: none;
        border-radius: 8px;
        text-align: left;
        padding-left: 14px;
        height: 38px;
        font-size: 13px;
        font-weight: 500;
    }}
    QPushButton#NavButton:hover {{
        background-color: {t["sidebar_hover"]};
        color: {t["text_primary"]};
    }}
    QPushButton#NavButton:checked {{
        background-color: {t["sidebar_active"]};
        color: {t["tonal_text"]};
        font-weight: 700;
        border-left: 3px solid {t["accent_primary"]};
    }}

    /* Card Frames (M3 Surface Containers) */
    QFrame#CardFrame {{
        background-color: {t["card"]};
        border: 1px solid {t["card_border"]};
        border-radius: 12px;
    }}
    QFrame#CardFrameDanger {{
        background-color: {t["card"]};
        border: 1.5px solid {t["danger"]};
        border-radius: 12px;
    }}
    QFrame#CardFrameWarning {{
        background-color: {t["card"]};
        border: 1.5px solid {t["warning"]};
        border-radius: 12px;
    }}

    /* Buttons (M3 4-Level Hierarchy) */
    /* 1. Filled Button (Primary CTA) */
    QPushButton#PrimaryButton {{
        background-color: {t["accent_primary"]};
        color: #ffffff;
        border: none;
        border-radius: 8px;
        padding: 6px 16px;
        font-size: 13px;
        font-weight: 600;
        min-height: 24px;
    }}
    QPushButton#PrimaryButton:hover {{
        background-color: {t["accent_hover"]};
    }}
    QPushButton#PrimaryButton:pressed {{
        background-color: {t["accent_active"]};
    }}
    QPushButton#PrimaryButton:disabled {{
        background-color: {t["card_border"]};
        color: {t["text_muted"]};
    }}

    /* 2. Filled Tonal Button */
    QPushButton#TonalButton {{
        background-color: {t["tonal_bg"]};
        color: {t["tonal_text"]};
        border: 1px solid {t["tonal_border"]};
        border-radius: 8px;
        padding: 6px 14px;
        font-size: 12px;
        font-weight: 600;
        min-height: 24px;
    }}
    QPushButton#TonalButton:hover {{
        background-color: {t["accent_primary"]};
        color: #ffffff;
    }}

    /* 3. Outlined / Secondary Button */
    QPushButton#SecondaryButton {{
        background-color: {t["card"]};
        color: {t["text_primary"]};
        border: 1px solid {t["input_border"]};
        border-radius: 8px;
        padding: 6px 12px;
        font-size: 12px;
        font-weight: 500;
        min-height: 24px;
    }}
    QPushButton#SecondaryButton:hover {{
        background-color: {t["sidebar_hover"]};
        border-color: {t["accent_primary"]};
        color: {t["accent_primary"]};
    }}
    QPushButton#SecondaryButton:pressed {{
        background-color: {t["sidebar_active"]};
    }}

    /* Inputs & Selects */
    QLineEdit, QComboBox, QSpinBox {{
        background-color: {t["input_bg"]};
        color: {t["text_primary"]};
        border: 1.5px solid {t["input_border"]};
        border-radius: 8px;
        padding: 6px 10px;
        font-size: 13px;
        selection-background-color: {t["accent_primary"]};
        selection-color: #ffffff;
    }}
    QLineEdit:focus, QComboBox:focus, QSpinBox:focus {{
        border: 1.5px solid {t["input_focus"]};
    }}

    QComboBox::drop-down {{
        subcontrol-origin: padding;
        subcontrol-position: top right;
        width: 24px;
        border-left: none;
    }}
    QComboBox QAbstractItemView {{
        background-color: {t["card"]};
        color: {t["text_primary"]};
        border: 1px solid {t["card_border"]};
        selection-background-color: {t["table_select_bg"]};
        selection-color: {t["table_select_text"]};
        border-radius: 8px;
        padding: 4px;
    }}

    /* Tables */
    QTableWidget, QTableView {{
        background-color: {t["table_row_even"]};
        alternate-background-color: {t["table_row_odd"]};
        selection-background-color: {t["table_select_bg"]};
        selection-color: {t["table_select_text"]};
        border: 1px solid {t["card_border"]};
        border-radius: 8px;
        color: {t["text_primary"]};
        gridline-color: {t["table_grid"]};
        font-size: 12px;
    }}
    QHeaderView::section {{
        background-color: {t["table_header_bg"]};
        color: {t["table_header_text"]};
        font-weight: 700;
        border: none;
        border-bottom: 1px solid {t["card_border"]};
        padding: 6px;
    }}

    /* Status Pills & Badges (M3 16px Pill) */
    QLabel#BadgePill {{
        background-color: {t["tonal_bg"]};
        color: {t["tonal_text"]};
        border-radius: 12px;
        padding: 2px 10px;
        font-size: 11px;
        font-weight: 700;
    }}

    QLabel#StatusOnBadge {{
        background-color: {t["success_bg"]};
        color: {t["success_text"]};
        border: 1px solid {t["success_border"]};
        border-radius: 14px;
        padding: 4px 12px;
        font-size: 12px;
        font-weight: 700;
    }}

    QLabel#StatusOffBadge {{
        background-color: {t["danger_bg"]};
        color: {t["danger_text"]};
        border: 1px solid {t["danger_border"]};
        border-radius: 14px;
        padding: 4px 12px;
        font-size: 12px;
        font-weight: 700;
    }}

    QLabel#StatusWarningBadge {{
        background-color: {t["warning_bg"]};
        color: {t["warning_text"]};
        border: 1px solid {t["warning_border"]};
        border-radius: 14px;
        padding: 4px 12px;
        font-size: 12px;
        font-weight: 700;
    }}

    /* Emergency & Feedback Banners */
    QFrame#EmergencyBannerFrame, QFrame#FeedbackBannerDanger {{
        background-color: {t["danger_bg"]};
        border: 1.5px solid {t["danger_border"]};
        border-radius: 10px;
        padding: 10px;
    }}
    QFrame#FeedbackBannerSuccess {{
        background-color: {t["success_bg"]};
        border: 1.5px solid {t["success_border"]};
        border-radius: 10px;
        padding: 10px;
    }}
    QFrame#FeedbackBannerInfo {{
        background-color: {t["surface_high"]};
        border: 1.5px solid {t["accent_primary"]};
        border-radius: 10px;
        padding: 10px;
    }}
    QLabel#EmergencyBannerTitle, QLabel#FeedbackTitleDanger {{
        color: {t["danger_text"]};
        font-weight: 800;
        font-size: 13px;
    }}
    QLabel#FeedbackTitleSuccess {{
        color: {t["success_text"]};
        font-weight: 800;
        font-size: 13px;
    }}
    QLabel#FeedbackTitleInfo {{
        color: {t["accent_primary"]};
        font-weight: 800;
        font-size: 13px;
    }}
    QLabel#EmergencyBannerDesc, QLabel#FeedbackBody {{
        color: {t["text_primary"]};
        font-size: 12px;
        font-weight: 500;
    }}

    /* Log Drawer */
    QFrame#LogDrawer {{
        background-color: {t["surface_low"]};
        border-top: 1px solid {t["card_border"]};
    }}
    QTextEdit#LogConsole, QTextEdit#RawMessageText {{
        background-color: {t["log_bg"]};
        color: {t["text_primary"]};
        border: 1px solid {t["card_border"]};
        border-radius: 8px;
        font-family: 'Segoe UI', 'Consolas', monospace;
        font-size: 12px;
        padding: 6px;
    }}

    /* Scroll Areas & Scrollbars */
    QScrollArea {{
        background-color: transparent;
        border: none;
    }}
    QScrollArea > QWidget {{
        background-color: transparent;
    }}
    QScrollArea > QWidget > QWidget {{
        background-color: transparent;
    }}
    QWidget#ScrollContentContainer {{
        background-color: transparent;
    }}

    QScrollBar:vertical {{
        background: transparent;
        width: 8px;
        margin: 0;
    }}
    QScrollBar::handle:vertical {{
        background: {t["scrollbar_handle"]};
        min-height: 25px;
        border-radius: 4px;
    }}
    QScrollBar::handle:vertical:hover {{
        background: {t["scrollbar_hover"]};
    }}
    QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
        height: 0px;
    }}

    /* Desktop Mini-Widget */
    QWidget#MiniWidgetContainer {{
        background-color: {t["card"]};
        border: 1px solid {t["card_border"]};
        border-radius: 10px;
    }}
    QLabel#MiniQueuePill {{
        background-color: {t["badge_bg"]};
        color: {t["badge_text"]};
        border-radius: 6px;
        padding: 2px 8px;
        font-weight: 700;
        font-size: 12px;
    }}
    QLabel#MiniCountdownText {{
        color: {t["text_primary"]};
        font-weight: 600;
        font-size: 13px;
        font-family: 'Segoe UI', monospace;
    }}
    QPushButton#MiniIconButton {{
        background-color: transparent;
        color: {t["text_secondary"]};
        border: none;
        border-radius: 4px;
        font-size: 12px;
    }}
    QPushButton#MiniIconButton:hover {{
        background-color: {t["sidebar_hover"]};
        color: {t["text_primary"]};
    }}
    """
