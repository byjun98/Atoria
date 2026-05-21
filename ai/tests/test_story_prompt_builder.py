"""Tests for StoryPromptBuilder."""
from __future__ import annotations

from app.schemas.story_schema import (
    CoursePlaceInput,
    RagContextByPlace,
    RagContextItem,
    StoryGenerationOptions,
    StoryIntroGenerationRequest,
    StoryUpdateGenerationRequest,
    StoryUserProfile,
)
from app.services.story import StoryPromptBuilder


def _request_with_rag():
    fact = RagContextItem(
        chunk_id="f_chunk_000",
        heritage_name="첨성대",
        content_role="fact_context",
        source_type="heritage_context",
        factuality_level="history",
        source_site="한국민족문화대백과",
        source_urls=["https://encykorea.aks.ac.kr/x"],
        context_text="신라의 천문 관측 시설.",
    )
    legend = RagContextItem(
        chunk_id="l_chunk_000",
        heritage_name="첨성대",
        content_role="legend_material",
        source_type="historical_anecdote",
        factuality_level="mixed",
        source_urls=["https://contents.history.go.kr/x"],
        context_text="선덕여왕이 별을 살폈다고 전해진다.",
        mission_hooks=["하늘과 별을 살피는 미션"],
    )
    return StoryIntroGenerationRequest(
        request_id="r1",
        user_profile=StoryUserProfile(
            nickname="민준", persona="curious", companion_type="family",
            story_theme="time_traveler", story_tone="warm", age_group="child",
        ),
        places=[
            CoursePlaceInput(place_name="첨성대", sequence=1, place_id="301"),
            CoursePlaceInput(place_name="동궁과월지", sequence=2, place_id="305"),
        ],
        options=StoryGenerationOptions(),
        rag_contexts=[
            RagContextByPlace(place_name="첨성대",
                              fact_contexts=[fact], story_materials=[legend]),
            RagContextByPlace(place_name="동궁과월지"),
        ],
    )


def test_build_intro_messages_returns_two_messages():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    assert len(msgs) == 2
    assert msgs[0]["role"] == "system"
    assert msgs[1]["role"] == "user"


def test_system_prompt_explains_three_role_rules():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    sys_text = msgs[0]["content"]
    assert "fact_context" in sys_text
    assert "legend_material" in sys_text
    assert "symbolic_material" in sys_text
    assert "JSON" in sys_text


def test_user_prompt_contains_user_profile_section():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    user = msgs[1]["content"]
    assert "[USER_PROFILE]" in user
    assert "민준" in user
    assert "family" in user


def test_user_prompt_contains_ordered_places():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    user = msgs[1]["content"]
    assert "[COURSE_PLACES]" in user
    cs = user.index("첨성대")
    dg = user.index("동궁과월지")
    assert cs < dg  # sequence ascending


def test_user_prompt_includes_rag_context_block():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    user = msgs[1]["content"]
    assert "[RAG_CONTEXT]" in user
    assert "f_chunk_000" in user
    assert "l_chunk_000" in user
    assert "https://encykorea.aks.ac.kr/x" in user


def test_user_prompt_includes_output_json_schema():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    user = msgs[1]["content"]
    assert "OUTPUT_JSON_SCHEMA" in user
    assert '"places"' in user
    assert '"used_chunk_ids"' in user
    assert '"warnings"' in user


def test_user_prompt_demands_json_only():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    combined = msgs[0]["content"] + msgs[1]["content"]
    assert "JSON" in combined
    assert "코드펜스" in combined or "JSON 외" in combined


def test_build_update_messages_basic_structure():
    req = StoryUpdateGenerationRequest(
        story_session_id="sess-1",
        current_place_name="동궁과월지",
        user_profile=StoryUserProfile(persona="curious"),
        previous_story_state={"last_place": "첨성대"},
        completed_mission_result={"mission_title": "별 세기", "image_url": "https://x"},
        rag_contexts=[RagContextByPlace(place_name="동궁과월지")],
    )
    msgs = StoryPromptBuilder().build_update_messages(req)
    assert len(msgs) == 2
    user = msgs[1]["content"]
    assert "story_session_id: sess-1" in user
    assert "current_place_name: 동궁과월지" in user
    assert "PREVIOUS_STORY_STATE" in user
    assert "COMPLETED_MISSION_RESULT" in user
    assert "OUTPUT_JSON_SCHEMA" in user


def test_prompt_version_is_current_default():
    """Bumped to v3 when switching to the connected-story narrative model."""
    builder = StoryPromptBuilder()
    assert builder.prompt_version == "story_intro_v4_fresh_design"


# ---- 154-prompt-pass: enriched prompt sections -------------------------


def test_user_prompt_includes_mission_quality_rules():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    user = msgs[1]["content"]
    assert "[MISSION_QUALITY_RULES]" in user
    assert "장소 고유 관찰 요소" in user


def test_user_prompt_includes_place_specificity_rules():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    user = msgs[1]["content"]
    assert "[PLACE_SPECIFICITY_RULES]" in user
    assert "mission_keywords" in user


def test_user_prompt_includes_style_rules():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    user = msgs[1]["content"]
    assert "[STYLE_RULES]" in user


def test_system_prompt_includes_good_and_bad_title_examples():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    sys_text = msgs[0]["content"]
    assert "좋은/나쁜 mission_title 예시" in sys_text
    assert "남쪽 창" in sys_text  # one of the good examples
    assert "탐험" in sys_text     # bad example token


def test_system_prompt_includes_three_tier_factuality_phrasing():
    msgs = StoryPromptBuilder().build_intro_messages(_request_with_rag())
    sys_text = msgs[0]["content"]
    assert "전해진다" in sys_text
    assert "상징적으로" in sys_text
    assert "fact_context" in sys_text and "legend_material" in sys_text and "symbolic_material" in sys_text
