from dataclasses import dataclass, field
from typing import List, Optional, Tuple
from enum import Enum

class PowerStatus(str, Enum):
    ON = "ON"             # Світло є (за графіком)
    OFF = "OFF"           # Відключення за графіком (ГПВ)
    SWITCHING = "SWITCH"  # Ймовірне перемикання / під питанням

@dataclass
class AddressRecord:
    id: int
    raw_text: str
    queues: List[str] # e.g. ["1.1", "1.2"]
    settlement: str = "" # e.g. "Харків м."
    street: str = ""     # e.g. "Сумська вул."
    building: str = ""   # e.g. "10"
    page: int = 1

@dataclass
class ScheduleInterval:
    start_str: str # "06:00"
    end_str: str   # "09:00"
    start_minute: int
    end_minute: int

    @classmethod
    def from_strings(cls, s_start: str, s_end: str):
        sh, sm = map(int, s_start.split(':'))
        eh, em = map(int, s_end.split(':'))
        # 24:00 is minute 1440
        start_min = sh * 60 + sm
        end_min = (24 * 60) if (eh == 24 or (eh == 0 and em == 0 and start_min > 0)) else (eh * 60 + em)
        return cls(start_str=s_start, end_str=s_end, start_minute=start_min, end_minute=end_min)

@dataclass
class DailySchedule:
    title: str
    date_str: str
    fetched_at: str
    queues: dict[str, List[ScheduleInterval]] = field(default_factory=dict)
    raw_post_text: str = ""
    is_emergency: bool = False
    emergency_alert_text: str = ""

@dataclass
class LiveStatus:
    status: PowerStatus
    queue_name: str
    current_time_str: str
    time_remaining_str: str
    next_change_str: str
    next_action_str: str
    details: str
