"""Schema validation tests for 151 story-generation contracts."""
from __future__ import annotations

import pytest

from app.schemas.story_schema import (
    CoursePlaceInput,
    MissionDraft,
    PlaceStoryDraft,
    RagContextItem,
    StoryGenerationOptions,
    StoryIntroDraft,
    StoryIntroGenerationRequest,
    StoryUserProfile,
)


def _request(**overrides):
    base = dict(
        user_profile=StoryUserProfile(persona="curious", language="ko"),
        places=[CoursePlaceInput(place_name="첨성대", sequence=1)],
    )
    base.update(overrides)
    return StoryIntroGenerationRequest(**base)


def test_request_constructs_with_minimal_fields():
    req = _request()
    assert req.places[0].place_name == "첨성대"
    assert req.options.use_rag is True


def test_empty_places_rejected():
    with pytest.raises(ValueError, match="places"):
        _request(places=[])


def test_blank_place_name_rejected():
    with pytest.raises(ValueError):
        CoursePlaceInput(place_name="   ", sequence=1)


def test_sequence_must_be_at_least_one():
    with pytest.raises(ValueError):
        CoursePlaceInput(place_name="x", sequence=0)


def test_mission_count_per_place_range():
    with pytest.raises(ValueError):
        StoryGenerationOptions(mission_count_per_place=0)
    with pytest.raises(ValueError):
        StoryGenerationOptions(mission_count_per_place=4)


def test_max_chars_range_enforced():
    with pytest.raises(ValueError):
        StoryGenerationOptions(max_intro_chars=100)
    with pytest.raises(ValueError):
        StoryGenerationOptions(max_intro_chars=99999)


def test_rag_context_item_preserves_urls_and_chunk_id():
    item = RagContextItem(
        chunk_id="c_chunk_000",
        heritage_name="첨성대",
        content_role="fact_context",
        source_type="heritage_context",
        source_urls=["https://encykorea.aks.ac.kr/x", "https://contents.history.go.kr/y"],
        context_text="...",
    )
    assert item.chunk_id == "c_chunk_000"
    assert len(item.source_urls) == 2


def test_invalid_content_role_rejected():
    with pytest.raises(ValueError):
        RagContextItem(
            chunk_id="x",
            heritage_name="첨성대",
            content_role="bogus_role",
            source_type="x",
            context_text="...",
        )


def test_response_draft_constructs():
    place = PlaceStoryDraft(
        sequence=1,
        place_name="첨성대",
        story_fragment="별이 빛난다.",
        mission=MissionDraft(
            mission_title="별 세기",
            mission_instruction="첨성대의 돌 단을 세어 보세요.",
            mission_type="observation",
            related_place_name="첨성대",
        ),
        used_chunk_ids=["c_chunk_000"],
        source_urls=["https://x"],
    )
    draft = StoryIntroDraft(
        title="별을 읽는 여왕의 길",
        intro="...",
        places=[place],
        outro="...",
        used_chunk_ids=["c_chunk_000"],
        source_urls=["https://x"],
    )
    assert draft.prompt_version == "story_intro_v1"
    assert draft.places[0].mission.mission_type == "observation"
