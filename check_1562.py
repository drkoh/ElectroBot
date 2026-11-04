import requests
import re

headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
try:
    r = requests.get('https://1562.kharkivrada.gov.ua/', headers=headers, timeout=10)
    print("1562 Portal status:", r.status_code)
    print("Title:", re.findall(r'<title>(.*?)</title>', r.text))
    # Look for API endpoints or scripts
    scripts = re.findall(r'<script[^>]*src="([^"]+)"', r.text)
    print("Scripts:", scripts[:5])
    links = re.findall(r'href="([^"]+)"', r.text)
    print("Links:", [l for l in links if "discon" in l or "announ" in l or "ogol" in l or "work" in l or "alarm" in l][:10])
except Exception as e:
    print("1562 error:", e)
