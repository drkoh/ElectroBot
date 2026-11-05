import requests
import re
import urllib.parse
from dataclasses import dataclass, field
from typing import List, Dict, Optional
from datetime import datetime
from core.address_utils import extract_street_and_building, is_building_in_list

@dataclass
class EmergencyOutage1562:
    service: str
    work_type: str
    start_time: str
    planned_end_time: str
    modified_time: str
    raw_announcement: str
    affected_streets: Dict[str, List[str]] = field(default_factory=dict)
    
    @property
    def workType(self) -> str:
        return self.work_type

    @property
    def reason(self) -> str:
        return self.work_type
    
    def matches_address(self, street_query: str, building: str = "") -> bool:
        if not building:
            st_clean, b_found = extract_street_and_building(street_query)
            if b_found:
                street_query = st_clean
                building = b_found

        q_norm = street_query.lower().replace("вул.", "").replace("вулиця", "").replace("просп.", "").replace("пр.", "").strip()
        stem = q_norm[:5] if len(q_norm) >= 5 else q_norm
        
        for street_name, buildings in self.affected_streets.items():
            s_norm = street_name.lower().replace("вул.", "").replace("вулиця", "").strip()
            if stem in s_norm or q_norm in s_norm or s_norm in q_norm:
                if not building:
                    return True
                if is_building_in_list(building, buildings):
                    return True
        return False

# Cache
_cache_time = None
_cached_outages = []

def fetch_1562_announcements(force_refresh: bool = False) -> List[EmergencyOutage1562]:
    global _cache_time, _cached_outages
    now = datetime.now()
    if not force_refresh and _cached_outages and _cache_time:
        if (now - _cache_time).total_seconds() < 300: # 5 minutes cache
            return _cached_outages

    session = requests.Session()
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        'Referer': 'https://1562.kharkivrada.gov.ua/perelik-vsi/'
    }

    try:
        r_init = session.get('https://1562.kharkivrada.gov.ua/perelik-vsi/', headers=headers, timeout=8)
        
        # Collect parameters from the live page
        params = []
        for inp in re.finditer(r'<input[^>]*name="([^"]+)"[^>]*value="([^"]*)"', r_init.text):
            params.append((inp.group(1), inp.group(2)))
        for sel in re.finditer(r'<select[^>]*name="([^"]+)"[\s\S]*?</select>', r_init.text):
            sel_name = sel.group(1)
            sel_html = sel.group(0)
            for opt in re.finditer(r'<option[^>]*value="([^"]*)"[^>]*selected', sel_html):
                params.append((sel_name, opt.group(1)))

        serialized = urllib.parse.urlencode(params)
        post_data = {
            'data': serialized,
            'rn': '0',
            'all': '1',
            'isFrame': '',
            'hr': '1',
            'ls': '0',
            'conf': '0',
            'frameJeoId': '0'
        }

        r_list = session.post('https://1562.kharkivrada.gov.ua/ajax/inform/informList-v2.php', data=post_data, headers=headers, timeout=12)
        html = r_list.text
        
        outages = []
        
        # Split announcements by card container boundary
        blocks = re.split(r'(?=<div[^>]*class="row gx-3 mt-4"|<div[^>]*class="messageinform"|<div[^>]*id="infoId\d+")', html)
        if len(blocks) <= 1:
            blocks = re.split(r'(?=Початок:?\s*\d{2}\.\d{2}\.\d{2})', html)
            
        for block in blocks:
            if "Початок" not in block and "Заплановано" not in block:
                continue
                
            # Extract title & reason before stripping html
            m_title = re.search(r'<h5[^>]*>(.*?)</h5>', block, re.DOTALL)
            m_pre = re.search(r'<pre[^>]*>(.*?)</pre>', block, re.DOTALL)
            
            title_text = re.sub(r'<[^>]+>', ' ', m_title.group(1)).strip() if m_title else ""
            pre_text = re.sub(r'<[^>]+>', ' ', m_pre.group(1)).strip() if m_pre else ""
            
            clean_b = re.sub(r'<style[^>]*>[\s\S]*?</style>', ' ', block)
            clean_b = re.sub(r'<[^>]+>', ' ', clean_b)
            clean_b = clean_b.replace("&nbsp;", " ").replace("&quot;", '"')
            clean_b = re.sub(r'\.forOrgMembersOnly\s*\{[^}]*\}', '', clean_b)
            clean_b = re.sub(r'\s+', ' ', clean_b).strip()
                
            # Extract start and planned
            m_start = re.search(r'Початок:?\s*(\d{2}\.\d{2}\.\d{2,4}\s+\d{2}:\d{2})', clean_b)
            m_plan = re.search(r'Заплановано:?\s*(\d{2}\.\d{2}\.\d{2,4}\s+\d{2}:\d{2})', clean_b)
            m_mod = re.search(r'Дата модифікації:?\s*(\d{2}\.\d{2}\.\d{2,4}\s+\d{2}:\d{2})', clean_b)
            m_exec = re.search(r'Виконавець:?\s*([^\|<\n\r]+?)(?=\s*Початок|\s*Заплановано|\s*Дата|\s*\[|\s*$)', clean_b)
            
            start_str = m_start.group(1) if m_start else ""
            plan_str = m_plan.group(1) if m_plan else ""
            mod_str = m_mod.group(1) if m_mod else ""
            exec_str = m_exec.group(1).strip() if m_exec else "Міські служби"
            
            # Extract affected streets and buildings: e.g. "[8] вул.Дудинської буд. 4/6, 4, 6, 8, 10, 10-А..."
            affected: Dict[str, List[str]] = {}
            for sm in re.finditer(r'\[\d+\]\s*(вул\.[^\s]+|просп\.[^\s]+|шосе\.[^\s]+|пров\.[^\s]+|[А-Яа-яІЇЄҐ]+\s+вул\.)\s*буд\.\s*([^\[]+)', clean_b):
                st_name = sm.group(1).strip()
                b_list = [b.strip() for b in sm.group(2).split(',') if b.strip()]
                affected[st_name] = b_list
                
            work_desc = pre_text if pre_text else title_text
            if not work_desc:
                idx_p = clean_b.find("Початок")
                work_desc = clean_b[:idx_p].strip() if idx_p > 0 else clean_b[:120]
            
            # Combine all text for search/filter
            full_raw = f"{title_text} | {pre_text} | {clean_b}"
            
            outages.append(EmergencyOutage1562(
                service=exec_str,
                work_type=work_desc[:180],
                start_time=start_str,
                planned_end_time=plan_str,
                modified_time=mod_str,
                raw_announcement=full_raw,
                affected_streets=affected
            ))
                
        _cached_outages = outages
        _cache_time = now
        return outages
    except Exception as e:
        print(f"Error fetching 1562: {e}")
        return _cached_outages or []

def check_address_in_1562(street_query: str, building: str = "") -> List[EmergencyOutage1562]:
    all_outages = fetch_1562_announcements()
    matched = []
    for o in all_outages:
        if o.matches_address(street_query, building):
            matched.append(o)
    return matched
