"""Print a sample story-generation prompt. No LLM / DB / RAG calls."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.schemas.story_schema import (  # noqa: E402
    CoursePlaceInput,
    RagContextByPlace,
    RagContextItem,
    StoryGenerationOptions,
    StoryIntroGenerationRequest,
    StoryUserProfile,
)
from app.services.story import StoryPromptBuilder  # noqa: E402


def _sample_request() -> StoryIntroGenerationRequest:
    fact = RagContextItem(
        chunk_id="heritage-cheomseongdae-encykorea-001_chunk_000",
        heritage_name="첨성대",
        title="첨성대 - 한국민족문화대백과",
        content_role="fact_context",
        source_type="heritage_context",
        factuality_level="history",
        source_site="한국민족문화대백과",
        source_urls=["https://encykorea.aks.ac.kr/Article/E0002925"],
        context_text="[문화재: 첨성대 | 콘텐츠 역할: fact_context | 사실성: history]\n경상북도 경주시에 있는 신라의 천문 관측 시설.",
        motifs=["천문", "별"],
    )
    legend = RagContextItem(
        chunk_id="legend_cheomseongdae_seondeok_001_chunk_000",
        heritage_name="첨성대",
        title="첨성대와 선덕여왕의 별 읽기",
        content_role="legend_material",
        source_type="historical_anecdote",
        factuality_level="mixed",
        source_site="우리역사넷",
        source_urls=["https://contents.history.go.kr/mobile/nh/view.do?levelId=nh_008_0060_0040_0010_0010"],
        context_text="[문화재: 첨성대 | 콘텐츠 역할: legend_material | 사실성: mixed]\n선덕여왕이 별을 살피며 백성을 헤아렸다고 전해진다.",
        mission_hooks=["하늘과 별을 상징하는 요소를 찾는 미션으로 활용 가능"],
        motifs=["별", "예언"],
        tone_tags=["신비", "지혜"],
    )

    return StoryIntroGenerationRequest(
        request_id="preview-001",
        user_profile=StoryUserProfile(
            nickname="민준",
            persona="curious",
            companion_type="family",
            story_theme="time_traveler",
            story_tone="warm",
            age_group="child",
        ),
        places=[
            CoursePlaceInput(place_id="301", place_name="첨성대", sequence=1,
                             expected_activity="별 관찰"),
            CoursePlaceInput(place_id="305", place_name="동궁과월지", sequence=2,
                             expected_activity="달빛 산책"),
        ],
        options=StoryGenerationOptions(),
        rag_contexts=[
            RagContextByPlace(
                place_name="첨성대",
                fact_contexts=[fact],
                story_materials=[legend],
            ),
            RagContextByPlace(place_name="동궁과월지"),
        ],
    )


def main() -> None:
    builder = StoryPromptBuilder()
    messages = builder.build_intro_messages(_sample_request())
    for i, m in enumerate(messages, 1):
        print(f"==== message {i} | role={m['role']} ====")
        print(m["content"])
        print()


if __name__ == "__main__":
    main()
