"""Tests for EbookDraftService end-to-end composition."""
from __future__ import annotations

from app.schemas.ebook_schema import (
    EbookDraftGenerationRequest,
    EbookDraftOptions,
    EbookMissionInput,
    EbookMissionResultInput,
    EbookPlaceInput,
    EbookStorySourceInput,
    EbookUserProfile,
)
from app.services.ebook.ebook_draft_service import EbookDraftService


def _request(*, mission_count=2, results=None, options=None,
             story_source_urls=None, story_chunk_ids=None):
    places = [
        EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1),
        EbookPlaceInput(place_id=2, place_name="동궁과월지", sequence=2),
    ][:mission_count]
    missions = []
    for i in range(1, mission_count + 1):
        missions.append(EbookMissionInput(
            sequence=i,
            title=f"{places[i-1].place_name} 이야기 미션 {i}",
            description="현장에서 둘러 보세요.",
            type="ACTION",
            story=f"{places[i-1].place_name} 의 짧은 이야기.",
        ))
    return EbookDraftGenerationRequest(
        user_profile=EbookUserProfile(nickname="민준"),
        places=places,
        story_source=EbookStorySourceInput(
            intro="시작합니다.", outro="마칩니다.",
            missions=missions,
            source_urls=story_source_urls or [],
            used_chunk_ids=story_chunk_ids or [],
        ),
        mission_results=results or [],
        options=options or EbookDraftOptions(),
    )


def test_full_draft_generated_with_expected_page_types():
    req = _request(
        results=[EbookMissionResultInput(
            sequence=1, place_name="첨성대", completed=True,
            user_answer="창이 인상적이었다.",
        )],
        story_source_urls=["https://encykorea.aks.ac.kr/x"],
    )
    draft = EbookDraftService().generate_draft(req)
    types = [p.page_type for p in draft.pages]
    assert types[0] == "cover"
    assert "table_of_contents" in types
    assert "prologue" in types
    assert types.count("place_story") == 2
    assert "mission_result" in types  # 첨성대만 결과 있음 → 1개
    assert types[-2] == "epilogue"
    assert types[-1] == "sources"


def test_page_numbers_are_sequential_starting_from_one():
    draft = EbookDraftService().generate_draft(_request())
    for i, p in enumerate(draft.pages, start=1):
        assert p.page_number == i
    assert draft.page_count == len(draft.pages)


def test_include_table_of_contents_false_drops_toc():
    req = _request(options=EbookDraftOptions(include_table_of_contents=False))
    draft = EbookDraftService().generate_draft(req)
    assert "table_of_contents" not in [p.page_type for p in draft.pages]


def test_include_sources_false_drops_sources_even_if_urls_exist():
    req = _request(options=EbookDraftOptions(include_sources=False),
                   story_source_urls=["https://x"])
    draft = EbookDraftService().generate_draft(req)
    assert "sources" not in [p.page_type for p in draft.pages]


def test_missing_mission_for_place_emits_warning():
    # Only mission for sequence 1; place 2 has no mission.
    req = _request(mission_count=2)
    # Drop the second mission to trigger the warning.
    req.story_source.missions = req.story_source.missions[:1]
    draft = EbookDraftService().generate_draft(req)
    assert any("MISSING_MISSION_FOR_PLACE" in w for w in draft.warnings)
    # Place 2 still gets a place_story page (with no mission).
    assert sum(1 for p in draft.pages if p.page_type == "place_story") == 2


def test_missing_mission_result_emits_warning():
    req = _request()  # no mission_results
    draft = EbookDraftService().generate_draft(req)
    assert any("MISSING_MISSION_RESULT" in w for w in draft.warnings)
    assert "mission_result" not in [p.page_type for p in draft.pages]


def test_unmatched_mission_result_emits_warning():
    req = _request(results=[EbookMissionResultInput(
        sequence=99, place_name="없는장소", completed=True,
    )])
    draft = EbookDraftService().generate_draft(req)
    assert any("MISSION_RESULT_UNMATCHED" in w for w in draft.warnings)


def test_source_urls_deduped_and_collected():
    req = _request(story_source_urls=[
        "https://x/1", "https://x/2", "https://x/1",
    ])
    draft = EbookDraftService().generate_draft(req)
    assert draft.source_urls == ["https://x/1", "https://x/2"]


def test_used_chunk_ids_collected():
    req = _request(story_chunk_ids=["c_001", "c_002", "c_001"])
    draft = EbookDraftService().generate_draft(req)
    assert draft.used_chunk_ids == ["c_001", "c_002"]


def test_incomplete_mission_skipped_by_default():
    req = _request(results=[EbookMissionResultInput(
        sequence=1, place_name="첨성대", completed=False,
    )])
    draft = EbookDraftService().generate_draft(req)
    # mission_result page omitted for incomplete missions by default
    assert "mission_result" not in [p.page_type for p in draft.pages]


def test_include_incomplete_missions_emits_result_page():
    req = _request(
        options=EbookDraftOptions(include_incomplete_missions=True),
        results=[EbookMissionResultInput(
            sequence=1, place_name="첨성대", completed=False,
            user_answer="아직 못 갔다.",
        )],
    )
    draft = EbookDraftService().generate_draft(req)
    types = [p.page_type for p in draft.pages]
    assert types.count("mission_result") == 1


def test_draft_id_present_and_metadata_carries_request_id():
    req = _request()
    req = req.model_copy(update={"request_id": "req-001"})
    draft = EbookDraftService().generate_draft(req)
    assert draft.draft_id.startswith("ebook-draft-")
    assert draft.metadata.get("request_id") == "req-001"


def test_place_pages_inherit_story_source_trace_as_fallback():
    """story_source 의 source_urls / used_chunk_ids 가 page-level 로 흘러야 한다."""
    req = _request(
        story_source_urls=["https://encykorea.aks.ac.kr/x"],
        story_chunk_ids=["c_001", "c_002"],
    )
    draft = EbookDraftService().generate_draft(req)
    place_pages = [p for p in draft.pages if p.page_type == "place_story"]
    assert place_pages, "expected at least one place_story page"
    for page in place_pages:
        assert "https://encykorea.aks.ac.kr/x" in page.source_urls
        assert "c_001" in page.used_chunk_ids
        assert "c_002" in page.used_chunk_ids


def test_mission_specific_source_trace_overrides_fallback():
    """EbookMissionInput.used_chunk_ids 가 있으면 그게 우선."""
    from app.schemas.ebook_schema import EbookMissionInput
    req = _request(
        story_source_urls=["https://generic.example.com/fallback"],
        story_chunk_ids=["c_fallback"],
    )
    # mission 1: per-mission trace 가 따로 있음
    req.story_source.missions[0] = EbookMissionInput(
        sequence=1,
        title=req.story_source.missions[0].title,
        description=req.story_source.missions[0].description,
        type="ACTION",
        story=req.story_source.missions[0].story,
        used_chunk_ids=["c_specific_1"],
        source_urls=["https://specific.example.com/1"],
    )
    draft = EbookDraftService().generate_draft(req)
    p1 = next(p for p in draft.pages if p.page_type == "place_story" and p.place_name == "첨성대")
    p2 = next(p for p in draft.pages if p.page_type == "place_story" and p.place_name == "동궁과월지")
    assert p1.used_chunk_ids == ["c_specific_1"]
    assert p1.source_urls == ["https://specific.example.com/1"]
    # 두 번째 장소는 fallback
    assert "c_fallback" in p2.used_chunk_ids
    assert "https://generic.example.com/fallback" in p2.source_urls


def test_place_page_metadata_carries_mission_result_status():
    req = _request(results=[EbookMissionResultInput(
        sequence=1, place_name="첨성대", completed=True,
        user_answer="기록.",
    )])
    draft = EbookDraftService().generate_draft(req)
    p1 = next(p for p in draft.pages if p.page_type == "place_story" and p.place_name == "첨성대")
    p2 = next(p for p in draft.pages if p.page_type == "place_story" and p.place_name == "동궁과월지")
    assert p1.metadata.get("mission_result_status") == "completed"
    assert p2.metadata.get("mission_result_status") == "missing"


def test_truncated_long_story_page_emits_warning():
    long_story = "가" * 5000
    req = _request()
    req.story_source.missions[0] = EbookMissionInput(
        sequence=1, title="첨성대 별 찾기", description="x",
        type="ACTION", story=long_story,
    )
    req.options.max_page_body_chars = 300
    draft = EbookDraftService().generate_draft(req)
    assert any("PAGE_BODY_TRUNCATED" in w for w in draft.warnings)
