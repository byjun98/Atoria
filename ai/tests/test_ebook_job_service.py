"""Tests for EbookJobService — orchestration only (no real OpenAI / DB)."""
from __future__ import annotations

from app.schemas.story import (
    ChapterUserResult,
    EbookChapter,
    EbookJobRequest,
    PersonInfo,
    ProtagonistInfo,
    StoryBlock,
)
from app.services.ebook.ebook_job_service import EbookJobService


def _wire_request():
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
        chapters=[
            EbookChapter(
                sequence=1, place_id=301, place_name="첨성대",
                place_address="경북 경주시",
                mission_title="별빛 관측소에서 단서 찾기",
                mission_description="별자리 사진.", mission_type="PHOTO",
                story_content="첨성대에 도착했습니다.",
                user_result=ChapterUserResult(image_url="https://x/p.jpg", choice=None),
            ),
            EbookChapter(
                sequence=2, place_id=302, place_name="불국사",
                place_address="경북 경주시",
                mission_title="불국사의 비밀을 선택하라",
                mission_description="두루마리 앞.", mission_type="CHOICE",
                story_content="두루마리를 골랐습니다.",
                user_result=ChapterUserResult(image_url=None, choice="이상 세계."),
            ),
        ],
    )


def test_service_returns_data_with_matching_story_id():
    data = EbookJobService().create_ebook_content(_wire_request())
    assert data.story_id == 301


def test_service_returns_meta_cover_pages():
    data = EbookJobService().create_ebook_content(_wire_request())
    assert data.ebook_content.meta.title == "경주의 비밀"
    assert data.ebook_content.cover.title == "경주의 비밀"
    assert data.ebook_content.pages
    types = [p.type for p in data.ebook_content.pages]
    assert types[0] == "COVER" and types[-1] == "BACK_COVER"
    assert types.count("CHAPTER") == 2


def test_service_uses_draft_service_under_the_hood(monkeypatch):
    """Spy: ensure EbookDraftService.generate_draft is invoked exactly once."""
    from app.services.ebook import ebook_draft_service as mod
    calls: list = []
    real = mod.EbookDraftService.generate_draft

    def _spy(self, request):
        calls.append(request)
        return real(self, request)

    monkeypatch.setattr(mod.EbookDraftService, "generate_draft", _spy)
    EbookJobService().create_ebook_content(_wire_request())
    assert len(calls) == 1
