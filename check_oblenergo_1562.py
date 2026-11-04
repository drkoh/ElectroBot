import requests
import urllib.parse

session = requests.Session()
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
    'Referer': 'https://1562.kharkivrada.gov.ua/perelik-vsi/'
}

r_init = session.get('https://1562.kharkivrada.gov.ua/perelik-vsi/', headers=headers)

# Let's filter by organizationMultiSelect[]=184 (Харківобленерго)
data_params = [
    ('from', '2026/11/01'),
    ('to', '2026/11/10'),
    ('organizationMultiSelect[]', '184'),
    ('radio1580works', '0'),
    ('radio1580datetype', '0'),
    ('streetid', ''),
    ('skyscraperid', '')
]
serialized = urllib.parse.urlencode(data_params)

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

r_list = session.post('https://1562.kharkivrada.gov.ua/ajax/inform/informList-v2.php', data=post_data, headers=headers)
print("informList 184 status:", r_list.status_code, "len:", len(r_list.text))
with open("oblenergo_1562.html", "w", encoding="utf-8") as f:
    f.write(r_list.text)

import re
clean = re.sub(r'<[^>]+>', ' ', r_list.text)
clean = re.sub(r'\s+', ' ', clean)
print("Sample:", clean[:500])
