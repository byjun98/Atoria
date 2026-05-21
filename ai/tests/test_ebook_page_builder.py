"""Tests for EbookPageBuilder."""
from __future__ import annotations

from app.schemas.ebook_schema import (
    EbookDraftGenerationRequest,
    EbookMissionInput,
    EbookMissionResultInput,
    EbookPlaceInput,
    EbookStorySourceInput,
    EbookUserProfile,
)
from app.services.ebook.ebook_page_builder import EbookPageBuilder


def _request(**overrides):
    base = dict(
        user_profile=EbookUserProfile(nickname="민준", story_theme="time_traveler"),
        places=[
            EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1,
                            address="경상북도 경주시 인왕동"),
            EbookPlaceInput(place_id=2, place_name="동궁과월지", sequence=2),
        ],
        story_source=EbookStorySourceInput(
            intro="여행을 시작합니다.", outro="여행을 마칩니다.",
            missions=[EbookMissionInput(
                sequence=1, title="첨성대 별 찾기",
                description="둘레를 걸어 보세요.",
                type="ACTION", story="첨성대는 신라의 천문 관측 시설.",
            )],
        ),
    )
    base.update(overrides)
    return EbookDraftGenerationRequest(**base)


def test_cover_uses_personalized_title():
    cover = EbookPageBuilder().build_cover(_request())
    assert "민준" in cover.title
    assert cover.author_label and "민준" in cover.author_label
    assert cover.image_prompt and "동화책 삽화" in cover.image_prompt


def test_table_of_contents_lists_places_in_sequence():
    page = EbookPageBuilder().build_table_of_contents_page(
        _request().places, page_number=2
    )
    assert page.page_type == "table_of_contents"
    assert "1. 첨성대" in page.body
    assert "2. 동궁과월지" in page.body


def test_prologue_uses_intro_text():
    page = EbookPageBuilder().build_prologue_page("프롤로그입니다.", page_number=3)
    assert page.page_type == "prologue"
    assert page.body == "프롤로그입니다."


def test_place_story_page_carries_photo_urls_and_storybook_body():
    """MVP — user_answer / selected_keywords 는 본문에 노출되지 않는다.
    사진 URL 만 image_urls 로 흘러가고, body 는 이야기책 톤."""
    req = _request()
    place = req.places[0]
    mission = req.story_source.missions[0]
    result = EbookMissionResultInput(
        sequence=1, place_name="첨성대", completed=True,
        photo_urls=["https://x/p.jpg"],
    )
    page = EbookPageBuilder().build_place_story_page(
        place=place, mission=mission, mission_result=result, page_number=4,
    )
    assert page.page_type == "place_story"
    assert page.title == "첨성대 별 찾기"
    assert page.subtitle == "경상북도 경주시 인왕동"
    assert page.image_urls == ["https://x/p.jpg"]
    # story_content 는 살아 있어야 함
    assert mission.story.strip() in page.body
    # 결과 보고식 표현 모두 금지
    for forbidden in (
        "기록했습니다", "선택했습니다", "완료했습니다", "함께 고른 키워드",
        "오늘의 미션", "미션 안내", "사진 1장이 함께 남았습니다",
    ):
        assert forbidden not in page.body


def test_place_story_page_without_mission_falls_back_to_place_name():
    req = _request()
    page = EbookPageBuilder().build_place_story_page(
        place=req.places[0], mission=None, mission_result=None, page_number=4,
    )
    assert page.title == "첨성대 이야기"


def test_long_body_truncated_to_max_chars():
    builder = EbookPageBuilder(max_page_body_chars=200)
    long_text = "가" * 1000
    page = builder.build_prologue_page(long_text, page_number=3)
    assert len(page.body) <= 200
    assert page.body.endswith("…(이하 생략)")
    assert page.metadata.get("truncated") is True


def test_sources_page_dedupes_urls():
    page = EbookPageBuilder().build_sources_page(
        ["https://x/1", "https://x/2", "https://x/1"], page_number=9,
    )
    assert page.page_type == "sources"
    assert page.source_urls == ["https://x/1", "https://x/2"]
    assert "[1] https://x/1" in page.body
    assert "[2] https://x/2" in page.body


def test_sources_page_with_no_urls_still_renders():
    page = EbookPageBuilder().build_sources_page([], page_number=9)
    assert page.source_urls == []
    assert "기록되지 않았습니다" in page.body


def test_mission_result_page_records_keywords_and_photos():
    req = _request()
    place = req.places[0]
    mission = req.story_source.missions[0]
    result = EbookMissionResultInput(
        sequence=1, place_name="첨성대", completed=True,
        selected_keywords=["별", "창", "돌"],
        photo_urls=["https://x/a.jpg", "https://x/b.jpg"],
    )
    page = EbookPageBuilder().build_mission_result_page(
        place=place, mission=mission, mission_result=result, page_number=5,
    )
    assert page.page_type == "mission_result"
    assert "별, 창, 돌" in page.body
    assert "사진 2장" in page.body
    assert page.image_urls == ["https://x/a.jpg", "https://x/b.jpg"]


def test_epilogue_page_lists_places():
    page = EbookPageBuilder().build_epilogue_page(
        outro="여정의 끝.", place_names=["첨성대", "동궁과월지"], page_number=10,
    )
    assert page.page_type == "epilogue"
    assert "첨성대" in page.body and "동궁과월지" in page.body


# ---- Korean particle / sentence-quality tests --------------------------


def test_cover_author_label_uses_natural_korean():
    """이전 버그: '민준와(과) 가족' → 이제 '민준 가족'."""
    cover = EbookPageBuilder().build_cover(_request())
    assert "와(과)" not in cover.author_label
    assert "민준 가족" in cover.author_label


def test_cover_image_prompt_uses_natural_kw_josa():
    cover = EbookPageBuilder().build_cover(_request())
    assert cover.image_prompt is not None
    assert "와(과)" not in cover.image_prompt
    # "민준과 가족" (받침 X → '과') 표현
    assert "민준과" in cover.image_prompt or "민준 가족" in cover.image_prompt


def test_place_story_body_no_double_eseo():
    req = _request()
    place = req.places[0]
    mission = req.story_source.missions[0]
    result = EbookMissionResultInput(
        sequence=1, place_name="첨성대", completed=True,
        photo_urls=["https://x/p.jpg"],
        metadata={"nickname": "민준"},
    )
    page = EbookPageBuilder().build_place_story_page(
        place=place, mission=mission, mission_result=result, page_number=4,
    )
    assert "에서에서" not in page.body  # ← 회귀 가드


def test_place_story_uses_storybook_tone_not_mission_report():
    """MVP: E-book 은 미션 보고서가 아니라 이야기책. mission_description /
    '미션' / '기록했습니다' / '함께 고른 키워드' / '사진 N장' 같은 표현 금지."""
    req = _request()
    place = req.places[0]
    mission = req.story_source.missions[0]
    page = EbookPageBuilder().build_place_story_page(
        place=place, mission=mission, mission_result=None, page_number=4,
    )
    # MVP 정책 — 본문에 '미션 / 미션 안내 / 오늘의 미션' 어구 자체 금지
    for forbidden in (
        "미션 안내:", "오늘의 미션", "이곳에서의 미션",
        "기록했습니다", "선택했습니다", "완료했습니다",
        "함께 고른 키워드", "사진 1장이 함께 남았습니다",
        "보세요는 것입니다", "하세요는 것입니다",
    ):
        assert forbidden not in page.body, f"forbidden phrase: {forbidden}"
    # mission.description 원문이 본문에 직접 들어가지 않아야 함 (이야기책)
    assert mission.description.strip() not in page.body
    # story_content 는 1문단으로 살아 있어야 함
    assert mission.story.strip() in page.body


def test_incomplete_result_not_treated_as_success():
    req = _request()
    place = req.places[0]
    mission = req.story_source.missions[0]
    incomplete = EbookMissionResultInput(
        sequence=1, place_name="첨성대", completed=False,
        # no user_answer / no keywords
    )
    page = EbookPageBuilder().build_place_story_page(
        place=place, mission=mission, mission_result=incomplete, page_number=4,
    )
    assert "완료했습니다" not in page.body
    assert page.metadata.get("mission_result_status") == "incomplete"


def test_missing_result_status_in_metadata():
    req = _request()
    page = EbookPageBuilder().build_place_story_page(
        place=req.places[1], mission=None, mission_result=None, page_number=5,
    )
    assert page.metadata.get("mission_result_status") == "missing"


# ---- MVP 이야기책 톤: mission/keywords 어구 절대 금지 ---------------------


def test_mission_description_not_inserted_into_body():
    """MVP 정책: mission.description 은 앱 진행용 데이터일 뿐,
    E-book 본문에 직접 노출되지 않는다."""
    req = _request()
    place = req.places[0]
    long_instr = (
        "첨성대의 둥근 몸체를 따라 돌면서 남쪽으로 난 창을 찾아보세요. "
        "그 창이 하늘과 어떻게 연결되어 있는지 생각해 보고, 신라인들이 별에서 "
        "어떤 메시지를 읽으려 했는지 가족과 이야기해 보세요."
    )
    mission = type(req.story_source.missions[0])(
        sequence=1, title="첨성대 별 찾기",
        description=long_instr,
        type="ACTION",
        story="첨성대는 신라의 천문 관측 시설.",
    )
    page = EbookPageBuilder().build_place_story_page(
        place=place, mission=mission, mission_result=None, page_number=4,
    )
    # mission.description 본문 미노출 + mission/오늘의 미션 어구 금지
    assert long_instr not in page.body
    for forbidden in (
        "오늘의 미션", "이곳에서의 미션", "미션 안내",
        "보세요는 것입니다", "하세요는 것입니다", "해 보세요는 것입니다",
    ):
        assert forbidden not in page.body
    # story 는 살아 있어야 함
    assert mission.story.strip() in page.body


def test_photo_paragraph_added_when_photo_urls_present():
    """사진이 있을 때만 사진을 자연스럽게 언급하는 한 줄 추가.
    '사진 N장이 함께 남았습니다 / 기록했습니다 / 함께 고른 키워드' 같은 결과
    보고식 표현은 절대 사용하지 않는다."""
    req = _request()
    place = req.places[0]
    mission = req.story_source.missions[0]
    result = EbookMissionResultInput(
        sequence=1, place_name="첨성대", completed=True,
        photo_urls=["https://x/p.jpg"],
        metadata={"nickname": "민준", "wire_mission_type": "PHOTO"},
    )
    page = EbookPageBuilder().build_place_story_page(
        place=place, mission=mission, mission_result=result, page_number=4,
    )
    # 사진을 자연스럽게 언급하는 새 표현 (한 가지 이상)
    assert ("사진으로 담았습니다" in page.body) or ("사진으로 남아" in page.body)
    # 결과 보고식 표현은 모두 금지
    for forbidden in (
        "사진 1장이 함께 남았습니다", "사진 1장도 이 페이지에 함께 남았습니다",
        "기록했습니다", "선택했습니다", "완료했습니다",
        "함께 고른 키워드",
    ):
        assert forbidden not in page.body
    # 중복 종결 회귀 가드
    assert "입니다.입니다" not in page.body


def test_no_photo_paragraph_when_photo_urls_absent():
    """사진이 없으면 사진 관련 문장을 넣지 않는다."""
    req = _request()
    place = req.places[0]
    mission = req.story_source.missions[0]
    result = EbookMissionResultInput(
        sequence=1, place_name="첨성대", completed=True,
        photo_urls=[],   # 사진 없음
        metadata={"nickname": "민준", "wire_mission_type": "PHOTO"},
    )
    page = EbookPageBuilder().build_place_story_page(
        place=place, mission=mission, mission_result=result, page_number=4,
    )
    assert "사진" not in page.body
    # body 는 mission.story 만 들어감
    assert page.body.strip() == mission.story.strip()
