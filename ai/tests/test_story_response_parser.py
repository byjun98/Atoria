"""Tests for StoryResponseParser."""
from __future__ import annotations

import json

import pytest

from app.services.story.story_response_parser import (
    StoryResponseError,
    StoryResponseParser,
)


def _valid_payload():
    return {
        "title": "별을 읽는 여왕의 길",
        "intro": "오늘 두 형제는 경주의 별빛을 따라 걷습니다.",
        "places": [
            {
                "sequence": 1,
                "place_id": "301",
                "place_name": "첨성대",
                "story_fragment": "첨성대 앞에 도착한 두 형제는 하늘을 올려다보았어요.",
                "mission": {
                    "mission_title": "별 세기",
                    "mission_instruction": "첨성대의 돌 단을 세어 보세요.",
                    "mission_type": "observation",
                    "verification_hint": None,
                    "related_place_name": "첨성대",
                    "related_chunk_ids": ["c1"],
                    "mission_keywords": ["별"],
                },
                "used_chunk_ids": ["c1"],
                "source_urls": ["https://x"],
            }
        ],
        "outro": "여정을 마치고 두 형제는 돌아갔습니다.",
        "used_chunk_ids": ["c1"],
        "source_urls": ["https://x"],
        "warnings": [],
    }


def test_parses_valid_json():
    raw = json.dumps(_valid_payload(), ensure_ascii=False)
    draft = StoryResponseParser().parse_intro_response(raw)
    assert draft.title == "별을 읽는 여왕의 길"
    assert len(draft.places) == 1
    assert draft.places[0].mission.mission_type == "observation"


def test_handles_code_fence_wrapper():
    raw = "```json\n" + json.dumps(_valid_payload(), ensure_ascii=False) + "\n```"
    draft = StoryResponseParser().parse_intro_response(raw)
    assert draft.title


def test_empty_response_raises():
    with pytest.raises(StoryResponseError) as ei:
        StoryResponseParser().parse_intro_response("   ")
    assert ei.value.code == "STORY_LLM_EMPTY_RESPONSE"


def test_invalid_json_raises():
    with pytest.raises(StoryResponseError) as ei:
        StoryResponseParser().parse_intro_response("{not valid json")
    assert ei.value.code == "STORY_LLM_INVALID_JSON"


def test_schema_validation_error_raises():
    bad = {"title": "x"}  # missing intro/places/outro
    with pytest.raises(StoryResponseError) as ei:
        StoryResponseParser().parse_intro_response(json.dumps(bad))
    assert ei.value.code == "STORY_LLM_SCHEMA_VALIDATION_ERROR"
