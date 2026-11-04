import os
import sys
from datetime import datetime, timedelta
from typing import Optional, List
from dataclasses import dataclass

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from core.models import PowerStatus, LiveStatus, DailySchedule
from core.service_1562 import EmergencyOutage1562, check_address_in_1562
from core.power_calc import calculate_power_status
from core.address_utils import extract_street_and_building

@dataclass
class UnifiedStatus:
    is_outage: bool
    state_type: str  # 'EMERGENCY_1562', 'EMERGENCY_GAV', 'SCHEDULED_OFF', 'SCHEDULED_ON', 'NO_DATA'
    badge_text: str
    badge_theme: str  # 'danger', 'warning', 'success', 'neutral'
    headline: str
    details: str
    subtext: str
    target_time_str: str
    target_seconds_left: int
    countdown_hms: str
    charge_window_str: str
    is_emergency: bool
    outage_1562: Optional[EmergencyOutage1562] = None
    scheduled_status: Optional[LiveStatus] = None

def format_seconds_to_hms(total_seconds: int) -> str:
    if total_seconds <= 0:
        return "00:00:00"
    h = total_seconds // 3600
    m = (total_seconds % 3600) // 60
    s = total_seconds % 60
    return f"{h:02d}:{m:02d}:{s:02d}"

def calculate_charge_window(queue_name: str, schedule: DailySchedule, now_dt: datetime) -> str:
    """Calculates continuous available power time before the next scheduled outage."""
    current_minutes = now_dt.hour * 60 + now_dt.minute
    intervals = schedule.queues.get(queue_name, [])
    if not intervals:
        return "⚡ Необмежено (графік відсутній)"
        
    sorted_invs = sorted(intervals, key=lambda i: i.start_minute)
    
    # Check if currently in an outage
    for inv in sorted_invs:
        if inv.start_minute <= current_minutes < inv.end_minute:
            return "❌ Зараз світла немає — зарядка неможлива"
            
    # Find next outage
    next_outage = None
    for inv in sorted_invs:
        if inv.start_minute > current_minutes:
            next_outage = inv
            break
            
    if next_outage:
        mins_avail = next_outage.start_minute - current_minutes
        h = mins_avail // 60
        m = mins_avail % 60
        if h > 0:
            return f"⚡ Вікно зарядки: ~{h} год {m:02d} хв (до {next_outage.start_str})"
        else:
            return f"⚠️ Вікно зарядки закінчується: ~{m} хв (до {next_outage.start_str})"
    else:
        mins_to_midnight = 1440 - current_minutes
        h = mins_to_midnight // 60
        return f"⚡ Вікно зарядки: до кінця доби (~{h} год)"

def resolve_unified_status(
    address_query: str,
    queue_name: str,
    schedule: Optional[DailySchedule],
    now_dt: Optional[datetime] = None
) -> UnifiedStatus:
    """
    Unified State Resolver prioritizing real operational facts:
    1. Municipal Emergency Outage (1562) matching street + house number
    2. Regional Emergency Outage (ГАВ / СГАВ) declared in Telegram
    3. Hourly Scheduled Outage (ГПВ)
    """
    if now_dt is None:
        now_dt = datetime.now()
        
    street, building = extract_street_and_building(address_query)
    
    # Step 1: Check 1562 Emergency Outages
    active_1562_outages = []
    if street:
        try:
            active_1562_outages = check_address_in_1562(street, building)
        except Exception:
            active_1562_outages = []
            
    if active_1562_outages:
        # User's specific address is in an emergency outage
        out_1562 = active_1562_outages[0]
        planned_end = out_1562.planned_end_time
        
        # Calculate countdown to planned restoration
        target_seconds = 0
        target_display = planned_end
        if planned_end:
            try:
                # Format: "04.11.26 20:00" or "04.11.2026 20:00"
                parts = planned_end.split()
                if len(parts) >= 2:
                    t_parts = parts[1].split(':')
                    d_parts = parts[0].split('.')
                    
                    year = int(d_parts[2])
                    if year < 100:
                        year += 2000
                    month = int(d_parts[1])
                    day = int(d_parts[0])
                    hour = int(t_parts[0])
                    minute = int(t_parts[1])
                    
                    target_dt = datetime(year, month, day, hour, minute)
                    diff = (target_dt - now_dt).total_seconds()
                    target_seconds = max(0, int(diff))
                    target_display = target_dt.strftime("%H:%M")
            except Exception:
                target_seconds = 0

        hms = format_seconds_to_hms(target_seconds)
        bld_str = f" {building}" if building else ""
        
        return UnifiedStatus(
            is_outage=True,
            state_type="EMERGENCY_1562",
            badge_text="🚨 АВАРІЯ 1562",
            badge_theme="danger",
            headline=f"Аварійне знеструмлення за адресою",
            details=f"Заявка диспетчерської АСДС 1562: {out_1562.work_type}",
            subtext=f"Вулиця: {street}{bld_str} | Служба: {out_1562.service}",
            target_time_str=target_display,
            target_seconds_left=target_seconds,
            countdown_hms=hms,
            charge_window_str="❌ Зараз тривають аварійні ремонтні роботи",
            is_emergency=True,
            outage_1562=out_1562
        )
        
    # Step 2: Check Regional Emergency (ГАВ) from Telegram schedule announcement
    if schedule and schedule.is_emergency:
        alert_msg = schedule.emergency_alert_text or "У Харківській області введено екстрені відключення (ГАВ)"
        return UnifiedStatus(
            is_outage=True,
            state_type="EMERGENCY_GAV",
            badge_text="🚨 ЕКСТРЕНІ ВІДКЛЮЧЕННЯ (ГАВ)",
            badge_theme="danger",
            headline="Діють аварійні відключення (ГАВ)",
            details=alert_msg,
            subtext="У разі застосування ГАВ стандартні погодинні графіки тимчасово призупинено.",
            target_time_str="До розпорядження",
            target_seconds_left=0,
            countdown_hms="--:--:--",
            charge_window_str="⚠️ Режим ГАВ: подача живлення нестабільна",
            is_emergency=True
        )
        
    # Step 3: Scheduled Outage (ГПВ)
    if schedule and queue_name:
        sched_status = calculate_power_status(queue_name, schedule, now_dt)
        charge_window = calculate_charge_window(queue_name, schedule, now_dt)
        
        # Calculate seconds to next change
        current_sec = now_dt.hour * 3600 + now_dt.minute * 60 + now_dt.second
        target_sec = 0
        if sched_status.next_change_str and ":" in sched_status.next_change_str:
            try:
                np = sched_status.next_change_str.split(":")
                target_min = int(np[0]) * 60 + int(np[1])
                target_total_sec = target_min * 60
                if target_total_sec >= current_sec:
                    target_sec = target_total_sec - current_sec
            except Exception:
                target_sec = 0
                
        hms = format_seconds_to_hms(target_sec)
        
        if sched_status.status == PowerStatus.OFF:
            return UnifiedStatus(
                is_outage=True,
                state_type="SCHEDULED_OFF",
                badge_text="🔴 ВІДКЛЮЧЕННЯ ЗА ГРАФІКОМ",
                badge_theme="danger",
                headline=f"Знеструмлено за графіком (Черга {queue_name})",
                details=sched_status.details,
                subtext=sched_status.next_action_str,
                target_time_str=sched_status.next_change_str,
                target_seconds_left=target_sec,
                countdown_hms=hms,
                charge_window_str=charge_window,
                is_emergency=False,
                scheduled_status=sched_status
            )
        else:
            return UnifiedStatus(
                is_outage=False,
                state_type="SCHEDULED_ON",
                badge_text="🟢 СВІТЛО Є ЗА ГРАФІКОМ",
                badge_theme="success",
                headline=f"Електроенергія подається (Черга {queue_name})",
                details=sched_status.details,
                subtext=sched_status.next_action_str,
                target_time_str=sched_status.next_change_str,
                target_seconds_left=target_sec,
                countdown_hms=hms,
                charge_window_str=charge_window,
                is_emergency=False,
                scheduled_status=sched_status
            )
            
    # Step 4: No data fallback
    return UnifiedStatus(
        is_outage=False,
        state_type="NO_DATA",
        badge_text="ℹ️ НЕМАЄ ДАНИХ",
        badge_theme="neutral",
        headline="Оберіть адресу або чергу",
        details="Введіть вашу вулицю та номер будинку або оберіть номер черги для відображення графіка.",
        subtext="",
        target_time_str="--:--",
        target_seconds_left=0,
        countdown_hms="--:--:--",
        charge_window_str="",
        is_emergency=False
    )
