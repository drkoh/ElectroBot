import os
import sqlite3
import json
import re
from typing import List, Dict, Any, Tuple
from pypdf import PdfReader

PDF_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "sample_queue.pdf")
DB_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "queues.db")
STREETS_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "kharkiv_streets.json")

COLUMNS = [
    (559.2, "1.1"),
    (610.3, "1.2"),
    (663.4, "2.1"),
    (712.2, "2.2"),
    (763.1, "3.1"),
    (809.6, "3.2"),
    (859.3, "4.1"),
    (905.8, "4.2"),
    (955.5, "5.1"),
    (1002.0, "5.2"),
    (1051.7, "6.1"),
    (1098.1, "6.2"),
]

def find_closest_queue_col(x: float) -> str | None:
    best_dist = 999999
    best_col = None
    for col_x, col_name in COLUMNS:
        dist = abs(x - col_x)
        if dist < best_dist:
            best_dist = dist
            best_col = col_name
    if best_dist < 26.0:
        return best_col
    return None

def normalize_text(text: str) -> str:
    t = text.replace("’", "'").replace("`", "'").replace("«", '"').replace("»", '"')
    t = re.sub(r'\s+', ' ', t).strip()
    return t

def get_ukrainian_stem(word: str) -> str:
    w = normalize_text(word).lower()
    endings = ["ського", "ської", "ському", "ською", "ських", "ським", "ськими",
               "ої", "ій", "ою", "их", "им", "ими", "ого", "ому", "а", "я", "е", "є", "и", "і", "у", "ю", "ом", "ем", "ям"]
    for end in endings:
        if w.endswith(end) and len(w) > len(end) + 3:
            return w[:-len(end)]
    return w

def build_database_from_pdf(pdf_path: str = PDF_PATH, db_path: str = DB_PATH) -> int:
    records = []
    
    if os.path.exists(pdf_path):
        reader = PdfReader(pdf_path)
        for page_idx, page in enumerate(reader.pages):
            elements = []
            def visitor(text, cm, tm, font_dict, font_size):
                if text.strip():
                    elements.append((tm[4], tm[5], text.strip()))
            page.extract_text(visitor_text=visitor)
            
            lines_by_y = {}
            for x, y, text in elements:
                if page_idx == 0 and y > 770:
                    continue
                if page_idx > 0 and y > 835:
                    continue
                matched_y = None
                for ey in lines_by_y:
                    if abs(y - ey) < 3.5:
                        matched_y = ey
                        break
                if matched_y is None:
                    matched_y = y
                    lines_by_y[matched_y] = []
                lines_by_y[matched_y].append((x, text))
                
            current_text_lines = []
            current_markers = []
            sorted_ys = sorted(lines_by_y.keys(), reverse=True)
            
            for y in sorted_ys:
                items = sorted(lines_by_y[y], key=lambda it: it[0])
                text_parts = [it[1] for it in items if it[0] < 530]
                marker_parts = [it for it in items if it[0] >= 530 and it[1] in ('X', 'x', 'Х', 'х', '+', '*')]
                
                line_text = " ".join(text_parts).strip()
                if line_text:
                    current_text_lines.append(line_text)
                    
                for x_pos, m in marker_parts:
                    q = find_closest_queue_col(x_pos)
                    if q and q not in current_markers:
                        current_markers.append(q)
                        
                if current_markers:
                    full_addr = normalize_text(" ".join(current_text_lines))
                    if full_addr:
                        records.append({
                            "page": page_idx + 1,
                            "address": full_addr,
                            "queues": list(current_markers)
                        })
                    current_text_lines = []
                    current_markers = []

    # Init SQLite
    os.makedirs(os.path.dirname(db_path), exist_ok=True)
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    
    cur.execute("DROP TABLE IF EXISTS records")
    cur.execute("DROP TABLE IF EXISTS kharkiv_streets")
    
    cur.execute("""
        CREATE TABLE records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            page INTEGER,
            address TEXT NOT NULL,
            address_lower TEXT NOT NULL,
            queues TEXT NOT NULL
        )
    """)
    cur.execute("CREATE INDEX idx_address_lower ON records(address_lower)")
    
    for r in records:
        queues_str = ",".join(r["queues"])
        addr_clean = r["address"]
        cur.execute("INSERT INTO records (page, address, address_lower, queues) VALUES (?, ?, ?, ?)", 
                    (r["page"], addr_clean, addr_clean.lower(), queues_str))
        
    # Index Kharkiv official streets
    cur.execute("""
        CREATE TABLE kharkiv_streets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            adpid INTEGER UNIQUE,
            aname TEXT NOT NULL,
            aname_lower TEXT NOT NULL
        )
    """)
    cur.execute("CREATE INDEX idx_aname_lower ON kharkiv_streets(aname_lower)")
    
    if os.path.exists(STREETS_PATH):
        with open(STREETS_PATH, "r", encoding="utf-8") as f:
            streets = json.load(f)
        for s in streets:
            cur.execute("INSERT OR IGNORE INTO kharkiv_streets (adpid, aname, aname_lower) VALUES (?, ?, ?)",
                        (s["adpid"], s["aname"], s["aname"].lower()))
                        
    conn.commit()
    conn.close()
    return len(records)

def ensure_database():
    if not os.path.exists(DB_PATH):
        build_database_from_pdf()
    else:
        conn = sqlite3.connect(DB_PATH)
        cur = conn.cursor()
        cur.execute("SELECT count(*) FROM records")
        c1 = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='kharkiv_streets'")
        has_streets = cur.fetchone()[0] > 0
        conn.close()
        if c1 == 0 or not has_streets:
            build_database_from_pdf()

def search_records(query: str, limit: int = 60) -> List[Dict[str, Any]]:
    """Searches both PDF registry and all official Kharkiv streets with stem matching."""
    ensure_database()
    q = normalize_text(query).strip()
    if not q:
        return []
        
    stem = get_ukrainian_stem(q).lower()
    pattern = f"%{stem}%"
    
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    
    results = []
    seen_names = set()
    
    # 1. Official Kharkiv Streets Registry (exact stem match)
    cur.execute("""
        SELECT adpid, aname FROM kharkiv_streets 
        WHERE aname_lower LIKE ?
        ORDER BY aname ASC LIMIT ?
    """, (pattern, limit))
    street_rows = cur.fetchall()
    
    for s in street_rows:
        display_name = f"Харків, {s['aname']}"
        seen_names.add(s["aname"].lower())
        results.append({
            "source": "Харків (Міський реєстр)",
            "address": display_name,
            "raw_street": s["aname"],
            "queues": [],
            "adpid": s["adpid"],
            "page": None
        })
        
    # 2. PDF Records (which have queues assigned)
    cur.execute("""
        SELECT id, page, address, queues FROM records 
        WHERE address_lower LIKE ? 
        ORDER BY id ASC LIMIT ?
    """, (pattern, limit))
    pdf_rows = cur.fetchall()
    
    for r in pdf_rows:
        queues_list = r["queues"].split(",") if r["queues"] else []
        results.append({
            "source": f"Реєстр ГПВ (Стор. {r['page']})",
            "address": r["address"],
            "raw_street": r["address"][:40],
            "queues": queues_list,
            "adpid": None,
            "page": r["page"]
        })
        
    conn.close()
    return results

if __name__ == "__main__":
    count = build_database_from_pdf()
    print(f"Rebuilt database! PDF records: {count}")
    for test in ["Дудинської", "Дудинська", "Гагаріна", "Сумська", "Сковороди"]:
        res = search_records(test, limit=3)
        print(f"\nSearch '{test}': found {len(res)}")
        for r in res:
            print(f"  [{r['source']}] {r['address']} -> Queues: {r['queues']}")
