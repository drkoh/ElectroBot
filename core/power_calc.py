import os
import sys
from datetime import datetime, time
from typing import Optional, Tuple
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from core.models import PowerStatus, LiveStatus, DailySchedule, ScheduleInterval

def calculate_power_status(queue_name: str, schedule: DailySchedule, now_dt: Optional[datetime] = None) -> LiveStatus:
    if now_dt is None:
        now_dt = datetime.now()
        
    current_minutes = now_dt.hour * 60 + now_dt.minute
    time_str = now_dt.strftime("%H:%M")
    
    intervals = schedule.queues.get(queue_name, [])
    if not intervals:
        return LiveStatus(
            status=PowerStatus.ON,
            queue_name=queue_name,
            current_time_str=time_str,
            time_remaining_str="--:--",
            next_change_str="--:--",
            next_action_str="Немає даних",
            details=f"Для черги {queue_name} немає відключень або відсутній графік."
        )
        
    # Sort intervals by start_minute
    sorted_invs = sorted(intervals, key=lambda i: i.start_minute)
    
    # Check if current_minutes falls inside any outage interval
    active_outage = None
    for inv in sorted_invs:
        if inv.start_minute <= current_minutes < inv.end_minute:
            active_outage = inv
            break
            
    if active_outage:
        # Currently OFF
        mins_left = active_outage.end_minute - current_minutes
        hours = mins_left // 60
        mins = mins_left % 60
        rem_str = f"{hours} год {mins:02d} хв" if hours > 0 else f"{mins} хв"
        end_display = "24:00" if active_outage.end_minute == 1440 else f"{active_outage.end_minute // 60:02d}:{active_outage.end_minute % 60:02d}"
        
        return LiveStatus(
            status=PowerStatus.OFF,
            queue_name=queue_name,
            current_time_str=time_str,
            time_remaining_str=rem_str,
            next_change_str=end_display,
            next_action_str=f"Увімкнення о {end_display}",
            details=f"Зараз діє погодинне відключення (інтервал {active_outage.start_str} - {active_outage.end_str}). Залишилось: {rem_str}."
        )
    else:
        # Currently ON. Find next upcoming outage today
        next_outage = None
        for inv in sorted_invs:
            if inv.start_minute > current_minutes:
                next_outage = inv
                break
                
        if next_outage:
            mins_left = next_outage.start_minute - current_minutes
            hours = mins_left // 60
            mins = mins_left % 60
            rem_str = f"{hours} год {mins:02d} хв" if hours > 0 else f"{mins} хв"
            start_display = next_outage.start_str
            
            # If less than 20 mins, mark warning
            status = PowerStatus.SWITCHING if mins_left <= 20 else PowerStatus.ON
            
            return LiveStatus(
                status=status,
                queue_name=queue_name,
                current_time_str=time_str,
                time_remaining_str=rem_str,
                next_change_str=start_display,
                next_action_str=f"Вимкнення о {start_display}",
                details=f"Світло є! До наступного відключення за графіком ({start_display} - {next_outage.end_str}): {rem_str}."
            )
        else:
            # No more outages today!
            mins_until_midnight = 1440 - current_minutes
            hours = mins_until_midnight // 60
            mins = mins_until_midnight % 60
            rem_str = f"{hours} год {mins:02d} хв" if hours > 0 else f"{mins} хв"
            
            return LiveStatus(
                status=PowerStatus.ON,
                queue_name=queue_name,
                current_time_str=time_str,
                time_remaining_str=rem_str,
                next_change_str="24:00",
                next_action_str="До кінця доби без вимкнень",
                details="На сьогодні всі заплановані черги відключень позаду. До кінця доби світло є!"
            )

if __name__ == "__main__":
    from core.telegram_schedule import fetch_schedule_from_telegram
    sched = fetch_schedule_from_telegram()
    for q in ["1.1", "2.1", "3.1", "5.1"]:
        st = calculate_power_status(q, sched)
        print(f"Queue {q} -> Status: {st.status.value} | {st.next_action_str} | Time left: {st.time_remaining_str}")
