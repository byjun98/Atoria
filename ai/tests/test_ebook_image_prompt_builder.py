"""Tests for EbookImagePromptBuilder."""
from __future__ import annotations

from app.schemas.ebook_schema import (
    EbookDraftGenerationRequest,
    EbookMissionInput,
    EbookMissionResultInput,
    EbookPlaceInput,
    EbookStorySourceInput,
    EbookUserProfile,
)
from app.services.ebook.ebook_image_prompt_builder import EbookImagePromptBuilder


def _req():
    return EbookDraftGenerationRequest(
        user_profile=EbookUserProfile(nickname="민준", story_theme="time_traveler"),
        places=[
            EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1),
            EbookPlaceInput(place_id=2, place_name="동궁과월지", sequence=2),
        ],
        story_source=EbookStorySourceInput(
            intro="i", outro="o",
            missions=[EbookMissionInput(
                sequence=1, title="첨성대 남쪽 창이 바라보는 하늘 찾기",
                description="d", type="ACTION", story="s",
            )],
        ),
    )


def test_cover_prompt_contains_nickname_theme_and_place():
    out = EbookImagePromptBuilder().build_cover_image_prompt(_req())
    assert "민준" in out
    assert "time_traveler" in out
    assert "첨성대" in out


def test_cover_prompt_uses_warm_family_style():
    out = EbookImagePromptBuilder().build_cover_image_prompt(_req())
    assert "동화책 삽화" in out
    assert "가족 친화적" in out


def test_place_prompt_contains_place_name():
    place = EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1)
    mission = EbookMissionInput(
        sequence=1, title="첨성대 남쪽 창이 바라보는 하늘 찾기",
        description="d", type="ACTION", story="s",
    )
    out = EbookImagePromptBuilder().build_place_page_image_prompt(place, mission, None)
    assert "첨성대" in out


def test_place_prompt_with_user_photo_does_not_describe_real_person():
    place = EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1)
    mission = EbookMissionInput(
        sequence=1, title="첨성대 별 찾기",
        description="d", type="PHOTO", story="s",
    )
    result = EbookMissionResultInput(
        sequence=1, place_name="첨성대",
        photo_urls=["https://x/p.jpg"], completed=True,
    )
    out = EbookImagePromptBuilder().build_place_page_image_prompt(place, mission, result)
    assert "사용자가 남긴 사진을 배치할 영역" in out
    assert "실제 인물 묘사는 피하고" in out


def test_place_prompt_blocks_violent_or_scary():
    place = EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1)
    out = EbookImagePromptBuilder().build_place_page_image_prompt(place, None, None)
    assert "공포·폭력 표현 금지" in out


# ---- visual keyword extraction & quality -------------------------------


def test_selected_keywords_propagate_to_prompt():
    place = EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1)
    mission = EbookMissionInput(
        sequence=1, title="첨성대 남쪽 창이 바라보는 하늘 찾기",
        description="첨성대의 둥근 돌단과 남쪽 창을 살펴보세요.",
        type="ACTION", story="s",
    )
    result = EbookMissionResultInput(
        sequence=1, place_name="첨성대", completed=True,
        selected_keywords=["별", "창", "돌"],
    )
    out = EbookImagePromptBuilder().build_place_page_image_prompt(place, mission, result)
    # selected_keywords 가 시각 키워드에 모두 들어가야 함
    assert "별" in out and "창" in out and "돌" in out


def test_mission_title_not_inserted_verbatim_in_focus():
    place = EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1)
    mission = EbookMissionInput(
        sequence=1, title="첨성대 남쪽 창이 바라보는 하늘 찾기",
        description="첨성대 둘레를 걸어 보세요.",
        type="ACTION", story="s",
    )
    out = EbookImagePromptBuilder().build_place_page_image_prompt(place, mission, None)
    # mission_title 전체가 괄호로 그대로 들어가는 옛 패턴 금지
    assert "(남쪽 창이 바라보는 하늘 찾기)" not in out
    assert "핵심 요소(" not in out
    # 핵심 명사는 살아 있어야 함
    assert "남쪽" in out or "하늘" in out


def test_donggung_wolji_keywords_extracted():
    place = EbookPlaceInput(place_id=2, place_name="동궁과월지", sequence=2)
    mission = EbookMissionInput(
        sequence=2,
        title="월지에 비친 누각의 그림자 포착하기",
        description="월지의 가장자리를 따라 걸으며 물에 비치는 누각의 그림자를 사진으로 남겨 보세요.",
        type="PHOTO", story="s",
    )
    out = EbookImagePromptBuilder().build_place_page_image_prompt(place, mission, None)
    # 월지 / 누각 / 그림자 중 최소 2개 이상이 있어야 함
    hits = sum(1 for k in ("월지", "누각", "그림자", "연못") if k in out)
    assert hits >= 2


def test_place_prompt_uses_natural_eul_reul_not_paren_form():
    """회귀 가드: '첨성대을(를)' / '월지을(를)' 같은 병기 표현 금지.

    참고: '첨성대' 와 '월지' 는 모두 마지막 글자에 받침이 없으므로 둘 다 '를' 이 붙는다.
    받침 있는 케이스(에밀레종) 도 함께 검증한다.
    """
    place_no_jong_1 = EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1)  # 대 → 받침 X → 를
    place_no_jong_2 = EbookPlaceInput(place_id=2, place_name="월지", sequence=1)    # 지 → 받침 X → 를
    place_with_jong = EbookPlaceInput(place_id=3, place_name="에밀레종", sequence=1)  # 종 → 받침 O → 을
    builder = EbookImagePromptBuilder()
    out_no_jong_1 = builder.build_place_page_image_prompt(place_no_jong_1, None, None)
    out_no_jong_2 = builder.build_place_page_image_prompt(place_no_jong_2, None, None)
    out_with_jong = builder.build_place_page_image_prompt(place_with_jong, None, None)
    # 병기 표현 금지
    assert "을(를)" not in out_no_jong_1
    assert "을(를)" not in out_no_jong_2
    assert "을(를)" not in out_with_jong
    # 받침 없는 장소명 → '를'
    assert "첨성대를 중심으로" in out_no_jong_1
    assert "월지를 중심으로" in out_no_jong_2
    # 받침 있는 장소명 → '을'
    assert "에밀레종을 중심으로" in out_with_jong


def test_visual_keywords_drop_stopwords():
    place = EbookPlaceInput(place_id=1, place_name="첨성대", sequence=1)
    mission = EbookMissionInput(
        sequence=1, title="첨성대 둘레 따라 걷기 미션",
        description="첨성대 주변을 가족과 함께 둘러보세요.",
        type="ACTION", story="s",
    )
    kws = EbookImagePromptBuilder().extract_visual_keywords(place, mission, None)
    assert "첨성대" in kws
    for stop in ("미션", "가족", "함께", "주변", "둘러보세요", "보세요"):
        assert stop not in kws
