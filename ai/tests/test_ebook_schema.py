"""Schema tests for E-book draft (156)."""
from __future__ import annotations

import pytest

from app.schemas.ebook_schema import (
    EbookDraft,
    EbookDraftGenerationRequest,
    EbookMissionInput,
    EbookMissionResultInput,
    EbookPageDraft,
    EbookPlaceInput,
    EbookStorySourceInput,
    EbookUserProfile,
)


def _request(**overrides):
    base = dict(
        user_profile=EbookUserProfile(nickname="민준", age_group="child"),
        places=[EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1)],
        story_source=EbookStorySourceInput(
            intro="여행을 시작합니다.",
            missions=[EbookMissionInput(
                sequence=1, title="첨성대 별 찾기",
                description="첨성대 둘레를 걸어 보세요.",
                type="ACTION", story="첨성대는 신라의 천문 관측 시설.",
            )],
            outro="여행을 마칩니다.",
        ),
    )
    base.update(overrides)
    return EbookDraftGenerationRequest(**base)


def test_request_constructs_with_minimum_fields():
    req = _request()
    assert req.places[0].place_name == "첨성대"


def test_empty_places_rejected():
    with pytest.raises(ValueError):
        _request(places=[])


def test_blank_intro_rejected():
    with pytest.raises(ValueError):
        EbookStorySourceInput(
            intro="   ", outro="x",
            missions=[EbookMissionInput(
                sequence=1, title="t", description="d", type="ACTION", story="s"
            )],
        )


def test_empty_missions_rejected():
    with pytest.raises(ValueError):
        EbookStorySourceInput(intro="i", outro="o", missions=[])


def test_blank_place_name_rejected():
    with pytest.raises(ValueError):
        EbookPlaceInput(place_name="   ", sequence=1)


def test_sequence_must_be_at_least_one():
    with pytest.raises(ValueError):
        EbookPlaceInput(place_name="x", sequence=0)


def test_max_page_body_chars_range():
    from app.schemas.ebook_schema import EbookDraftOptions
    with pytest.raises(ValueError):
        EbookDraftOptions(max_page_body_chars=100)
    with pytest.raises(ValueError):
        EbookDraftOptions(max_page_body_chars=99999)


def test_mission_result_preserves_photo_urls():
    r = EbookMissionResultInput(
        sequence=1, place_name="첨성대",
        photo_urls=["https://x/1.jpg", "https://x/2.jpg"],
        selected_keywords=["별", "창"],
    )
    assert r.photo_urls == ["https://x/1.jpg", "https://x/2.jpg"]
    assert r.selected_keywords == ["별", "창"]


def test_mission_input_optional_source_trace_fields():
    m = EbookMissionInput(
        sequence=1, title="t", description="d", type="ACTION", story="s",
        used_chunk_ids=["c_1"],
        source_urls=["https://x"],
    )
    assert m.used_chunk_ids == ["c_1"]
    assert m.source_urls == ["https://x"]
    # default 빈 배열로 동작
    m2 = EbookMissionInput(
        sequence=2, title="t", description="d", type="ACTION", story="s",
    )
    assert m2.used_chunk_ids == []
    assert m2.source_urls == []


def test_response_draft_constructs():
    page = EbookPageDraft(
        page_number=1, page_type="cover", title="t",
    )
    draft = EbookDraft(
        draft_id="d1", title="t", pages=[page], page_count=1,
    )
    assert draft.page_count == 1
    assert draft.pages[0].page_type == "cover"
