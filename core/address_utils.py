import re
from typing import Tuple, List

def extract_street_and_building(text: str) -> Tuple[str, str]:
    """
    Parses full address string into (street_name, building_number).
    Examples:
      'вул. Дудинської, 10' -> ('вул. Дудинської', '10')
      'Дудинської 4/6' -> ('Дудинської', '4/6')
      'пр. Науки, буд. 25-Б' -> ('пр. Науки', '25-Б')
      'Сумська' -> ('Сумська', '')
    """
    if not text:
        return "", ""
        
    s = text.strip()
    
    # Check for patterns like ", буд. 10", ", 10-А", " 10-А"
    m = re.search(r'(?:,?\s*(?:буд\.?|б\.?|д\.?|будинок)\s*|,\s*|\s+)(\d+[\w\/\-\\\.]*)\s*$', s, re.IGNORECASE)
    if m:
        bld = m.group(1).strip()
        street = s[:m.start()].strip().rstrip(',').strip()
        # Ensure street isn't just empty or type prefix
        if len(street) >= 2:
            return street, bld
            
    return s, ""

def normalize_building(b: str) -> str:
    """Normalizes building string: lowercase, trims, standardizes slashes/dashes."""
    if not b:
        return ""
    b_norm = b.strip().lower()
    b_norm = re.sub(r'^(?:буд\.?|б\.?)\s*', '', b_norm)
    # Remove surrounding spaces around slashes or hyphens
    b_norm = re.sub(r'\s*[\/\\]\s*', '/', b_norm)
    b_norm = re.sub(r'\s*-\s*', '-', b_norm)
    return b_norm

def building_matches(target_b: str, candidate_b: str) -> bool:
    """
    Safely checks if user's building matches candidate building from 1562 list.
    Avoids false positives like '1' matching '10' or '14'.
    """
    t = normalize_building(target_b)
    c = normalize_building(candidate_b)
    
    if not t or not c:
        return False
        
    if t == c:
        return True
        
    # Check slash combos: e.g. "4/6" matches "4" or "6"
    t_parts = t.split('/')
    c_parts = c.split('/')
    if len(t_parts) > 1 or len(c_parts) > 1:
        if t in c_parts or c in t_parts:
            return True
            
    # Check letter suffixes: e.g. "10а" vs "10-а"
    t_clean = t.replace('-', '')
    c_clean = c.replace('-', '')
    if t_clean == c_clean:
        return True
        
    return False

def is_building_in_list(user_building: str, building_list: List[str]) -> bool:
    if not user_building:
        return True
    if not building_list:
        # If no specific buildings are listed, whole street is affected
        return True
        
    for cand in building_list:
        if building_matches(user_building, cand):
            return True
    return False
