import os
import sys
import json
import re
from datetime import datetime, timezone
import requests

# Ensure project root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from core.models import DailySchedule, ScheduleInterval

CACHE_FILE = os.path.join(os.path.dirname(__file__), "..", "data", "latest_schedule.json")

# Default fallback if offline on first run
FALLBACK_RAW = """
1.1 06:00-09:00; 12:30-19:30; 23:00-24:00
1.2 06:00-09:00; 12:30-19:30; 22:00-24:00
2.1 06:00-09:00; 12:30-19:30; 22:00-24:00
2.2 02:00-09:00; 12:30-19:30; 22:00-23:00
3.1 00:00-02:00; 08:00-12:30; 16:00-23:00
3.2 00:00-02:00; 08:00-12:30; 16:00-23:00
4.1 00:00-02:00; 08:00-12:30; 16:00-23:00
4.2 00:00-02:00; 08:00-12:30; 16:00-23:00
5.1 02:00-06:00; 09:00-16:00; 19:30-22:00; 23:00-24:00
5.2 02:00-08:00; 09:00-16:00; 19:30-22:00; 23:00-24:00
6.1 02:00-08:00; 09:00-16:00; 19:30-22:00; 23:00-24:00
6.2 00:00-08:00; 09:00-16:00; 19:30-22:00; 23:00-24:00
"""

def parse_schedule_text(text: str, title: str = "", date_str: str = "", fetched_at: str = "") -> DailySchedule:
    queues: dict[str, list[ScheduleInterval]] = {}
    
    for line in text.splitlines():
        line = line.strip()
        m = re.match(r'^([1-6]\.[1-2])\s+(.+)$', line)
        if m:
            q_name = m.group(1)
            times_str = m.group(2)
            intervals = []
            for part in re.split(r'[;,]+', times_str):
                part = part.strip()
                pm = re.match(r'(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})', part)
                if pm:
                    intervals.append(ScheduleInterval.from_strings(pm.group(1), pm.group(2)))
            if intervals:
                queues[q_name] = intervals
                
    return DailySchedule(
        title=title or "Графік погодинних відключень (ГПВ)",
        date_str=date_str or datetime.now().strftime("%d.%m.%Y"),
        fetched_at=fetched_at or datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        queues=queues,
        raw_post_text=text.strip()
    )

def fetch_schedule_from_telegram() -> DailySchedule:
    """Fetches real-time schedule and emergency status from official t.me/s/kharkivenergy channel."""
    url = "https://t.me/s/kharkivenergy"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
    
    try:
        r = requests.get(url, headers=headers, timeout=8)
        if r.status_code == 200:
            html = r.text
            messages = re.findall(r'<div class="tgme_widget_message_wrap[^"]*"[\s\S]*?</div>\s*</div>\s*</div>', html)
            
            queue_pattern = re.compile(r'\b[1-6]\.[1-2]\b')
            
            # Check for recent emergency outage announcements in the last 6 messages
            is_emergency = False
            emergency_text = ""
            for msg in reversed(messages[-6:]):
                clean_m = re.sub(r'<br\s*/?>', '\n', msg)
                clean_m = re.sub(r'<[^>]+>', '', clean_m)
                clean_m = clean_m.replace("&nbsp;", " ").replace("&quot;", '"')
                clean_m = clean_m.strip()
                if any(k in clean_m.lower() for k in ["аварійні відключення", "гав", "сгав", "масштабної атаки", "масштабна атака"]):
                    is_emergency = True
                    lines = [l.strip() for l in clean_m.split('\n') if l.strip() and not l.startswith("Харківобленерго") and not l.startswith("Forwarded")]
                    emergency_text = " ".join(lines[:2])
                    break
            
            for msg in reversed(messages):
                clean_text = re.sub(r'<br\s*/?>', '\n', msg)
                clean_text = re.sub(r'<[^>]+>', '', clean_text).strip()
                
                matches = queue_pattern.findall(clean_text)
                if len(matches) >= 6:
                    time_match = re.search(r'<time[^>]*datetime="([^"]+)"', msg)
                    post_iso = time_match.group(1) if time_match else datetime.now().isoformat()
                    
                    # Try to extract human date from text (e.g. "у середу, 4 листопада")
                    date_match = re.search(r'(у\s+\w+,\s+\d{1,2}\s+[а-яіїєґ]+)', clean_text, re.IGNORECASE)
                    date_display = date_match.group(1) if date_match else datetime.now().strftime("%d.%m.%Y")
                    
                    title = "Графік Харківобленерго (ГПВ)"
                    schedule = parse_schedule_text(clean_text, title=title, date_str=date_display, fetched_at=post_iso)
                    schedule.is_emergency = is_emergency
                    schedule.emergency_alert_text = emergency_text
                    
                    if len(schedule.queues) >= 6:
                        save_cache(schedule)
                        return schedule
    except Exception as e:
        print(f"Failed to fetch Telegram: {e}")
        
    # If network failed or no posts matched, try cache
    cached = load_cache()
    if cached:
        return cached
        
    # Ultimate fallback
    return parse_schedule_text(FALLBACK_RAW, title="Базовий графік ГПВ (Офлайн)")

def save_cache(schedule: DailySchedule):
    try:
        os.makedirs(os.path.dirname(CACHE_FILE), exist_ok=True)
        data = {
            "title": schedule.title,
            "date_str": schedule.date_str,
            "fetched_at": schedule.fetched_at,
            "raw_post_text": schedule.raw_post_text,
            "is_emergency": schedule.is_emergency,
            "emergency_alert_text": schedule.emergency_alert_text,
            "queues": {
                q: [[inv.start_str, inv.end_str] for inv in inv_list]
                for q, inv_list in schedule.queues.items()
            }
        }
        with open(CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"Error saving schedule cache: {e}")

def load_cache() -> DailySchedule | None:
    if os.path.exists(CACHE_FILE):
        try:
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
            queues = {}
            for q, pairs in data.get("queues", {}).items():
                queues[q] = [ScheduleInterval.from_strings(p[0], p[1]) for p in pairs]
            return DailySchedule(
                title=data.get("title", ""),
                date_str=data.get("date_str", ""),
                fetched_at=data.get("fetched_at", ""),
                queues=queues,
                raw_post_text=data.get("raw_post_text", ""),
                is_emergency=data.get("is_emergency", False),
                emergency_alert_text=data.get("emergency_alert_text", "")
            )
        except Exception as e:
            print(f"Error loading cache: {e}")
    return None

if __name__ == "__main__":
    sched = fetch_schedule_from_telegram()
    print("Schedule Loaded:")
    print("  Title:", sched.title)
    print("  Date:", sched.date_str)
    print("  Fetched at:", sched.fetched_at)
    print("  Total subqueues:", len(sched.queues))
    for q in sorted(sched.queues.keys()):
        invs = ", ".join([f"{i.start_str}-{i.end_str}" for i in sched.queues[q]])
        print(f"    {q}: {invs}")
