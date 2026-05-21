from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import requests

from app.core.config import settings


tm2 = (datetime.now(ZoneInfo("Asia/Seoul")) - timedelta(minutes=60)).strftime("%Y%m%d%H%M")

params = {
    "tm2": tm2,
    "stn": "283",
    "disp": "1",
    "help": "0",
    "authKey": settings.WEATHER_KEY,
}

url = "https://apihub.kma.go.kr/api/typ01/cgi-bin/url/nph-aws2_min"

res = requests.get(url, params=params, timeout=60)

print("STATUS:", res.status_code)
print("URL:", res.url)
print("===== RAW RESPONSE HEAD =====")
print(res.text[:3000])
