import asyncio

from app.clients.weather_client import WeatherClient


async def main():
    client = WeatherClient(stn=283)

    weather = client.fetch_current_weather()

    print("=== Weather Context ===")
    print(weather)


if __name__ == "__main__":
    asyncio.run(main())
