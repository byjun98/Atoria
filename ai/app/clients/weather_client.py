"""
KMA API Hub AWS minute data client.
"""
from __future__ import annotations

import httpx
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from typing import Optional

from pydantic import BaseModel
from app.core.config import settings
from app.core.logging import get_logger

_logger = get_logger(__name__)


class WeatherData(BaseModel):
    temperature: Optional[float] = None  # TA
    rainfall: Optional[float] = None     # RN (using RN-60m or RN-day or just any RN field available, typically RN-60m or RN-day. Actually, AWS min has RN-15m, RN-60m. We can use RN-60m)
    humidity: Optional[float] = None     # HM
    wind_speed: Optional[float] = None   # WS
    wind_direction: Optional[float] = None  # WD


class WeatherClient:
    """Client for KMA API Hub AWS minute data."""

    def __init__(self, stn: int = 283) -> None:
        self.stn = stn
        self.api_key = settings.WEATHER_KEY
        self.base_url = "https://apihub.kma.go.kr/api/typ01/cgi-bin/url/nph-aws2_min"

    def fetch_current_weather(self) -> WeatherData:
        """Fetches weather data from 5 minutes ago (KST) to avoid missing recent data."""
        if not self.api_key:
            _logger.warning("Weather API key (WEATHER_KEY) is not set. Skipping weather fetch.")
            return WeatherData()

        target_time = datetime.now(ZoneInfo("Asia/Seoul")) - timedelta(minutes=5)
        tm2 = target_time.strftime("%Y%m%d%H%M")

        params = {
            "tm2": tm2,
            "stn": self.stn,
            "disp": 0,
            "help": 1,
            "authKey": self.api_key
        }

        try:
            with httpx.Client(timeout=10.0) as client:
                response = client.get(self.base_url, params=params)
                response.raise_for_status()
                response.encoding = 'euc-kr'
                return self._parse_response(response.text)
        except Exception as e:
            _logger.error(f"Failed to fetch weather data: {e}")
            return WeatherData()

    def _parse_response(self, text: str) -> WeatherData:
        """
        Parses the fixed-width/space-separated KMA AWS minute response.
        Expected format with help=1:
        # ...
        # YYMMDDHHMI  STN  WD  WS  GST  WD1  WS1   TA   RE   RN-15M   RN-60M   RN-12H   RN-24H     RN-DAY   HM   PA   PS
        202302010900  283 ...
        """
        lines = text.strip().split('\n')
        
        headers = []
        data_parts = []
        
        for line in lines:
            line = line.strip()
            if not line:
                continue
            if line.startswith('#'):
                # Try to find the header line that contains column names
                if 'YYMMDDHHMI' in line or 'TM' in line.upper():
                    # Clean up the '#' and split
                    headers = [h.upper() for h in line.lstrip('#').strip().split()]
            else:
                # First non-comment line is the data
                data_parts = line.split()
                break

        if not headers or not data_parts:
            _logger.warning("Weather API returned empty or unparseable data.")
            return WeatherData()

        weather = WeatherData()

        def get_val(col_name: str) -> Optional[float]:
            try:
                idx = headers.index(col_name)
                if idx < len(data_parts):
                    val = float(data_parts[idx])
                    # KMA often uses -99.9 or -999 for missing values
                    if val > -99:
                        return val
            except ValueError:
                pass
            return None

        def first_not_none(*values):
            for value in values:
                if value is not None:
                    return value
            return None

        weather.temperature = get_val('TA')
        weather.rainfall = get_val('RN-15m')
        weather.humidity = get_val('HM')
        weather.wind_speed = first_not_none(get_val('WS10'), get_val('WS1'))
        weather.wind_direction = first_not_none(get_val('WD10'), get_val('WD1'))

        # For rainfall, KMA AWS minute data usually has RN-60M or RN-DAY
        rn = get_val('RN-60M')
        if rn is None:
            rn = get_val('RN-DAY')
        if rn is None:
            rn = get_val('RN-15M')
        weather.rainfall = rn

        return weather
