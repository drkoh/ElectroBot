import os
import sys
import asyncio
import math
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Any

from nicegui import ui, app

# Ensure core modules are available
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))
from core.config import config
from core.telegram_schedule import fetch_schedule_from_telegram, load_cache
from core.status_resolver import resolve_unified_status
from core.service_1562 import fetch_1562_announcements, EmergencyOutage1562
from core.address_utils import extract_street_and_building
from core.queue_detector import detect_queue_for_address
from core.ping_detector import ping_host, detect_default_gateway

def check_router_online(ip: str = "") -> Optional[bool]:
    target_ip = ip.strip() if ip else detect_default_gateway()
    if not target_ip:
        return None
    res = ping_host(target_ip)
    return res.is_reachable

# ---------------------------------------------------------------------------
# Apple Human Interface Guidelines (HIG) Stylesheet
# ---------------------------------------------------------------------------
APPLE_CSS = """
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');

:root {
    --apple-font: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Inter", "Helvetica Neue", Arial, sans-serif;
    --apple-bg: #090A0D;
    --apple-card-bg: rgba(28, 28, 32, 0.78);
    --apple-card-inset: rgba(44, 44, 48, 0.55);
    --apple-card-border: rgba(255, 255, 255, 0.09);
    --apple-text-primary: #FFFFFF;
    --apple-text-secondary: #8E8E93;
    --apple-blue: #0A84FF;
    --apple-orange: #FF9F0A;
    --apple-green: #30D158;
    --apple-red: #FF453A;
    --apple-purple: #BF5AF2;
}

body {
    background: var(--apple-bg);
    background-image: radial-gradient(circle at 50% 0%, #151821 0%, #090A0D 75%);
    font-family: var(--apple-font);
    color: var(--apple-text-primary);
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    margin: 0;
    padding: 0;
    min-height: 100vh;
}

/* Apple Frosted Glass Card */
.apple-card {
    background: var(--apple-card-bg);
    backdrop-filter: blur(28px) saturate(190%);
    -webkit-backdrop-filter: blur(28px) saturate(190%);
    border: 1px solid var(--apple-card-border);
    border-radius: 22px;
    box-shadow: 0 12px 36px rgba(0, 0, 0, 0.45);
    transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.apple-card:hover {
    border-color: rgba(255, 255, 255, 0.16);
}

/* Inset Group Card */
.apple-inset {
    background: var(--apple-card-inset);
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-radius: 16px;
    padding: 14px 18px;
}

/* Cupertino Segmented Control */
.cupertino-segmented {
    background: rgba(118, 118, 128, 0.22);
    backdrop-filter: blur(20px);
    border-radius: 14px;
    padding: 3px;
    border: 1px solid rgba(255, 255, 255, 0.06);
    display: inline-flex;
    gap: 3px;
}

.cupertino-segment-btn {
    border-radius: 11px;
    padding: 8px 18px;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
    color: var(--apple-text-secondary);
    border: none;
    background: transparent;
}

.cupertino-segment-btn.active {
    background: rgba(255, 255, 255, 0.18);
    color: #FFFFFF;
    box-shadow: 0 3px 10px rgba(0, 0, 0, 0.3);
}

.cupertino-segment-btn:hover:not(.active) {
    color: #FFFFFF;
    background: rgba(255, 255, 255, 0.06);
}

/* Apple Pills and Badges */
.apple-pill-blue {
    background: rgba(10, 132, 255, 0.16);
    color: #58A6FF;
    border: 1px solid rgba(10, 132, 255, 0.3);
    border-radius: 9999px;
    padding: 3px 10px;
    font-size: 12px;
    font-weight: 600;
}

.apple-pill-orange {
    background: rgba(255, 159, 10, 0.16);
    color: #FFB74D;
    border: 1px solid rgba(255, 159, 10, 0.35);
    border-radius: 9999px;
    padding: 3px 10px;
    font-size: 12px;
    font-weight: 600;
}

.apple-pill-green {
    background: rgba(48, 209, 88, 0.16);
    color: #4ADE80;
    border: 1px solid rgba(48, 209, 88, 0.3);
    border-radius: 9999px;
    padding: 3px 10px;
    font-size: 12px;
    font-weight: 600;
}

.apple-pill-red {
    background: rgba(255, 69, 58, 0.16);
    color: #FF7B72;
    border: 1px solid rgba(255, 69, 58, 0.35);
    border-radius: 9999px;
    padding: 3px 10px;
    font-size: 12px;
    font-weight: 600;
}

/* SF Monospaced Tabular Numbers */
.sf-mono {
    font-family: "SF Mono", "Menlo", monospace;
    font-variant-numeric: tabular-nums;
    letter-spacing: -0.02em;
}

/* Pulsating Live Indicator */
@keyframes pulse-live {
    0% { transform: scale(0.95); opacity: 0.7; }
    50% { transform: scale(1.08); opacity: 1; }
    100% { transform: scale(0.95); opacity: 0.7; }
}

.live-pulse {
    animation: pulse-live 2s infinite ease-in-out;
}

/* Scrollbars */
::-webkit-scrollbar {
    width: 6px;
    height: 6px;
}
::-webkit-scrollbar-track {
    background: transparent;
}
::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.18);
    border-radius: 9999px;
}
::-webkit-scrollbar-thumb:hover {
    background: rgba(255, 255, 255, 0.3);
}
"""

# ---------------------------------------------------------------------------
# State Management
# ---------------------------------------------------------------------------
class AppState:
    def __init__(self):
        self.active_address = config.get("active_address", "вул. Дудинської, 9")
        self.active_queue = config.get("active_queue", "2.2")
        self.schedule = load_cache()
        self.outages_1562: List[EmergencyOutage1562] = []
        self.is_loading = False
        self.last_sync_time = datetime.now().strftime("%H:%M")
        self.active_tab = "home"
        self.router_online = None
        self.router_ip = config.get("router_ip", "")
        self.favorites = config.get_favorites()
        
        # Power station calculator state
        self.battery_model = "EcoFlow River 2 Max (512 Wh)"
        self.custom_wh = 512
        self.load_router = True      # 15W
        self.load_laptop = True      # 65W
        self.load_starlink = False   # 45W
        self.load_led = True         # 20W
        self.load_fridge = False     # 120W
        self.load_tv = False         # 80W
        self.extra_watts = 0

state = AppState()

# ---------------------------------------------------------------------------
# SVG Timeline Renderer (The Hero Apple Widget Component)
# ---------------------------------------------------------------------------
def render_apple_timeline_svg(schedule, queue_name: str, now_dt: datetime) -> str:
    width = 920
    height = 126
    
    box_top = 8
    box_bottom = 66
    baseline_y = 80
    hour_tick_bottom = 96
    half_tick_bottom = 89
    quarter_tick_bottom = 85
    text_y = 112
    
    now_minutes = now_dt.hour * 60 + now_dt.minute + (now_dt.second / 60.0)
    window_start = now_minutes - 45.0
    window_duration = 390.0 # 6.5 hours
    window_end = window_start + window_duration
    
    def to_x(m):
        return ((m - window_start) / window_duration) * width

    svg = []
    svg.append(f'<svg viewBox="0 0 {width} {height}" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg" style="display:block; overflow:visible;">')
    
    svg.append('''
    <defs>
        <linearGradient id="amberGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#FF9F0A" stop-opacity="0.32"/>
            <stop offset="100%" stop-color="#FF9F0A" stop-opacity="0.10"/>
        </linearGradient>
        <filter id="laserGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="3.5" result="blur"/>
            <feComposite in="SourceGraphic" in2="blur" operator="over"/>
        </filter>
    </defs>
    ''')
    
    # 1. Outage Intervals
    clean_q = queue_name.replace("Черга", "").strip()
    raw_invs = []
    if schedule and hasattr(schedule, 'queues'):
        raw_invs = schedule.queues.get(clean_q, [])
        
    all_invs = []
    for inv in raw_invs:
        all_invs.append((inv.start_minute, inv.end_minute))
        all_invs.append((inv.start_minute + 1440, inv.end_minute + 1440))
        all_invs.append((inv.start_minute - 1440, inv.end_minute - 1440))
        
    for s_m, e_m in all_invs:
        if e_m <= window_start or s_m >= window_end:
            continue
        x1 = to_x(s_m)
        x2 = to_x(e_m)
        left = max(0.0, x1)
        right = min(float(width), x2)
        box_w = right - left
        if box_w >= 6:
            r = 10
            dur_h = (e_m - s_m) / 60.0
            dur_str = f"💡 {dur_h:.1f}"
            svg.append(f'<rect x="{left:.1f}" y="{box_top}" width="{box_w:.1f}" height="{box_bottom - box_top}" rx="{r}" fill="url(#amberGrad)" stroke="#FF9F0A" stroke-width="2"/>')
            
            cx = (left + right) / 2.0
            cy = (box_top + box_bottom) / 2.0 + 4
            if box_w >= 45:
                svg.append(f'<text x="{cx:.1f}" y="{cy:.1f}" fill="#FFFFFF" font-family="-apple-system, sans-serif" font-size="12" font-weight="700" text-anchor="middle">{dur_str}</text>')
                
    # 2. Baseline
    svg.append(f'<line x1="0" y1="{baseline_y}" x2="{width}" y2="{baseline_y}" stroke="#4A4D5C" stroke-width="1.5"/>')
    
    # 3. Ticks and Labels
    start_tick = int(math.floor(window_start / 15.0) * 15)
    end_tick = int(math.ceil(window_end / 15.0) * 15)
    
    for tm in range(start_tick, end_tick + 1, 15):
        tx = to_x(tm)
        if tx < -5 or tx > width + 5:
            continue
        min_of_h = (tm % 60 + 60) % 60
        if min_of_h == 0:
            h_val = ((tm // 60) % 24 + 24) % 24
            lbl = f"{h_val:02d}:00"
            svg.append(f'<line x1="{tx:.1f}" y1="{baseline_y}" x2="{tx:.1f}" y2="{hour_tick_bottom}" stroke="#8E92A4" stroke-width="2"/>')
            svg.append(f'<text x="{tx:.1f}" y="{text_y}" fill="#8E92A4" font-family="-apple-system, sans-serif" font-size="11" font-weight="500" text-anchor="middle">{lbl}</text>')
        elif min_of_h == 30:
            svg.append(f'<line x1="{tx:.1f}" y1="{baseline_y}" x2="{tx:.1f}" y2="{half_tick_bottom}" stroke="#65697B" stroke-width="1.5"/>')
        else:
            svg.append(f'<line x1="{tx:.1f}" y1="{baseline_y}" x2="{tx:.1f}" y2="{quarter_tick_bottom}" stroke="#444755" stroke-width="1"/>')
            
    # 4. Red Needle Cursor at Current Time
    now_x = to_x(now_minutes)
    if 0 <= now_x <= width:
        svg.append(f'<line x1="{now_x:.1f}" y1="{box_top}" x2="{now_x:.1f}" y2="{baseline_y}" stroke="#FF453A" stroke-width="3" filter="url(#laserGlow)"/>')
        p1 = f"{now_x:.1f},{baseline_y - 2}"
        p2 = f"{now_x - 5:.1f},{baseline_y + 8}"
        p3 = f"{now_x + 5:.1f},{baseline_y + 8}"
        svg.append(f'<polygon points="{p1} {p2} {p3}" fill="#FF453A"/>')
        
    svg.append('</svg>')
    return ''.join(svg)

# ---------------------------------------------------------------------------
# Background Data Loader
# ---------------------------------------------------------------------------
async def refresh_all_data():
    state.is_loading = True
    try:
        # 1. Fetch Telegram schedule in background
        loop = asyncio.get_event_loop()
        state.schedule = await loop.run_in_executor(None, fetch_schedule_from_telegram)
        
        # 2. Fetch 1562 announcements in background
        state.outages_1562 = await loop.run_in_executor(None, lambda: fetch_1562_announcements(force_refresh=True))
        
        # 3. Router Ping
        state.router_online = await loop.run_in_executor(None, lambda: check_router_online(state.router_ip))
        
        state.last_sync_time = datetime.now().strftime("%H:%M")
    except Exception as e:
        print(f"Error refreshing data: {e}")
    finally:
        state.is_loading = False

# ---------------------------------------------------------------------------
# Main Application Page
# ---------------------------------------------------------------------------
@ui.page('/')
def main_page():
    ui.add_head_html(f"<style>{APPLE_CSS}</style>")
    ui.page_title("ElectroBot — Моніторинг світла Харків")

    # Dynamic UI Refresh Container
    content_area = ui.column().classes('w-full max-w-5xl mx-auto px-4 py-6 gap-6')

    def render_content():
        content_area.clear()
        with content_area:
            # 1. Top Apple Header & Navigation Bar
            render_apple_header()

            # 2. Tab Views
            if state.active_tab == "home":
                render_home_tab()
            elif state.active_tab == "matrix":
                render_matrix_tab()
            elif state.active_tab == "search":
                render_search_tab()
            elif state.active_tab == "1562":
                render_1562_tab()
            elif state.active_tab == "calc":
                render_calculator_tab()

    # -----------------------------------------------------------------------
    # Component: Apple Navigation Bar
    # -----------------------------------------------------------------------
    def render_apple_header():
        with ui.row().classes('w-full justify-between items-center mb-2 flex-wrap gap-4'):
            # Brand & Status Pill
            with ui.row().classes('items-center gap-3'):
                ui.label("⚡ ElectroBot").classes('text-2xl font-bold tracking-tight text-white')
                ui.html('<span class="apple-pill-blue">v1.4 • Apple Edition</span>')
                
                # Active Address Pill
                with ui.row().classes('items-center gap-1 cursor-pointer apple-pill-orange').on('click', lambda: set_tab("search")):
                    ui.label(f"📍 {state.active_address}")

            # Cupertino Segmented Tabs
            with ui.row().classes('cupertino-segmented'):
                tabs = [
                    ("home", "⚡ Головна"),
                    ("matrix", "📅 Усі черги"),
                    ("search", "🔍 Пошук"),
                    ("1562", "🚨 АСДС 1562"),
                    ("calc", "🔋 Станція")
                ]
                for tid, tname in tabs:
                    is_active = (state.active_tab == tid)
                    btn_cls = f"cupertino-segment-btn {'active' if is_active else ''}"
                    ui.button(tname, on_click=lambda t=tid: set_tab(t)).classes(btn_cls)

            # Right System Actions
            with ui.row().classes('items-center gap-3'):
                # Live clock
                clock_lbl = ui.label(datetime.now().strftime("%H:%M:%S")).classes('sf-mono text-sm font-semibold text-gray-400')
                
                # Active Queue Selector Dropdown
                queues_list = ["1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"]
                q_select = ui.select(
                    options=queues_list,
                    value=state.active_queue,
                    on_change=lambda e: on_queue_changed(e.value)
                ).classes('w-24 text-sm').props('dense rounded outlined dark')

                # Refresh Button
                ui.button("🔄 Оновити", on_click=trigger_manual_refresh).classes('apple-inset text-xs font-semibold text-white px-3 py-2 cursor-pointer hover:bg-white/10')

    def set_tab(tab_name: str):
        state.active_tab = tab_name
        render_content()

    def on_queue_changed(new_q: str):
        state.active_queue = new_q
        config.set("active_queue", new_q)
        render_content()

    async def trigger_manual_refresh():
        ui.notify("Оновлення даних з офіційних каналів...", type="info")
        await refresh_all_data()
        render_content()
        ui.notify("Дані успішно оновлено!", type="positive")

    # -----------------------------------------------------------------------
    # Tab 1: ⚡ Головна (The Apple Outage Card & Battery Companion)
    # -----------------------------------------------------------------------
    def render_home_tab():
        now_dt = datetime.now()
        status = resolve_unified_status(
            address_query=state.active_address,
            queue_name=state.active_queue,
            schedule=state.schedule,
            now_dt=now_dt
        )

        with ui.column().classes('w-full gap-5'):
            # HERO CARD: Apple Dark Timeline Widget (Exactly as on screenshot!)
            with ui.column().classes('apple-card p-6 w-full'):
                # Card Top Row: Left Info & Right Status
                with ui.row().classes('w-full justify-between items-start'):
                    # Top-Left: Queue & Status time
                    with ui.column().classes('gap-1'):
                        with ui.row().classes('items-center gap-2'):
                            ui.label(f"#{state.active_queue}").classes('text-3xl font-extrabold text-white tracking-tight')
                            ui.label("💡" if status.is_outage else "⚡").classes('text-2xl')
                        
                        time_now_str = now_dt.strftime("%H:%M")
                        ui.label(f"Станом на {time_now_str}").classes('text-sm font-semibold text-[#FF9F0A]')

                    # Top-Right: Outage Status & Time Remaining
                    with ui.column().classes('items-end gap-1 text-right'):
                        status_title = "Аварія" if status.is_emergency else ("Вимкнено" if status.is_outage else "Увімкнено")
                        ui.label(status_title).classes('text-2xl font-bold text-white')
                        
                        if status.is_outage:
                            timer_txt = f"Відновиться о ~{status.target_time_str}"
                        else:
                            timer_txt = f"Вимкнеться о ~{status.target_time_str}" if status.target_time_str != "24:00" else "Світло є без обмежень"
                            
                        ui.label(timer_txt).classes('text-sm font-semibold text-[#FF9F0A]')

                # Large Monospaced Countdown Display
                with ui.row().classes('w-full justify-center my-2'):
                    with ui.column().classes('items-center'):
                        ui.label(status.countdown_hms).classes('sf-mono text-5xl font-extrabold text-white tracking-widest')
                        ui.label(status.details).classes('text-xs text-gray-400 mt-1')

                # Embedded Live SVG Timeline Ruler
                with ui.column().classes('w-full mt-3'):
                    svg_html = render_apple_timeline_svg(state.schedule, state.active_queue, now_dt)
                    ui.html(svg_html).classes('w-full')

            # SECOND ROW: Smart Charge Window & Hardware Connectivity
            with ui.row().classes('w-full gap-5 grid grid-cols-1 md:grid-cols-2'):
                # Smart Charge Window (EcoFlow / Bluetti)
                with ui.column().classes('apple-card p-5 justify-between'):
                    with ui.row().classes('items-center justify-between w-full'):
                        ui.label("⚡ Розумне вікно живлення").classes('text-base font-bold text-white')
                        ui.html('<span class="apple-pill-blue">EcoFlow • Bluetti</span>')
                    
                    ui.label(status.charge_window_str).classes('text-sm font-semibold text-[#58A6FF] my-2')
                    ui.label(status.charge_window_advice).classes('text-xs text-gray-300 leading-relaxed')
                    
                    with ui.row().classes('w-full justify-between items-center mt-3 pt-3 border-t border-white/10'):
                        ui.label("Режим станції:").classes('text-xs text-gray-400')
                        ui.label("Турбо-зарядка (~1.5 год)" if "Коротке" in status.charge_window_str else "Стандарт (~2.5 год)").classes('text-xs font-semibold text-white')

                # Hardware Router Ping Card
                with ui.column().classes('apple-card p-5 justify-between'):
                    with ui.row().classes('items-center justify-between w-full'):
                        ui.label("📶 Домашня мережа & Роутер").classes('text-base font-bold text-white')
                        if state.router_online is True:
                            ui.html('<span class="apple-pill-green">🟢 Онлайн (Світло є)</span>')
                        elif state.router_online is False:
                            ui.html('<span class="apple-pill-red">🔴 Знеструмлено (Немає пінг)</span>')
                        else:
                            ui.html('<span class="apple-pill-orange">⚪ Опитування...</span>')

                    ui.label("Фізична перевірка наявності струму в оселі через пінгування вашого Wi-Fi роутера або шлюзу.").classes('text-xs text-gray-300 my-2')
                    
                    with ui.row().classes('w-full justify-between items-center mt-3 pt-3 border-t border-white/10'):
                        ui.label(f"IP роутера: {state.router_ip or 'Авто-детекція'}").classes('text-xs text-gray-400 sf-mono')
                        ui.button("Перевірити пінг", on_click=trigger_ping_check).classes('apple-inset text-xs font-semibold text-white px-3 py-1 cursor-pointer hover:bg-white/10')

    async def trigger_ping_check():
        ui.notify("Пінгування домашнього шлюзу...", type="info")
        loop = asyncio.get_event_loop()
        state.router_online = await loop.run_in_executor(None, lambda: check_router_online(state.router_ip))
        render_content()
        status_txt = "Світло є вдома (Роутер онлайн)!" if state.router_online else "Роутер недоступний (Оселя знеструмлена)"
        ui.notify(status_txt, type="positive" if state.router_online else "warning")

    # -----------------------------------------------------------------------
    # Tab 2: 📅 Повний графік відключень (12 черг)
    # -----------------------------------------------------------------------
    def render_matrix_tab():
        with ui.column().classes('w-full gap-4'):
            with ui.row().classes('justify-between items-center w-full'):
                with ui.column().classes('gap-1'):
                    ui.label("📅 Погодинний розклад Харківобленерго").classes('text-xl font-bold text-white')
                    ui.label(state.schedule.date_str if state.schedule else "Завантаження розкладу...").classes('text-xs text-[#FF9F0A]')
                
                ui.button("🔄 Оновити розклад", on_click=trigger_manual_refresh).classes('apple-inset text-xs font-semibold text-white px-3 py-2 cursor-pointer')

            # 12-Queue Interactive Inset Table
            with ui.column().classes('apple-card p-5 w-full overflow-x-auto'):
                with ui.row().classes('w-full justify-between text-xs font-bold text-gray-400 border-b border-white/10 pb-2 mb-2'):
                    ui.label("ЧЕРГА").classes('w-20')
                    ui.label("ІНТЕРВАЛИ ВІДКЛЮЧЕНЬ СЬОГОДНІ").classes('flex-1')
                    ui.label("ДІЯ").classes('w-24 text-right')

                if state.schedule and state.schedule.queues:
                    for q_name, intervals in sorted(state.schedule.queues.items()):
                        is_current = (q_name == state.active_queue)
                        row_bg = 'rgba(10, 132, 255, 0.12)' if is_current else 'rgba(255, 255, 255, 0.02)'
                        border_cls = 'border-l-4 border-[#0A84FF]' if is_current else 'border-l-4 border-transparent'
                        
                        with ui.row().classes(f'w-full items-center justify-between p-3 rounded-xl mb-1 {border_cls}').style(f'background: {row_bg};'):
                            # Queue Name
                            with ui.row().classes('w-20 items-center gap-1'):
                                ui.label(f"#{q_name}").classes('font-bold text-sm text-white')
                                if is_current:
                                    ui.label("📍").classes('text-xs')

                            # Intervals Pills
                            with ui.row().classes('flex-1 gap-2 flex-wrap'):
                                for inv in intervals:
                                    dur_h = (inv.end_minute - inv.start_minute) / 60.0
                                    ui.html(f'<span class="apple-pill-orange">{inv.start_str} - {inv.end_str} ({dur_h:.1f}г)</span>')

                            # Select Button
                            with ui.row().classes('w-24 justify-end'):
                                if is_current:
                                    ui.label("Активна").classes('text-xs font-semibold text-[#0A84FF]')
                                else:
                                    ui.button("Обрати", on_click=lambda q=q_name: on_queue_changed(q)).classes('apple-inset text-xs font-semibold px-2 py-1 text-gray-300 hover:text-white cursor-pointer')

    # -----------------------------------------------------------------------
    # Tab 3: 🔍 Пошук адреси та автодетекція черги
    # -----------------------------------------------------------------------
    def render_search_tab():
        with ui.column().classes('w-full gap-5'):
            ui.label("🔍 Пошук адреси та визначення черги").classes('text-xl font-bold text-white')

            with ui.column().classes('apple-card p-6 w-full gap-4'):
                ui.label("Введіть вулицю та номер будинку у Харкові:").classes('text-sm text-gray-300')
                
                search_input = ui.input(
                    placeholder="Наприклад: вул. Дудинської, 9 або Полтавський Шлях, 148...",
                    value=state.active_address
                ).classes('w-full text-base').props('rounded outlined dark clearable')

                with ui.row().classes('gap-2 items-center flex-wrap'):
                    ui.label("Швидкі адреси:").classes('text-xs text-gray-400')
                    sample_addrs = [
                        "вул. Дудинської, 9",
                        "вул. Полтавський Шлях, 148",
                        "вул. Сумська, 40",
                        "просп. Науки, 14",
                        "вул. Шевченка, 24"
                    ]
                    for s_addr in sample_addrs:
                        ui.button(s_addr, on_click=lambda a=s_addr: apply_address(a)).classes('apple-inset text-xs text-gray-300 hover:text-white px-2 py-1 cursor-pointer')

                # Result Card
                result_box = ui.column().classes('w-full apple-inset p-4 gap-2 mt-2')
                
                def update_address_preview(val):
                    result_box.clear()
                    with result_box:
                        if not val:
                            ui.label("Введіть адресу для визначення черги.").classes('text-sm text-gray-400')
                            return
                            
                        detected_q = detect_queue_for_address(val)
                        with ui.row().classes('justify-between items-center w-full'):
                            with ui.column().classes('gap-1'):
                                ui.label(f"📍 {val}").classes('text-base font-bold text-white')
                                ui.html(f'<span class="apple-pill-blue text-sm font-bold">Визначено: Черга #{detected_q}</span>')
                            
                            ui.button("Встановити як мою адресу", on_click=lambda: apply_address(val)).classes('apple-inset bg-[#0A84FF] text-white font-bold text-xs px-4 py-2 cursor-pointer')

                update_address_preview(search_input.value)
                search_input.on('change', lambda e: update_address_preview(e.value))

    def apply_address(new_addr: str):
        state.active_address = new_addr
        config.set("active_address", new_addr)
        detected_q = detect_queue_for_address(new_addr)
        state.active_queue = detected_q
        config.set("active_queue", detected_q)
        ui.notify(f"Збережено: {new_addr} (Черга #{detected_q})", type="positive")
        set_tab("home")

    # -----------------------------------------------------------------------
    # Tab 4: 🚨 АСДС «1562» (Live Dispatcher Announcements)
    # -----------------------------------------------------------------------
    def render_1562_tab():
        with ui.column().classes('w-full gap-5'):
            with ui.row().classes('justify-between items-center w-full'):
                with ui.column().classes('gap-1'):
                    ui.label("🚨 Оперативна диспетчерська АСДС 1562").classes('text-xl font-bold text-white')
                    ui.label(f"Оновлено: {state.last_sync_time} • В базі: {len(state.outages_1562)} активних робіт").classes('text-xs text-[#FF9F0A]')
                
                ui.button("🔄 Оновити 1562", on_click=trigger_manual_refresh).classes('apple-inset text-xs font-semibold text-white px-3 py-2 cursor-pointer')

            # Diagnostic Status Banner for Active Address
            st_clean, bld_clean = extract_street_and_building(state.active_address)
            matched_1562 = [o for o in state.outages_1562 if o.matches_address(st_clean, bld_clean)] if st_clean else []

            with ui.column().classes('apple-card p-5 w-full'):
                if matched_1562:
                    first = matched_1562[0]
                    with ui.row().classes('items-center gap-2 mb-2'):
                        ui.html('<span class="apple-pill-red text-sm font-bold">🚨 АВАРІЙНЕ ЗНЕСТРУМЛЕННЯ ЗА ВАШИМ БУДИНКОМ</span>')
                    ui.label(f"Адреса: {state.active_address}").classes('text-sm font-bold text-white')
                    ui.label(f"Причина: {first.work_type} ({first.service})").classes('text-xs text-gray-300')
                    ui.label(f"Початок: {first.start_time} | Орієнтовне завершення: {first.planned_end_time}").classes('text-xs font-semibold text-[#FF9F0A]')
                else:
                    with ui.row().classes('items-center gap-2'):
                        ui.html('<span class="apple-pill-green text-sm font-bold">🟢 Аварій 1562 за вашою адресою не зафіксовано</span>')
                    ui.label(f"За адресою «{state.active_address}» локальних робіт немає. Подача електроенергії регулюється плановим графіком Обленерго (Черга #{state.active_queue}).").classes('text-xs text-gray-300 mt-1')

            # Real Announcements List
            ui.label(f"Активні аварійні заявки по Харкову ({len(state.outages_1562)}):").classes('text-sm font-bold text-gray-300 mt-2')
            
            with ui.column().classes('w-full gap-3'):
                for out in state.outages_1562[:25]:
                    with ui.column().classes('apple-card p-4 w-full gap-2'):
                        with ui.row().classes('justify-between items-center w-full'):
                            ui.label(out.service).classes('font-bold text-sm text-white')
                            ui.html(f'<span class="apple-pill-orange text-xs">{out.start_time} → {out.planned_end_time}</span>')
                        
                        ui.label(out.work_type).classes('text-xs text-gray-200 font-medium')
                        
                        if out.affected_streets:
                            streets_preview = ", ".join(f"{st} ({len(blds)} буд.)" for st, blds in list(out.affected_streets.items())[:3])
                            ui.label(f"📍 Вулиці: {streets_preview}").classes('text-xs text-gray-400')

    # -----------------------------------------------------------------------
    # Tab 5: 🔋 Калькулятор акумулятора / зарядної станції
    # -----------------------------------------------------------------------
    def render_calculator_tab():
        with ui.column().classes('w-full gap-5'):
            ui.label("🔋 Калькулятор автономності EcoFlow / Bluetti").classes('text-xl font-bold text-white')

            with ui.column().classes('apple-card p-6 w-full gap-6'):
                # Station Selector
                with ui.column().classes('w-full gap-2'):
                    ui.label("Оберіть вашу зарядну станцію:").classes('text-sm font-semibold text-gray-300')
                    stations = {
                        "EcoFlow River 2 (256 Wh)": 256,
                        "EcoFlow River 2 Max (512 Wh)": 512,
                        "EcoFlow Delta 2 (1024 Wh)": 1024,
                        "Bluetti EB3A (268 Wh)": 268,
                        "Bluetti AC180 (1152 Wh)": 1152,
                        "Bluetti Elite 30 V2 (288 Wh)": 288,
                        "Власна станція": state.custom_wh
                    }
                    station_select = ui.select(
                        options=list(stations.keys()),
                        value=state.battery_model
                    ).classes('w-full text-base').props('rounded outlined dark')

                # Calculate Total Load
                def get_total_watts():
                    total = 0
                    if state.load_router: total += 15
                    if state.load_laptop: total += 65
                    if state.load_starlink: total += 45
                    if state.load_led: total += 20
                    if state.load_fridge: total += 120
                    if state.load_tv: total += 80
                    total += state.extra_watts
                    return max(total, 5)

                # Appliances Load Toggles
                ui.label("Підключені прилади:").classes('text-sm font-semibold text-gray-300 mt-2')
                
                with ui.row().classes('w-full grid grid-cols-2 md:grid-cols-3 gap-3'):
                    def toggle_btn(name, watts, attr_name):
                        is_on = getattr(state, attr_name)
                        pill_cls = 'apple-pill-blue' if is_on else 'apple-inset text-gray-400'
                        btn = ui.button(
                            f"{name} ({watts} Вт)",
                            on_click=lambda: (setattr(state, attr_name, not is_on), render_content())
                        ).classes(f'{pill_cls} text-xs font-semibold py-2 cursor-pointer')

                    toggle_btn("Wi-Fi Роутер", 15, "load_router")
                    toggle_btn("Ноутбук", 65, "load_laptop")
                    toggle_btn("Starlink", 45, "load_starlink")
                    toggle_btn("LED Освітлення", 20, "load_led")
                    toggle_btn("Холодильник", 120, "load_fridge")
                    toggle_btn("Телевізор", 80, "load_tv")

                # Extra Load Slider
                with ui.column().classes('w-full gap-1 mt-2'):
                    ui.label(f"Додаткове навантаження: {state.extra_watts} Вт").classes('text-xs text-gray-400')
                    ui.slider(min=0, max=500, value=state.extra_watts, on_change=lambda e: (setattr(state, 'extra_watts', int(e.value)), render_content())).props('dark')

                # Calculation Result Hero Box
                tot_w = get_total_watts()
                capacity_wh = stations.get(station_select.value, state.custom_wh)
                effective_wh = capacity_wh * 0.88 # 88% inverter efficiency
                hours_left = effective_wh / tot_w
                h = int(hours_left)
                m = int((hours_left - h) * 60)

                with ui.column().classes('apple-inset p-5 w-full items-center justify-center gap-2 mt-4').style('background: rgba(10, 132, 255, 0.1); border-color: rgba(10, 132, 255, 0.3);'):
                    ui.label("ОЧІКУВАНИЙ ЧАС АВТОНОМНОЇ РОБОТИ").classes('text-xs font-bold text-[#58A6FF] tracking-wider')
                    ui.label(f"{h} год {m:02d} хв").classes('sf-mono text-4xl font-extrabold text-white')
                    ui.label(f"Сумарне споживання: {tot_w} Вт  |  Ємність акумулятора: {capacity_wh} Вт⋅год").classes('text-xs text-gray-300')

    # Initial Render
    render_content()

    # Trigger initial background network refresh shortly after page displays
    ui.timer(0.5, refresh_all_data, once=True)

    # Periodic Auto-refresh Timer: every 5 seconds for clock & countdown
    ui.timer(5.0, render_content)

    # Periodic Background Data Fetch: every 120 seconds
    ui.timer(120.0, refresh_all_data)

# ---------------------------------------------------------------------------
# Entry Point
# ---------------------------------------------------------------------------
if __name__ in {"__main__", "__mp_main__"}:
    print("Starting ElectroBot Apple NiceGUI Web Application on http://localhost:8080 ...")
    ui.run(
        title="ElectroBot",
        favicon="⚡",
        port=8080,
        dark=True,
        reload=False
    )
