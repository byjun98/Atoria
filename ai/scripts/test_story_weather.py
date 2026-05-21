import json
from app.schemas.story_schema import (
    StoryIntroGenerationRequest,
    StoryUserProfile,
    CoursePlaceInput,
    WeatherContext
)
from app.services.story.story_prompt_builder import StoryPromptBuilder

def test_weather_in_prompt():
    # 1. 가짜 날씨 데이터와 요청 객체 생성
    fake_weather = WeatherContext(
        temperature=28.5,
        rainfall=0.0,
        humidity=60.0,
        wind_speed=2.5,
        wind_direction=180.0
    )
    
    req = StoryIntroGenerationRequest(
        user_profile=StoryUserProfile(nickname="테스터"),
        places=[CoursePlaceInput(place_name="불국사", sequence=1)],
        weather_context=fake_weather  # 날씨 주입
    )

    # 2. Prompt Builder로 프롬프트 생성
    builder = StoryPromptBuilder()
    prompt_text = builder.build_intro_prompt_text(req)

    # 3. 결과 확인
    print("=== 생성된 프롬프트 중 WEATHER_CONTEXT 부분 ===")
    
    # 전체 프롬프트에서 [WEATHER_CONTEXT] 부분만 찾아서 출력
    lines = prompt_text.split('\n')
    weather_start = -1
    for i, line in enumerate(lines):
        if "[WEATHER_CONTEXT]" in line:
            weather_start = i
            break
            
    if weather_start != -1:
        for line in lines[weather_start:weather_start+10]:
            if line.startswith("[RAG_CONTEXT]"):
                break
            print(line)
    else:
        print("[WEATHER_CONTEXT] 블록을 찾을 수 없습니다!")

if __name__ == "__main__":
    test_weather_in_prompt()
