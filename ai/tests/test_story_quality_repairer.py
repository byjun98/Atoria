"""Unit tests for StoryQualityRepairer."""
from __future__ import annotations

from app.schemas.story_schema import (
    CoursePlaceInput,
    MissionDraft,
    PlaceStoryDraft,
    RagContextByPlace,
    RagContextItem,
    StoryIntroDraft,
    StoryIntroGenerationRequest,
    StoryUserProfile,
)
from app.services.story.story_quality_models import StoryQualityReport
from app.services.story.story_quality_repairer import StoryQualityRepairer


def _draft(places, used_chunk_ids=None, source_urls=None):
    return StoryIntroDraft(
        title="t", intro="i" * 100, outro="o" * 60,
        places=places,
        used_chunk_ids=used_chunk_ids or [],
        source_urls=source_urls or [],
    )


def _request(rag=None):
    return StoryIntroGenerationRequest(
        user_profile=StoryUserProfile(),
        places=[
            CoursePlaceInput(place_id="1", place_name="첨성대", sequence=1),
            CoursePlaceInput(place_id="2", place_name="동궁과월지", sequence=2),
        ],
        rag_contexts=rag or [],
    )


def test_places_are_sorted_by_sequence():
    p2 = PlaceStoryDraft(sequence=2, place_name="동궁과월지", story_fragment="x" * 100)
    p1 = PlaceStoryDraft(sequence=1, place_name="첨성대", story_fragment="x" * 100)
    draft = _draft([p2, p1])
    repaired, _ = StoryQualityRepairer().repair_intro_draft(draft, _request(), StoryQualityReport())
    assert [p.sequence for p in repaired.places] == [1, 2]


def test_related_place_name_backfilled():
    p = PlaceStoryDraft(
        sequence=1, place_name="첨성대", story_fragment="x" * 100,
        mission=MissionDraft(
            mission_title="t", mission_instruction="x" * 50,
            mission_type="observation", related_place_name="",
        ),
    )
    p2 = PlaceStoryDraft(sequence=2, place_name="동궁과월지", story_fragment="x" * 100)
    draft = _draft([p, p2])
    report = StoryQualityReport()
    repaired, report = StoryQualityRepairer().repair_intro_draft(draft, _request(), report)
    assert repaired.places[0].mission.related_place_name == "첨성대"
    assert report.fixed_count >= 1


def test_used_chunk_ids_and_source_urls_backfilled_from_rag():
    rag = [
        RagContextByPlace(
            place_name="첨성대",
            fact_contexts=[RagContextItem(
                chunk_id="c_001", heritage_name="첨성대",
                content_role="fact_context", source_type="x",
                context_text="...", source_urls=["https://x"],
            )],
            story_materials=[RagContextItem(
                chunk_id="l_001", heritage_name="첨성대",
                content_role="legend_material", source_type="x",
                factuality_level="mixed", context_text="...",
                source_urls=["https://y"],
            )],
        )
    ]
    p = PlaceStoryDraft(
        sequence=1, place_name="첨성대", story_fragment="x" * 100,
        mission=MissionDraft(
            mission_title="t", mission_instruction="x" * 50,
            mission_type="observation", related_place_name="첨성대",
        ),
    )
    p2 = PlaceStoryDraft(sequence=2, place_name="동궁과월지", story_fragment="x" * 100)
    draft = _draft([p, p2])
    repaired, report = StoryQualityRepairer().repair_intro_draft(
        draft, _request(rag=rag), StoryQualityReport()
    )
    assert "c_001" in repaired.places[0].used_chunk_ids
    assert "l_001" in repaired.places[0].used_chunk_ids
    assert "https://x" in repaired.places[0].source_urls
    assert "https://y" in repaired.places[0].source_urls
    # draft-level union
    assert "c_001" in repaired.used_chunk_ids
    assert "https://x" in repaired.source_urls
    assert report.fixed_count >= 4


def test_mission_type_alias_normalised():
    # Pydantic Literal would normally reject "PHOTO". Use model_construct to
    # simulate an LLM payload that has already bypassed validation (e.g. via a
    # future schema change) so we can prove the repairer handles it.
    bad_mission = MissionDraft.model_construct(
        mission_title="t", mission_instruction="x" * 50,
        mission_type="PHOTO", related_place_name="첨성대",
        related_chunk_ids=[], mission_keywords=[],
    )
    p = PlaceStoryDraft(
        sequence=1, place_name="첨성대", story_fragment="x" * 100,
        mission=bad_mission,
    )
    p2 = PlaceStoryDraft(sequence=2, place_name="동궁과월지", story_fragment="x" * 100)
    draft = _draft([p, p2])
    repaired, report = StoryQualityRepairer().repair_intro_draft(
        draft, _request(), StoryQualityReport()
    )
    assert repaired.places[0].mission.mission_type == "photo"
    assert report.fixed_count >= 1


def test_repairer_does_not_rewrite_prose():
    p = PlaceStoryDraft(
        sequence=1, place_name="첨성대", story_fragment="원문 그대로 보존되어야 합니다." * 5,
        mission=MissionDraft(
            mission_title="원문 미션", mission_instruction="원문 instruction." * 4,
            mission_type="observation", related_place_name="첨성대",
        ),
    )
    p2 = PlaceStoryDraft(sequence=2, place_name="동궁과월지", story_fragment="x" * 100)
    draft = _draft([p, p2])
    repaired, _ = StoryQualityRepairer().repair_intro_draft(draft, _request(), StoryQualityReport())
    assert repaired.places[0].story_fragment == p.story_fragment
    assert repaired.places[0].mission.mission_title == "원문 미션"
