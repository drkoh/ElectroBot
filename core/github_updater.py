import os
import requests
from dataclasses import dataclass
from typing import Optional

CURRENT_VERSION = "v1.4"
GITHUB_REPO = "drkoh/ElectroBot"
RELEASES_API = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"

@dataclass
class UpdateInfo:
    has_update: bool
    is_latest: bool
    current_version: str
    latest_version: str
    release_title: str
    release_notes: str
    html_url: str
    download_url_exe: Optional[str] = None
    download_url_apk: Optional[str] = None

def is_version_newer(current: str, remote: str) -> bool:
    if not remote:
        return False
    clean_cur = current.lstrip("vV").strip()
    clean_rem = remote.lstrip("vV").strip()
    if clean_cur == clean_rem:
        return False
        
    def to_ints(v: str):
        parts = []
        for p in v.replace("-", ".").split("."):
            try:
                parts.append(int(p))
            except ValueError:
                pass
        return parts

    c_parts = to_ints(clean_cur)
    r_parts = to_ints(clean_rem)
    max_len = max(len(c_parts), len(r_parts))
    for i in range(max_len):
        c = c_parts[i] if i < len(c_parts) else 0
        r = r_parts[i] if i < len(r_parts) else 0
        if r > c:
            return True
        if r < c:
            return False
    return False

def check_github_updates(timeout_sec: int = 6) -> UpdateInfo:
    headers = {
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "ElectroBot-Desktop"
    }
    try:
        r = requests.get(RELEASES_API, headers=headers, timeout=timeout_sec)
        if r.status_code == 404:
            return UpdateInfo(
                has_update=False,
                is_latest=True,
                current_version=CURRENT_VERSION,
                latest_version=CURRENT_VERSION,
                release_title="Релізів ще немає",
                release_notes="Офіційних релізів на GitHub ще не опубліковано.",
                html_url=f"https://github.com/{GITHUB_REPO}/releases"
            )
        r.raise_for_status()
        data = r.json()
        
        tag = data.get("tag_name", "").strip()
        title = data.get("name") or tag
        body = data.get("body") or "Опис оновлення відсутній."
        html_url = data.get("html_url") or f"https://github.com/{GITHUB_REPO}/releases"
        
        url_exe = None
        url_apk = None
        for a in data.get("assets", []):
            name = a.get("name", "").lower()
            if name.endswith(".exe") or name.endswith(".zip"):
                url_exe = a.get("browser_download_url")
            elif name.endswith(".apk"):
                url_apk = a.get("browser_download_url")
                
        has_new = is_version_newer(CURRENT_VERSION, tag)
        return UpdateInfo(
            has_update=has_new,
            is_latest=not has_new,
            current_version=CURRENT_VERSION,
            latest_version=tag if tag else CURRENT_VERSION,
            release_title=title,
            release_notes=body,
            html_url=html_url,
            download_url_exe=url_exe,
            download_url_apk=url_apk
        )
    except Exception as e:
        return UpdateInfo(
            has_update=False,
            is_latest=False,
            current_version=CURRENT_VERSION,
            latest_version=CURRENT_VERSION,
            release_title="Помилка перевірки",
            release_notes=str(e),
            html_url=f"https://github.com/{GITHUB_REPO}/releases"
        )
