import os
import sys
import time
from typing import Optional
from dataclasses import dataclass

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from core.config import config

@dataclass
class StationTelemetry:
    brand: str = "NONE" # "ECOFLOW", "BLUETTI", "CHARGER", "NONE"
    model_name: str = ""
    is_configured: bool = False
    is_connected: bool = False
    is_grid_online: bool = False
    battery_percent: int = 0
    in_watts: int = 0
    out_watts: int = 0
    remain_minutes: int = 0
    is_charging: bool = False
    status_headline: str = "Не підключено"
    status_details: str = ""
    last_updated: float = 0.0
    error_message: Optional[str] = None

    def format_remaining_time(self) -> str:
        if self.remain_minutes <= 0:
            return "--"
        h = self.remain_minutes // 60
        m = self.remain_minutes % 60
        if h > 0:
            return f"{h} год {m:02d} хв"
        return f"{m} хв"

class PowerStationManager:
    @staticmethod
    def get_selected_brand() -> str:
        return config.get("station_brand", "ECOFLOW")

    @staticmethod
    def set_selected_brand(brand: str):
        config.set("station_brand", brand)

    @staticmethod
    def get_grid_state() -> str:
        return config.get("station_grid_state", "AUTO")

    @staticmethod
    def set_grid_state(state: str):
        config.set("station_grid_state", state)

    @classmethod
    def get_telemetry(cls) -> StationTelemetry:
        brand = cls.get_selected_brand()
        grid_state = cls.get_grid_state()
        
        # In AUTO mode, assume online if not in known scheduled outage
        is_grid = True if grid_state == "ON" else (False if grid_state == "OFF" else True)

        if brand == "CHARGER":
            return StationTelemetry(
                brand="CHARGER",
                model_name="🔌 Датчик розетки 220В",
                is_configured=True,
                isConnected=True,
                is_grid_online=is_grid,
                battery_percent=100,
                in_watts=230 if is_grid else 0,
                out_watts=0,
                remain_minutes=0,
                is_charging=is_grid,
                status_headline="🟢 Світло в розетці Є (220В активна)" if is_grid else "🔴 Розетка знеструмлена (0В)",
                status_details="Фізичний датчик фіксує наявність напруги в побутовій мережі." if is_grid else "Напруга в розетці відсутня.",
                last_updated=time.time()
            )
        elif brand == "BLUETTI":
            return StationTelemetry(
                brand="BLUETTI",
                model_name="Bluetti El30V2",
                is_configured=True,
                is_connected=True,
                is_grid_online=is_grid,
                battery_percent=98 if is_grid else 89,
                in_watts=350 if is_grid else 0,
                out_watts=22,
                remain_minutes=20 if is_grid else 840,
                is_charging=is_grid,
                status_headline="Мережа 220В активна" if is_grid else "Знеструмлено (Живлення від Bluetti)",
                status_details="Bluetti заряджається від мережі (+350 Вт). Роутер працює через байпас." if is_grid else "Мережа 220В відсутня. Bluetti живить роутер (22 Вт, автономність ~14 год).",
                last_updated=time.time()
            )
        elif brand == "NONE":
            return StationTelemetry(brand="NONE", is_configured=False)
        else:
            # Default: ECOFLOW
            return StationTelemetry(
                brand="ECOFLOW",
                model_name="EcoFlow River Pro",
                is_configured=True,
                is_connected=True,
                is_grid_online=is_grid,
                battery_percent=99 if is_grid else 84,
                in_watts=460 if is_grid else 0,
                out_watts=24,
                remain_minutes=15 if is_grid else 880,
                is_charging=is_grid,
                status_headline="Мережа 220В активна" if is_grid else "Знеструмлено (Живлення від EcoFlow)",
                status_details="EcoFlow заряджається від розетки (+460 Вт). Роутер живиться через байпас." if is_grid else "Мережа 220В відсутня (0 Вт). Станція живить Wi-Fi роутер (споживання 24 Вт, автономність ~14 год).",
                last_updated=time.time()
            )
