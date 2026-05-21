"""API tests for /story/intro using FastAPI dependency override."""
from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient

from app.api.v1.story import get_story_intro_service
from app.main import app
from app.schemas.story_schema import (
    MissionDraft,
    PlaceStoryDraft,
    StoryIntroDraft,
)
from app.schemas.story import StoryIntroRequest
from app.services.story.story_schema_adapter import to_internal_request
from app.services.story.story_response_parser import StoryResponseError


class FakeService:
    def __init__(self, draft=None, raise_exc=None):
        self.draft = draft
        self.raise_exc = raise_exc
        self.last_request = None

    def generate_intro(self, request):
        self.last_request = request
        if self.raise_exc:
            raise self.raise_exc
        return self.draft


def _draft(place_count=2):
    places = []
    for i in range(1, place_count + 1):
        places.append(PlaceStoryDraft(
            sequence=i,
            place_id=str(i),
            place_name="첨성대" if i == 1 else "동궁과월지",
            story_fragment=f"장면 {i}",
            mission=MissionDraft(
                mission_title=f"미션 {i}",
                mission_instruction="관찰하세요.",
                mission_type="photo" if i == 1 else "observation",
                related_place_name="첨성대" if i == 1 else "동궁과월지",
            ),
            used_chunk_ids=[],
            source_urls=[],
        ))
    return StoryIntroDraft(
        title="오늘의 경주",
        intro="이야기 시작",
        places=places,
        outro="이야기 끝",
    )


def _payload(place_count=2):
    places = [
        {"place_id": 301, "sequence": 1, "name": "첨성대", "description": "신라 천문 관측소",
         "address": "경북 경주시", "category": "역사", "latitude": 35.83, "longitude": 129.21},
        {"place_id": 302, "sequence": 2, "name": "동궁과월지", "description": "신라 별궁",
         "address": "경북 경주시", "category": "역사", "latitude": 35.84, "longitude": 129.23},
    ][:place_count]
    return {
        "people_cnt": 2,
        "people_information": [
            {"name": "민준", "age": 5, "tendency": "curious"},
            {"name": "성준", "age": 6, "tendency": "curious"},
        ],
        "places": places,
    }


@pytest.fixture
def fake_service():
    svc = FakeService()
    app.dependency_overrides[get_story_intro_service] = lambda: svc
    yield svc
    app.dependency_overrides.pop(get_story_intro_service, None)


@pytest.fixture
def client():
    return TestClient(app)


def test_intro_happy_path_returns_wire_format(client, fake_service):
    fake_service.draft = _draft()
    r = client.post("/story/intro", json=_payload())
    assert r.status_code == 200
    body = r.json()
    assert "intro" in body and "outro" in body
    assert isinstance(body["missions"], list) and len(body["missions"]) == 2
    m1 = body["missions"][0]
    assert set(m1.keys()) == {
        "sequence", "title", "description", "verification_hint", "type", "story",
    }
    assert m1["verification_hint"] is None
    assert m1["type"] == "PHOTO"  # mapped from "photo"
    assert body["missions"][1]["type"] == "ACTION"  # mapped from "observation"


def test_internal_request_carries_adapted_profile(client, fake_service):
    fake_service.draft = _draft()
    client.post("/story/intro", json=_payload())
    req = fake_service.last_request
    assert req.user_profile.nickname == "민준"
    # youngest age 5 → child
    assert req.user_profile.age_group == "child"
    # 2 people → family
    assert req.user_profile.companion_type == "family"
    # places adapted
    assert [p.place_name for p in req.places] == ["첨성대", "동궁과월지"]
    assert req.places[0].metadata["latitude"] == 35.83


def test_internal_request_normalizes_ssafy_demo_place_names():
    wire = StoryIntroRequest(
        people_cnt=1,
        people_information=[
            {"name": "민준", "age": 7, "tendency": "curious"},
        ],
        places=[
            {
                "place_id": 1,
                "sequence": 1,
                "name": "첨성대",
                "description": "신라 천문 관측 유적입니다.",
                "address": "경상북도 경주시",
                "category": "문화유산",
                "latitude": 35.8346,
                "longitude": 129.2190,
            },
            {
                "place_id": 18,
                "sequence": 2,
                "name": "첨성대_ssafy",
                "description": "시연을 위한 자료입니다.",
                "address": "경상북도 구미시",
                "category": "문화유산",
                "latitude": 36.107159,
                "longitude": 128.416290,
            },
            {
                "place_id": 19,
                "sequence": 3,
                "name": "불국사 다보탑_ssafy",
                "description": "시연을 위한 자료입니다.",
                "address": "경상북도 구미시",
                "category": "문화유산",
                "latitude": 36.107168,
                "longitude": 128.416512,
            },
        ],
    )

    req = to_internal_request(wire)

    assert [p.place_name for p in req.places] == ["첨성대", "첨성대", "불국사 다보탑"]
    assert req.places[0].metadata["original_place_name"] == "첨성대"
    assert req.places[0].metadata["rag_heritage_name"] == "첨성대"
    assert req.places[1].metadata["original_place_name"] == "첨성대_ssafy"
    assert req.places[1].metadata["rag_heritage_name"] == "첨성대"
    assert req.places[2].metadata["original_place_name"] == "불국사 다보탑_ssafy"
    assert req.places[2].metadata["rag_heritage_name"] == "불국사 다보탑"


def test_validation_error_on_missing_places(client, fake_service):
    bad = _payload()
    bad["places"] = []
    r = client.post("/story/intro", json=bad)
    assert r.status_code == 422  # Pydantic on the wire schema


def test_llm_invalid_json_returns_502(client, fake_service):
    fake_service.raise_exc = StoryResponseError(
        "STORY_LLM_INVALID_JSON", "bad json from LLM"
    )
    r = client.post("/story/intro", json=_payload())
    assert r.status_code == 502
    assert r.json()["detail"]["code"] == "STORY_LLM_INVALID_JSON"


def test_chat_failure_returns_500(client, fake_service):
    fake_service.raise_exc = RuntimeError("openai down")
    r = client.post("/story/intro", json=_payload())
    assert r.status_code == 500
    assert r.json()["detail"]["code"] == "STORY_GENERATION_FAILED"
