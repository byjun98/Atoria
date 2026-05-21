"""Unit tests for StoryQualityValidator."""
from __future__ import annotations

import pytest

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
from app.services.story.story_quality_validator import StoryQualityValidator


def _request(places=None, rag_contexts=None):
    return StoryIntroGenerationRequest(
        user_profile=StoryUserProfile(persona="curious"),
        places=places or [
            CoursePlaceInput(place_id="1", place_name="첨성대", sequence=1),
            CoursePlaceInput(place_id="2", place_name="동궁과월지", sequence=2),
        ],
        rag_contexts=rag_contexts or [],
    )


def _good_place(seq=1, name="첨성대", *, mission_type="observation",
                story=None, mission_instr=None,
                used_chunk_ids=None, source_urls=None):
    story = story or (
        f"{name} 앞에 도착한 두 형제는 별이 그려진 옛 돌의 방향을 관찰하며 "
        f"오래된 천문 관측대를 상상해 보았어요. 오늘은 무엇을 발견할 수 있을까요?"
    )
    instr = mission_instr or (
        f"{name}에서 돌의 모양과 방향을 찾아요. 두 단서가 한 화면에 보이면 "
        "클리어이고, 사진은 다음 별길 단서가 됩니다."
    )
    return PlaceStoryDraft(
        sequence=seq,
        place_id=str(seq),
        place_name=name,
        story_fragment=story,
        mission=MissionDraft(
            mission_title=f"{name}의 별 관찰",
            mission_instruction=instr,
            mission_type=mission_type,
            verification_hint=f"{name}의 돌 모양과 방향이 한 화면에 보이면 클리어입니다.",
            related_place_name=name,
            mission_keywords=["돌", "방향"],
        ),
        used_chunk_ids=used_chunk_ids or ["c_001"],
        source_urls=source_urls or ["https://x"],
    )


_DEFAULT_INTRO = (
    "오늘 두 형제는 경주의 오래된 별빛 길을 따라 걸으며 신라의 흔적을 찾아갑니다. "
    "곳곳에 숨은 단서를 함께 만나 보아요."
)
_DEFAULT_OUTRO = "여정의 끝에 두 형제는 오늘 만난 장면들을 마음에 담고 돌아왔습니다."
_SENTINEL = object()


def _good_draft(places=None, intro=_SENTINEL, outro=_SENTINEL,
                used_chunk_ids=_SENTINEL, source_urls=_SENTINEL):
    return StoryIntroDraft(
        title="별을 읽는 여정",
        intro=_DEFAULT_INTRO if intro is _SENTINEL else intro,
        places=places or [_good_place(1, "첨성대"), _good_place(2, "동궁과월지")],
        outro=_DEFAULT_OUTRO if outro is _SENTINEL else outro,
        used_chunk_ids=["c_001"] if used_chunk_ids is _SENTINEL else used_chunk_ids,
        source_urls=["https://x"] if source_urls is _SENTINEL else source_urls,
    )


def test_clean_draft_passes():
    report = StoryQualityValidator().validate_intro_draft(_good_draft(), _request())
    # may have warnings (e.g. no RAG context provided), but no errors
    assert not report.has_errors()


def test_empty_intro_is_error():
    draft = _good_draft(intro="   ")
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "EMPTY_INTRO" and i.severity == "error" for i in report.issues)


def test_empty_outro_is_error():
    draft = _good_draft(outro="")
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "EMPTY_OUTRO" and i.severity == "error" for i in report.issues)


def test_place_count_mismatch_is_error():
    draft = _good_draft(places=[_good_place(1, "첨성대")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "PLACE_COUNT_MISMATCH" for i in report.issues)


def test_missing_mission_is_error():
    p = _good_place(1, "첨성대")
    p.mission = None
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "MISSING_MISSION" for i in report.issues)


def test_invalid_mission_type_is_error():
    p = _good_place(1, "첨성대")
    # bypass schema by direct attr write
    p.mission.mission_type = "BOGUS"  # type: ignore[assignment]
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "MISSION_TYPE_INVALID" for i in report.issues)


def test_short_story_fragment_is_error():
    p = _good_place(1, "첨성대", story="짧다")
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "PLACE_STORY_TOO_SHORT" and i.severity == "error"
               for i in report.issues)


def test_short_mission_description_is_warning():
    p = _good_place(1, "첨성대", mission_instr="관찰하세요.")
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "MISSION_DESCRIPTION_TOO_SHORT" for i in report.issues)


def test_photo_afterthought_phrase_is_error():
    p = _good_place(
        1,
        "첨성대",
        mission_instr=(
            "첨성대에서 네모난 창과 둥근 돌층을 찾아요. 창과 돌층이 한 화면에 "
            "보이면 클리어입니다. 그 모습을 사진으로 찍어보세요."
        ),
    )
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(
        i.code == "MISSION_PHOTO_AFTERTHOUGHT" and i.severity == "error"
        for i in report.issues
    )


def test_missing_photo_clear_condition_is_error():
    p = _good_place(
        1,
        "첨성대",
        mission_instr="첨성대에서 네모난 창을 찾아 둥근 돌층과 비교해 보세요.",
    )
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(
        i.code == "MISSION_CLEAR_CONDITION_MISSING" and i.severity == "error"
        for i in report.issues
    )


def test_seokbinggo_internal_mission_is_error():
    p = _good_place(
        1,
        "석빙고",
        mission_instr=(
            "석빙고 내부 천장과 배수로를 찾아요. 내부 천장과 배수로가 한 화면에 "
            "보이면 클리어입니다."
        ),
    )
    places = [
        CoursePlaceInput(place_id="1", place_name="석빙고", sequence=1),
        CoursePlaceInput(place_id="2", place_name="동궁과월지", sequence=2),
    ]
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request(places=places))
    assert any(
        i.code == "FORBIDDEN_ACCESS_INSTRUCTION" and i.severity == "error"
        for i in report.issues
    )


def test_missing_used_chunk_ids_warning_when_rag_provided():
    rag = [
        RagContextByPlace(
            place_name="첨성대",
            fact_contexts=[RagContextItem(
                chunk_id="c_001", heritage_name="첨성대",
                content_role="fact_context", source_type="x",
                context_text="...", source_urls=["https://x"],
            )],
        )
    ]
    draft = _good_draft(used_chunk_ids=[], source_urls=[])
    draft.places[0].used_chunk_ids = []
    draft.places[0].source_urls = []
    report = StoryQualityValidator().validate_intro_draft(draft, _request(rag_contexts=rag))
    codes = {i.code for i in report.issues}
    assert "MISSING_RAG_USAGE" in codes
    assert "MISSING_SOURCE_TRACE" in codes
    assert "PLACE_USED_CHUNK_MISSING" in codes
    assert "PLACE_SOURCE_URLS_MISSING" in codes


def test_assertive_phrase_in_legend_context_warns():
    rag = [
        RagContextByPlace(
            place_name="첨성대",
            story_materials=[RagContextItem(
                chunk_id="l_001", heritage_name="첨성대",
                content_role="legend_material", source_type="x",
                factuality_level="mixed", context_text="...",
            )],
        )
    ]
    p = _good_place(1, "첨성대", story=(
        "선덕여왕은 첨성대에서 별을 읽으며 백성을 헤아렸다. 실제로 그렇게 했다."
    ))
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request(rag_contexts=rag))
    assert any(i.code == "FACT_LEGEND_CONFUSION" for i in report.issues)


def test_symbolic_without_symbolic_phrase_warns():
    rag = [
        RagContextByPlace(
            place_name="첨성대",
            symbolic_materials=[RagContextItem(
                chunk_id="s_001", heritage_name="첨성대",
                content_role="symbolic_material", source_type="x",
                factuality_level="symbolic", context_text="...",
            )],
        )
    ]
    p = _good_place(1, "첨성대", story=(
        "두 형제는 첨성대 앞에서 돌의 방향을 따라 걸어 보았어요."
    ))
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request(rag_contexts=rag))
    assert any(i.code == "SYMBOLIC_AS_FACT" for i in report.issues)


def test_generic_sentence_warns():
    p = _good_place(1, "첨성대", story=(
        "재미있는 여행을 떠나볼까요. 같이 걸어 봅시다."
    ))
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "STORY_TOO_GENERIC" for i in report.issues)


# ---- 154-prompt-pass: place specificity / generic detection ------------


def _rag_for_cheomseongdae():
    return RagContextByPlace(
        place_name="첨성대",
        fact_contexts=[
            RagContextItem(
                chunk_id="f_001", heritage_name="첨성대",
                content_role="fact_context", source_type="x",
                factuality_level="history",
                context_text="...", motifs=["별", "천문", "선덕여왕"],
                mission_hooks=["남쪽 창에서 하늘 관측 미션"],
            )
        ],
    )


def test_generic_mission_title_warns():
    p = _good_place(
        1, "첨성대",
        story="첨성대 앞에서 별과 천문 관측대를 살피며 신라 사람들의 시선을 따라가 봅니다.",
        mission_instr="첨성대 둘레를 따라 걸으며 별과 남쪽 창의 방향을 관찰해 보세요.",
    )
    p.mission.mission_title = "탐험"  # generic, no place keyword
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(
        draft, _request(rag_contexts=[_rag_for_cheomseongdae()]),
    )
    assert any(i.code == "MISSION_TITLE_TOO_GENERIC" for i in report.issues)


def test_mission_without_place_keywords_warns():
    p = _good_place(
        1, "첨성대",
        story="첨성대 별 천문 관측대 신라 사람들을 떠올립니다." * 2,  # story OK
        mission_instr="현장에서 한 가지 행동을 천천히 해보고 가족과 이야기 나눠 보세요.",
    )
    p.mission.mission_title = "오늘의 한 가지 행동"  # no place keyword
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(
        draft, _request(rag_contexts=[_rag_for_cheomseongdae()]),
    )
    assert any(i.code == "MISSION_NOT_PLACE_SPECIFIC" for i in report.issues)


def test_story_fragment_without_place_keywords_warns():
    p = _good_place(
        1, "첨성대",
        story="옛날에 어떤 사람들이 이곳을 지나갔을까요. 가만히 발걸음을 따라가 봅니다." * 2,
    )
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(
        draft, _request(rag_contexts=[_rag_for_cheomseongdae()]),
    )
    assert any(i.code == "STORY_NOT_PLACE_SPECIFIC" for i in report.issues)


def test_intro_too_generic_warns():
    draft = _good_draft(intro=(
        "탐험할 준비가 되었나요? 옛날 이야기 속으로 들어가 보아요."
    ))
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "INTRO_TOO_GENERIC" for i in report.issues)


def test_outro_too_generic_warns():
    draft = _good_draft(outro=(
        "오늘의 탐험은 여기까지입니다. 다음에 또 만나요."
    ))
    report = StoryQualityValidator().validate_intro_draft(draft, _request())
    assert any(i.code == "OUTRO_TOO_GENERIC" for i in report.issues)


def test_rag_context_underused_warns():
    rag = [
        RagContextByPlace(
            place_name="첨성대",
            fact_contexts=[RagContextItem(
                chunk_id="f1", heritage_name="첨성대",
                content_role="fact_context", source_type="x",
                factuality_level="history", context_text="...",
                motifs=["별", "천문", "선덕여왕"],
            )],
        )
    ]
    # Both places must avoid the RAG motifs (별/천문/선덕여왕) for the global
    # underuse check to fire — including the default place-2 fixture text.
    p1 = _good_place(
        1, "첨성대",
        story="첨성대 앞에서 둥근 돌탑을 천천히 살피고 가족과 함께 걷습니다." * 2,
        mission_instr="첨성대 주변을 따라 걸으며 돌의 모양을 비교해 보세요.",
    )
    p1.mission.mission_title = "첨성대의 돌단 따라 걷기"
    p2 = _good_place(
        2, "동궁과월지",
        story="동궁과월지의 연못 옆을 가족과 함께 천천히 걸어 봅니다." * 2,
        mission_instr="동궁과월지 둘레를 따라 걸으며 연못의 풍경을 사진으로 남겨 보세요.",
    )
    p2.mission.mission_title = "동궁과월지의 연못 풍경 담기"
    draft = _good_draft(
        places=[p1, p2],
        intro="민준과 가족은 첨성대와 동궁과월지를 함께 걸으며 돌과 풍경을 살펴봅니다.",
        outro="첨성대와 동궁과월지를 함께 걸으며 본 풍경을 가족과 나누어 봅니다.",
    )
    report = StoryQualityValidator().validate_intro_draft(
        draft, _request(rag_contexts=rag)
    )
    # RAG motifs (별/천문/선덕여왕) 가 본문에 하나도 안 들어갔는지 확인
    assert any(i.code == "RAG_CONTEXT_UNDERUSED" for i in report.issues)


def test_place_specific_draft_passes_specificity_checks():
    rag = [_rag_for_cheomseongdae()]
    p = _good_place(
        1, "첨성대",
        story=(
            "첨성대는 신라 사람들이 별과 하늘의 움직임을 살피던 천문 관측 시설로 알려져 있습니다. "
            "선덕여왕이 별빛 아래에서 백성을 헤아렸을지도 모른다는 이야기가 전해집니다. "
            "민준은 첨성대의 남쪽 창과 둥근 돌의 단을 살피며 천문을 상상해 봅니다."
        ),
        mission_instr=(
            "첨성대의 둥근 돌단과 남쪽 창을 차례로 찾아요. 남쪽 창과 둥근 돌단이 "
            "한 화면에 보이면 클리어이고, 사진은 별을 읽는 단서가 됩니다."
        ),
    )
    p.mission.mission_title = "첨성대 남쪽 창이 바라보는 별 따라가기"
    draft = _good_draft(places=[p, _good_place(2, "동궁과월지")])
    report = StoryQualityValidator().validate_intro_draft(
        draft, _request(rag_contexts=rag),
    )
    codes = {i.code for i in report.issues}
    assert "MISSION_TITLE_TOO_GENERIC" not in codes
    assert "MISSION_NOT_PLACE_SPECIFIC" not in codes
    assert "STORY_NOT_PLACE_SPECIFIC" not in codes
