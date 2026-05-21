import httpx
import os
from dotenv import load_dotenv
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

load_dotenv("c:/Users/SSAFY/Desktop/자율/.env")
key = os.getenv("WEATHER_KEY")

if not key:
    print("No WEATHER_KEY found in .env")
    exit(1)

target_time = datetime.now(ZoneInfo("Asia/Seoul")) - timedelta(minutes=5)
tm2 = target_time.strftime("%Y%m%d%H%M")
url = f"https://apihub.kma.go.kr/api/typ01/cgi-bin/url/nph-aws2_min?tm2={tm2}&stn=283&disp=0&help=1&authKey={key}"

print("Fetching:", url)
res = httpx.get(url)
print(res.status_code)
res.encoding = 'euc-kr'
print(repr(res.text[:1000]))
