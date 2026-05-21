"""Tests for EbookJobAdapter — wire ↔ 156 internal conversion (157)."""
from __future__ import annotations

from app.schemas.ebook_schema import (
    EbookCoverDraft,
    EbookDraft,
    EbookPageDraft,
)
from app.schemas.story import (
    ChapterUserResult,
    EbookChapter,
    EbookJobRequest,
    PersonInfo,
    ProtagonistInfo,
    StoryBlock,
)
from app.services.ebook.ebook_job_adapter import EbookJobAdapter


def _photo_chapter():
    return EbookChapter(
        sequence=1, place_id=301, place_name="첨성대", place_address="경북 경주시",
        mission_title="별빛 관측소에서 단서 찾기",
        mission_description="첨성대 앞에서 별자리 사진.",
        mission_type="PHOTO",
        story_content="첨성대에 도착해 별을 살폈습니다.",
        user_result=ChapterUserResult(image_url="https://x/p.jpg", choice=None),
    )


def _choice_chapter():
    return EbookChapter(
        sequence=2, place_id=302, place_name="불국사", place_address="경북 경주시",
        mission_title="불국사의 비밀을 선택하라",
        mission_description="두루마리 앞에서 선택.",
        mission_type="CHOICE",
        story_content="두루마리를 골랐습니다.",
        user_result=ChapterUserResult(image_url=None, choice="이상 세계."),
    )


def _wire_request(chapters=None):
    return EbookJobRequest(
        story_id=301, user_id=1,
        story=StoryBlock(
            title="경주의 비밀",
            intro="두 아이가 여행을 시작합니다.",
            outro="아이들은 기억을 마음에 담았습니다.",
            protagonist_info=ProtagonistInfo(
                people_cnt=2,
                people_information=[
                    PersonInfo(name="민준", age=5, tendency="모험적"),
                    PersonInfo(name="성준", age=6, tendency="모험적"),
                ],
            ),
        ),
        chapters=chapters or [_photo_chapter(), _choice_chapter()],
    )


# ---- request → draft ---------------------------------------------------


def test_wire_request_converts_to_draft_request():
    req = _wire_request()
    draft_req = EbookJobAdapter().to_draft_request(req)
    assert draft_req.user_profile.nickname == "민준"
    assert draft_req.user_profile.age_group == "child"  # 5세 최소
    assert draft_req.user_profile.companion_type == "family"
    assert [p.place_name for p in draft_req.places] == ["첨성대", "불국사"]
    assert [m.title for m in draft_req.story_source.missions] == [
        "별빛 관측소에서 단서 찾기", "불국사의 비밀을 선택하라",
    ]


def test_chapters_user_result_image_becomes_photo_url():
    req = _wire_request()
    draft_req = EbookJobAdapter().to_draft_request(req)
    photo_result = next(r for r in draft_req.mission_results if r.sequence == 1)
    assert photo_result.photo_urls == ["https://x/p.jpg"]
    assert photo_result.completed is True


def test_choice_only_chapter_produces_no_mission_result_in_mvp():
    """MVP 정책: 미션은 PHOTO 만 사용. choice 만 있고 image_url 이 없으면
    mission_result 자체가 만들어지지 않는다 — 이야기책에 결과 보고가 끼어들지
    않도록 보장."""
    req = _wire_request()
    draft_req = EbookJobAdapter().to_draft_request(req)
    sequences_with_result = {r.sequence for r in draft_req.mission_results}
    # sequence 2 (choice-only chapter) 는 mission_result 가 없어야 한다
    assert 2 not in sequences_with_result
    # sequence 1 (PHOTO) 는 정상 처리
    assert 1 in sequences_with_result


def test_photo_chapter_maps_image_url_only_no_user_answer_no_keywords():
    """user_result.image_url 만 photo_urls 로 흘리고, user_answer / selected_keywords
    어디로도 사용자 입력을 흘리지 않는다."""
    req = _wire_request()
    draft_req = EbookJobAdapter().to_draft_request(req)
    photo_result = next(r for r in draft_req.mission_results if r.sequence == 1)
    assert photo_result.photo_urls == ["https://x/p.jpg"]
    assert photo_result.user_answer is None         # ← 회귀 가드
    assert photo_result.selected_keywords == []     # ← 회귀 가드
    assert photo_result.completed is True
    assert photo_result.metadata.get("wire_mission_type") == "PHOTO"


def test_chapter_with_no_user_result_has_no_mission_result():
    empty = EbookChapter(
        sequence=3, place_id=303, place_name="석굴암", place_address="경북",
        mission_title="t", mission_description="d", mission_type="QUIZ",
        story_content="s",
        user_result=ChapterUserResult(image_url=None, choice=None),
    )
    req = _wire_request(chapters=[_photo_chapter(), _choice_chapter(), empty])
    draft_req = EbookJobAdapter().to_draft_request(req)
    sequences = {r.sequence for r in draft_req.mission_results}
    assert 3 not in sequences  # no result for empty user_result


# ---- draft → wire response ---------------------------------------------


def _fake_draft():
    """Mimic what EbookDraftService would return for the 2-chapter request."""
    return EbookDraft(
        draft_id="d-1",
        title="경주의 비밀",
        subtitle="민준 가족의 경주 탐험 이야기",
        cover=EbookCoverDraft(
            title="경주의 비밀", subtitle=None,
            author_label="민준 가족의 경주 이야기",
            theme=None, image_prompt="동화책 표지 prompt",
        ),
        pages=[
            EbookPageDraft(page_number=1, page_type="cover",
                           title="경주의 비밀"),
            EbookPageDraft(page_number=2, page_type="prologue",
                           title="여행의 시작", body="두 아이가 여행을 시작합니다."),
            EbookPageDraft(page_number=3, page_type="place_story",
                           title="별빛 관측소에서 단서 찾기",
                           place_name="첨성대",
                           subtitle="경북 경주시",
                           body="첨성대에 도착했습니다."),
            EbookPageDraft(page_number=4, page_type="place_story",
                           title="불국사의 비밀을 선택하라",
                           place_name="불국사",
                           subtitle="경북 경주시",
                           body="두루마리를 골랐습니다."),
            # internal-only pages that must be filtered out
            EbookPageDraft(page_number=5, page_type="table_of_contents",
                           title="여행의 차례", body="..."),
            EbookPageDraft(page_number=6, page_type="mission_result",
                           title="첨성대에서 남긴 기록", body="..."),
            EbookPageDraft(page_number=7, page_type="sources",
                           title="참고 자료", body="..."),
            EbookPageDraft(page_number=8, page_type="epilogue",
                           title="여행을 마치며", body="아이들은 기억을 담았습니다."),
        ],
        page_count=8,
    )


def test_wire_response_skips_internal_only_page_types():
    data = EbookJobAdapter().to_ebook_content(
        story_id=301, request=_wire_request(), draft=_fake_draft(),
    )
    types = [p.type for p in data.ebook_content.pages]
    # COVER + INTRO + 2 CHAPTER + OUTRO + BACK_COVER (auto)
    assert types == ["COVER", "INTRO", "CHAPTER", "CHAPTER", "OUTRO", "BACK_COVER"]
    # 내부 전용 page_type 은 절대 외부 enum 에 없어야 함
    for t in types:
        assert t in {"COVER", "INTRO", "CHAPTER", "OUTRO", "BACK_COVER"}


# ---- storybook chapter title -------------------------------------------


def test_storybook_chapter_title_not_mission_title():
    """CHAPTER page title 은 wire mission_title('별빛 관측소에서 단서 찾기' 등)
    을 그대로 노출하지 않고 이야기책 톤으로 합성한다."""
    data = EbookJobAdapter().to_ebook_content(
        story_id=301, request=_wire_request(), draft=_fake_draft(),
    )
    chapter_pages = [p for p in data.ebook_content.pages if p.type == "CHAPTER"]
    assert len(chapter_pages) == 2

    # 1번째 chapter
    p1 = chapter_pages[0]
    assert p1.title.startswith("첫 번째 이야기, ")
    assert "첨성대의 별빛" in p1.title

    # 2번째 chapter (불국사)
    p2 = chapter_pages[1]
    assert p2.title.startswith("두 번째 이야기, ")


def test_chapter_title_excludes_mission_form_words():
    """CHAPTER title 에 '미션 / 선택하라 / 찾기 / 포착하기' 같은 미션형 표현
    노출 금지."""
    data = EbookJobAdapter().to_ebook_content(
        story_id=301, request=_wire_request(), draft=_fake_draft(),
    )
    for page in data.ebook_content.pages:
        if page.type != "CHAPTER":
            continue
        for forbidden in ("미션", "선택하라", "찾기", "포착하기", "조사", "탐험"):
            assert forbidden not in page.title, (
                f"chapter title leaks mission-form word '{forbidden}': {page.title!r}"
            )


def test_chapter_title_does_not_contain_request_mission_titles():
    """wire mission_title 문자열이 chapter title 안에 그대로 들어가지 않아야 한다."""
    req = _wire_request()
    data = EbookJobAdapter().to_ebook_content(
        story_id=301, request=req, draft=_fake_draft(),
    )
    request_mission_titles = [c.mission_title for c in req.chapters]
    for page in data.ebook_content.pages:
        if page.type != "CHAPTER":
            continue
        for mt in request_mission_titles:
            assert mt not in page.title


def test_storybook_chapter_title_helper_examples():
    """compose_storybook_chapter_title 헬퍼 직접 케이스 — spec 예시들."""
    from app.services.ebook.ebook_job_adapter import compose_storybook_chapter_title
    assert compose_storybook_chapter_title(1, "첨성대") == "첫 번째 이야기, 첨성대의 별빛"
    assert compose_storybook_chapter_title(2, "동궁과월지") == "두 번째 이야기, 월지에 비친 달빛"
    assert compose_storybook_chapter_title(2, "월지") == "두 번째 이야기, 월지에 비친 달빛"
    # ordinal fallback (11 이상) — "{N}번째 이야기, ..."
    assert compose_storybook_chapter_title(12, "첨성대").startswith("12번째 이야기, ")
    # suffix fallback (사전에 없는 장소) — 장소명만
    assert compose_storybook_chapter_title(1, "알수없는장소") == "첫 번째 이야기, 알수없는장소"
    # 부분 매칭 — '불국사 다보탑' → 다보탑 매핑 사용
    assert "다보의 빛" in compose_storybook_chapter_title(3, "불국사 다보탑")


def test_chapter_layout_only_image_or_text_only_in_mvp():
    """MVP — TEXT_WITH_QUOTE / quote 어떠한 응답에도 사용하지 않는다."""
    data = EbookJobAdapter().to_ebook_content(
        story_id=301, request=_wire_request(), draft=_fake_draft(),
    )
    photo_page = next(p for p in data.ebook_content.pages if p.sequence == 1)
    choice_page = next(p for p in data.ebook_content.pages if p.sequence == 2)
    # PHOTO chapter — IMAGE_TOP_TEXT_BOTTOM 유지
    assert photo_page.layout == "IMAGE_TOP_TEXT_BOTTOM"
    assert photo_page.image_url == "https://x/p.jpg"
    assert photo_page.caption == "첨성대에서 남긴 여행의 한 장면"
    assert photo_page.quote is None
    # CHOICE chapter — image_url 도 quote 도 없으니 TEXT_ONLY 로 fallback
    assert choice_page.layout == "TEXT_ONLY"
    assert choice_page.image_url is None
    assert choice_page.quote is None  # ← MVP: choice 는 절대 quote 로 흐르지 않음


def test_chapter_no_image_no_choice_falls_back_to_text_only():
    no_user_result_chapter = EbookChapter(
        sequence=1, place_id=999, place_name="첨성대", place_address="경북",
        mission_title="t", mission_description="d", mission_type="QUIZ",
        story_content="s",
        user_result=ChapterUserResult(image_url=None, choice=None),
    )
    req = _wire_request(chapters=[no_user_result_chapter])
    # synthesize a tiny draft with one place_story page for 첨성대
    draft = EbookDraft(
        draft_id="d", title="t",
        cover=EbookCoverDraft(title="t"),
        pages=[
            EbookPageDraft(page_number=1, page_type="cover", title="t"),
            EbookPageDraft(page_number=2, page_type="prologue", title="i", body="i"),
            EbookPageDraft(page_number=3, page_type="place_story",
                           title="t", place_name="첨성대", body="b"),
            EbookPageDraft(page_number=4, page_type="epilogue", title="o", body="o"),
        ],
        page_count=4,
    )
    data = EbookJobAdapter().to_ebook_content(
        story_id=req.story_id, request=req, draft=draft,
    )
    chap = next(p for p in data.ebook_content.pages if p.type == "CHAPTER")
    assert chap.layout == "TEXT_ONLY"
    assert chap.image_url is None
    assert chap.quote is None


def test_back_cover_always_appended():
    data = EbookJobAdapter().to_ebook_content(
        story_id=301, request=_wire_request(), draft=_fake_draft(),
    )
    last = data.ebook_content.pages[-1]
    assert last.type == "BACK_COVER"
    assert last.layout == "BACK_COVER"
    assert "첨성대" in (last.text or "")
    assert "불국사" in (last.text or "")


def test_meta_page_count_matches_wire_pages_length():
    data = EbookJobAdapter().to_ebook_content(
        story_id=301, request=_wire_request(), draft=_fake_draft(),
    )
    assert data.ebook_content.meta.page_count == len(data.ebook_content.pages)


def test_meta_title_and_author_from_wire_request():
    data = EbookJobAdapter().to_ebook_content(
        story_id=301, request=_wire_request(), draft=_fake_draft(),
    )
    assert data.ebook_content.meta.title == "경주의 비밀"
    assert "민준" in data.ebook_content.meta.author
    assert "성준" in data.ebook_content.meta.author
    assert data.ebook_content.meta.language == "ko"


def test_cover_uses_wire_title_and_default_background():
    data = EbookJobAdapter().to_ebook_content(
        story_id=301, request=_wire_request(), draft=_fake_draft(),
    )
    assert data.ebook_content.cover.title == "경주의 비밀"
    assert data.ebook_content.cover.background_color == "#F5E6D3"
    assert data.ebook_content.cover.thumbnail_hint  # non-empty
