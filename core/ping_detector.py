import subprocess
import os
import re
import socket
from datetime import datetime
from dataclasses import dataclass
from typing import Optional

@dataclass
class PingResult:
    is_reachable: bool
    ip_address: str
    latency_ms: Optional[float]
    timestamp: datetime
    error_msg: str = ""

def detect_default_gateway() -> str:
    """Attempts to find the default gateway IP on Windows."""
    try:
        creationflags = getattr(subprocess, 'CREATE_NO_WINDOW', 0x08000000) if os.name == 'nt' else 0
        startupinfo = subprocess.STARTUPINFO() if os.name == 'nt' else None
        if startupinfo:
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = subprocess.SW_HIDE
            
        out = subprocess.check_output(
            ["ipconfig"],
            creationflags=creationflags,
            startupinfo=startupinfo,
            text=True,
            timeout=3
        )
        # Look for Default Gateway ... : 192.168.x.x
        m = re.search(r'(?:Default Gateway|Основной шлюз|Основний шлюз)[ .:]+([1-9]\d{0,2}(?:\.\d{1,3}){3})', out, re.IGNORECASE)
        if m:
            ip = m.group(1).strip()
            if ip != "0.0.0.0":
                return ip
    except Exception:
        pass
    return "192.168.1.1"

def ping_host(ip: str = "", timeout_ms: int = 1000) -> PingResult:
    """
    Pings a target host (router / gateway) without popping any console window.
    Returns PingResult.
    """
    if not ip:
        ip = detect_default_gateway()
        
    now = datetime.now()
    
    creationflags = getattr(subprocess, 'CREATE_NO_WINDOW', 0x08000000) if os.name == 'nt' else 0
    startupinfo = subprocess.STARTUPINFO() if os.name == 'nt' else None
    if startupinfo:
        startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startupinfo.wShowWindow = subprocess.SW_HIDE
        
    cmd = ["ping", "-n", "1", "-w", str(timeout_ms), ip] if os.name == 'nt' else ["ping", "-c", "1", "-W", "1", ip]
    
    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            creationflags=creationflags,
            startupinfo=startupinfo,
            text=True,
            timeout=3
        )
        out = proc.stdout
        if proc.returncode == 0 and ("TTL=" in out or "ttl=" in out):
            # Extract latency: "time=2ms" or "час=2мс"
            m = re.search(r'(?:time|час|время)[=<](\d+(?:\.\d+)?)ms', out, re.IGNORECASE)
            lat = float(m.group(1)) if m else 1.0
            return PingResult(
                is_reachable=True,
                ip_address=ip,
                latency_ms=lat,
                timestamp=now
            )
        else:
            return PingResult(
                is_reachable=False,
                ip_address=ip,
                latency_ms=None,
                timestamp=now,
                error_msg="Вузол недоступний (таймаут або відсутня відповідь)"
            )
    except Exception as e:
        return PingResult(
            is_reachable=False,
            ip_address=ip,
            latency_ms=None,
            timestamp=now,
            error_msg=str(e)
        )

if __name__ == "__main__":
    gw = detect_default_gateway()
    print(f"Detected Gateway: {gw}")
    res = ping_host(gw)
    print(f"Ping result: Reachable={res.is_reachable}, Latency={res.latency_ms}ms, Error='{res.error_msg}'")
