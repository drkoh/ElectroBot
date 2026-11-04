import os
import json
from typing import Dict, Any, List

CONFIG_FILE = os.path.join(os.path.dirname(__file__), "..", "data", "app_settings.json")

DEFAULT_CONFIG: Dict[str, Any] = {
    "theme": "dark",
    "active_address": "Харків, вул. Дудинської",
    "active_queue": "2.1",
    "favorites": [], # No fake preset addresses!
    "notify_minutes_before": 15,
    "notify_enabled": True,
    "auto_refresh_minutes": 15,
    "minimize_to_tray_on_close": True,
    "start_minimized_to_tray": False,
    "router_ping_enabled": True,
    "router_ip": "",
    "desktop_widget_enabled": False,
    "window_width": 1050,
    "window_height": 720
}

class ConfigManager:
    def __init__(self, filepath: str = CONFIG_FILE):
        self.filepath = filepath
        self.data: Dict[str, Any] = dict(DEFAULT_CONFIG)
        self.load()

    def load(self):
        if os.path.exists(self.filepath):
            try:
                with open(self.filepath, "r", encoding="utf-8") as f:
                    saved = json.load(f)
                    self.data.update(saved)
            except Exception as e:
                print(f"Error loading config: {e}")
        else:
            self.save()

    def save(self):
        try:
            os.makedirs(os.path.dirname(self.filepath), exist_ok=True)
            with open(self.filepath, "w", encoding="utf-8") as f:
                json.dump(self.data, f, ensure_ascii=False, indent=2)
        except Exception as e:
            print(f"Error saving config: {e}")

    def get(self, key: str, default: Any = None) -> Any:
        return self.data.get(key, default)

    def set(self, key: str, value: Any):
        self.data[key] = value
        self.save()

    def get_favorites(self) -> List[Dict[str, Any]]:
        return self.data.get("favorites", [])

    def add_favorite(self, title: str, address: str, queue: str) -> str:
        favs = self.get_favorites()
        import time
        new_id = str(int(time.time() * 1000))
        new_fav = {
            "id": new_id,
            "title": title or "Моя адреса",
            "address": address,
            "queue": queue
        }
        favs.append(new_fav)
        self.data["favorites"] = favs
        self.save()
        return new_id

    def edit_favorite(self, fav_id: str, title: str, address: str, queue: str):
        favs = self.get_favorites()
        for f in favs:
            if f.get("id") == fav_id:
                f["title"] = title
                f["address"] = address
                f["queue"] = queue
                break
        self.data["favorites"] = favs
        self.save()

    def remove_favorite(self, fav_id: str):
        favs = [f for f in self.get_favorites() if f.get("id") != fav_id]
        self.data["favorites"] = favs
        self.save()

config = ConfigManager()
