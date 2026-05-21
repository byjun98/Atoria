"""End-to-end quality-control flow tests for StoryIntroService."""
from __future__ import annotations

import json

import pytest

from app.core.config import settings
from app.schemas.rag_search_schema import RagSearchData
from app.schemas.story_schema import (
    CoursePlaceInput,
    StoryGenerationOptions,
    StoryIntroGenerationRequest,
    StoryUserProfile,
)
from app.services.story.story_intro_service import (
    StoryIntroService,
    StoryQualityValidationFailed,
)


# --- fakes --------------------------------------------------------------


class FakeRagSearchService:
    def search(self, request):
        return RagSearchData(
            query=request.query, embedding_model="fake",
            top_k=request.top_k or 5, result_count=0, results=[], filters={},
        )


class ScriptedChatClient:
    """Returns a sequence of pre-made JSON payloads, one per call."""

    def __init__(self, responses: list[str]):
        self._responses = list(responses)
        self.calls: list[list[dict]] = []

    def create_json_chat_completion(self, messages, temperature=None):
        self.calls.append(list(messages))
        return self._responses.pop(0)


def _request():
    return StoryIntroGenerationRequest(
        user_profile=StoryUserProfile(persona="curious"),
        places=[
            CoursePlaceInput(place_id="1", place_name="첨성대", sequence=1),
            CoursePlaceInput(place_id="2", place_name="동궁과월지", sequence=2),
        ],
        options=StoryGenerationOptions(use_rag=False),
    )


def _good_payload(place_count=2) -> str:
    places = []
    names = ["첨성대", "동궁과월지", "불국사 다보탑"]
    for i in range(1, place_count + 1):
        name = names[i - 1]
        places.append({
            "sequence": i,
            "place_id": str(i),
            "place_name": name,
            "story_fragment": (
                f"{name} 앞에 도착한 두 형제는 별이 그려진 옛 돌의 방향을 관찰하며 "
                f"오래된 천문 관측대를 상상해 보았어요. 오늘은 어떤 단서가 보일까요?"
            ),
            "mission": {
                "mission_title": f"{name}의 별 관찰",
                "mission_instruction": (
                    f"{name}에서 돌의 모양과 방향을 찾아요. 두 단서가 한 화면에 "
                    "보이면 클리어이고, 사진은 다음 별길 단서가 됩니다."
                ),
                "mission_type": "observation",
                "verification_hint": f"{name}의 돌 모양과 방향이 한 화면에 보이면 클리어입니다.",
                "related_place_name": name,
                "related_chunk_ids": [],
                "mission_keywords": ["별", "방향"],
            },
            "used_chunk_ids": [],
            "source_urls": [],
        })
    return json.dumps({
        "title": "별을 읽는 여정",
        "intro": (
            "오늘 두 형제는 경주의 오래된 별빛 길을 따라 걸으며 신라의 흔적을 찾아갑니다. "
            "곳곳에 숨은 단서를 함께 만나 보아요."
        ),
        "places": places,
        "outro": "여정의 끝에 두 형제는 오늘 만난 장면들을 마음에 담고 돌아왔습니다.",
        "used_chunk_ids": [],
        "source_urls": [],
        "warnings": [],
    }, ensure_ascii=False)


def _broken_payload() -> str:
    """Returns places=1 (count mismatch) and an empty intro."""
    return json.dumps({
        "title": "x",
        "intro": "",
        "places": [{
            "sequence": 1, "place_id": "1", "place_name": "첨성대",
            "story_fragment": "x" * 100,
            "mission": {
                "mission_title": "t", "mission_instruction": "x" * 50,
                "mission_type": "observation", "related_place_name": "첨성대",
                "related_chunk_ids": [], "mission_keywords": [],
            },
            "used_chunk_ids": [], "source_urls": [],
        }],
        "outro": "마무리.",
        "used_chunk_ids": [], "source_urls": [], "warnings": [],
    }, ensure_ascii=False)


# --- tests --------------------------------------------------------------


def test_clean_response_returns_draft():
    chat = ScriptedChatClient([_good_payload()])
    svc = StoryIntroService(rag_search_service=FakeRagSearchService(), chat_client=chat)
    draft = svc.generate_intro(_request())
    assert len(draft.places) == 2
    assert draft.intro and draft.outro
    # only 1 LLM call (no retry needed)
    assert len(chat.calls) == 1


def test_quality_failure_raises_when_retry_disabled(monkeypatch):
    monkeypatch.setattr(settings, "STORY_QUALITY_ENABLE_RETRY", False)
    chat = ScriptedChatClient([_broken_payload()])
    svc = StoryIntroService(rag_search_service=FakeRagSearchService(), chat_client=chat)
    with pytest.raises(StoryQualityValidationFailed) as ei:
        svc.generate_intro(_request())
    codes = {i.code for i in ei.value.report.issues}
    assert "EMPTY_INTRO" in codes
    assert "PLACE_COUNT_MISMATCH" in codes
    assert len(chat.calls) == 1  # no retry


def test_retry_succeeds_after_first_broken(monkeypatch):
    monkeypatch.setattr(settings, "STORY_QUALITY_ENABLE_RETRY", True)
    monkeypatch.setattr(settings, "STORY_QUALITY_MAX_RETRIES", 1)
    chat = ScriptedChatClient([_broken_payload(), _good_payload()])
    svc = StoryIntroService(rag_search_service=FakeRagSearchService(), chat_client=chat)
    draft = svc.generate_intro(_request())
    assert len(draft.places) == 2
    assert len(chat.calls) == 2
    # The retry call must include the REPAIR_INSTRUCTION block.
    user_msg = chat.calls[1][-1]["content"]
    assert "REPAIR_INSTRUCTION" in user_msg


def test_retry_then_still_broken_raises(monkeypatch):
    monkeypatch.setattr(settings, "STORY_QUALITY_ENABLE_RETRY", True)
    monkeypatch.setattr(settings, "STORY_QUALITY_MAX_RETRIES", 1)
    chat = ScriptedChatClient([_broken_payload(), _broken_payload()])
    svc = StoryIntroService(rag_search_service=FakeRagSearchService(), chat_client=chat)
    with pytest.raises(StoryQualityValidationFailed):
        svc.generate_intro(_request())
    assert len(chat.calls) == 2
