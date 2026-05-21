"""
Adapter between API.md wire schemas (`app/schemas/story.py`) and the 156 internal
draft schemas (`app/schemas/ebook_schema.py`).

Two responsibilities:
  1. wire request → internal `EbookDraftGenerationRequest`
  2. internal `EbookDraft` (+ original wire request) → wire `EbookJobData`

We never expose internal-only fields (draft_id / page_type / body / image_prompt
/ used_chunk_ids / source_urls / warnings) on the wire response. The wire
`EbookContent` shape from API.md is preserved exactly.
"""
from __future__ import annotations

from typing import TYPE_CHECKING, Iterable

if TYPE_CHECKING:
    from app.services.ebook.ebook_narrative_service import EbookNarrativeDraft

from app.schemas.ebook_schema import (
    EbookDraft,
    EbookDraftGenerationRequest,
    EbookDraftOptions,
    EbookMissionInput,
    EbookMissionResultInput,
    EbookPageDraft,
    EbookPlaceInput,
    EbookStorySourceInput,
    EbookUserProfile,
)
from app.schemas.story import (
    EbookContent,
    EbookCover,
    EbookChapter,
    EbookJobData,
    EbookJobRequest,
    EbookMeta,
    EbookPage,
    PageLayout,
    PageType,
)


# ---- enum mappings -----------------------------------------------------


# 156 internal page_type → wire PageType.
# table_of_contents / mission_result / sources / reflection 는 API.md enum 에
# 없으므로 호출자가 wire 변환 시 제외 (None 으로 표시).
_INTERNAL_TO_WIRE_PAGE_TYPE: dict[str, PageType | None] = {
    "cover": "COVER",
    "table_of_contents": None,
    "prologue": "INTRO",
    "place_story": "CHAPTER",
    "mission_result": None,
    "reflection": None,
    "epilogue": "OUTRO",
    "sources": None,
}


_DEFAULT_BACKGROUND_COLOR = "#F5E6D3"


# ---- storybook chapter title -------------------------------------------

# 1..10 한국어 서수. 그 이상은 fallback 으로 "{N}번째 이야기, {place_name}" 사용.
_KOREAN_ORDINALS: dict[int, str] = {
    1: "첫 번째",
    2: "두 번째",
    3: "세 번째",
    4: "네 번째",
    5: "다섯 번째",
    6: "여섯 번째",
    7: "일곱 번째",
    8: "여덟 번째",
    9: "아홉 번째",
    10: "열 번째",
}

# 장소별 시적 후렴 — '미션 / 찾기 / 선택하라 / 포착하기' 같은 미션형 어휘 금지.
# 매칭은 place_name 정확 일치 우선, 없으면 부분 포함 (예: "불국사 다보탑" → "다보탑").
_PLACE_TITLE_SUFFIX: dict[str, str] = {
    "첨성대": "별빛",
    "동궁과월지": "달빛",
    "동궁과 월지": "달빛",
    "월지": "달빛",
    "안압지": "달빛",
    "불국사": "부처의 자리",
    "불국사 대웅전": "부처의 자리",
    "대웅전": "부처의 자리",
    "불국사 다보탑": "다보의 빛",
    "다보탑": "다보의 빛",
    "불국사 석가탑": "석가의 정적",
    "석가탑": "석가의 정적",
    "불국사 백운교": "구름 다리",
    "백운교": "구름 다리",
    "에밀레종": "종소리의 여운",
    "성덕대왕신종": "종소리의 여운",
    "천마총": "하늘을 달리는 말",
    "천마총 금관": "왕의 빛나는 가지",
    "금관": "왕의 빛나는 가지",
    "선덕여왕릉": "여왕의 잠",
    "경주 고선사지 삼층석탑": "원효의 자취",
    "고선사지 삼층석탑": "원효의 자취",
    "고선사지": "원효의 자취",
    "봉황대": "잠든 봉황",
    "석빙고": "겨울이 머문 자리",
    "경주향교": "오래된 배움",
    "향교": "오래된 배움",
}


def _ordinal_phrase(sequence: int) -> str:
    return _KOREAN_ORDINALS.get(sequence, f"{sequence}번째")


def _suffix_for(place_name: str) -> str | None:
    if not place_name:
        return None
    if place_name in _PLACE_TITLE_SUFFIX:
        return _PLACE_TITLE_SUFFIX[place_name]
    # 부분 포함 fallback — 가장 구체적인 키부터.
    for key in sorted(_PLACE_TITLE_SUFFIX, key=len, reverse=True):
        if key in place_name:
            return _PLACE_TITLE_SUFFIX[key]
    return None


def compose_storybook_chapter_title(sequence: int, place_name: str) -> str:
    """이야기책 톤의 chapter title 생성. mission_title 은 절대 사용하지 않는다.

    예:
        (1, "첨성대")        → "첫 번째 이야기, 첨성대의 별빛"
        (2, "동궁과월지")    → "두 번째 이야기, 월지에 비친 달빛"  (특수 표현)
        (3, "에밀레종")      → "세 번째 이야기, 에밀레종의 종소리의 여운"
        (1, "알수없음")      → "첫 번째 이야기, 알수없음"  (suffix fallback)
        (12, "첨성대")       → "12번째 이야기, 첨성대의 별빛"  (ordinal fallback)
    """
    ordinal = _ordinal_phrase(sequence)
    suffix = _suffix_for(place_name)
    # 동궁과월지 / 월지 는 '~의 달빛' 보다 '월지에 비친 달빛' 이 자연스럽다.
    if place_name and ("월지" in place_name or "안압지" in place_name):
        return f"{ordinal} 이야기, 월지에 비친 달빛"
    if not place_name:
        return f"{ordinal} 이야기"
    if suffix:
        return f"{ordinal} 이야기, {place_name}의 {suffix}"
    return f"{ordinal} 이야기, {place_name}"


# ---- helpers -----------------------------------------------------------


def _age_group_from_people(people) -> str | None:
    if not people:
        return None
    youngest = min(p.age for p in people)
    if youngest <= 12:
        return "child"
    if youngest <= 19:
        return "teen"
    if len(people) > 1:
        return "family"
    return "adult"


def _companion_type_from_people(people) -> str | None:
    if not people:
        return None
    return "family" if len(people) > 1 else "solo"


def _persona_hint(people) -> str | None:
    for p in people:
        if p.tendency:
            return p.tendency
    return None


def _author_label(people) -> str:
    return ", ".join(p.name for p in people if p.name) or "사용자"


def _unique(values: Iterable[str]) -> list[str]:
    out: list[str] = []
    for v in values:
        if v and v not in out:
            out.append(v)
    return out


# ---- adapter -----------------------------------------------------------


class EbookJobAdapter:
    # ---- request → draft -----------------------------------------------

    def to_draft_request(self, request: EbookJobRequest) -> EbookDraftGenerationRequest:
        people = list(request.story.protagonist_info.people_information)
        nickname = people[0].name if people else None

        profile = EbookUserProfile(
            nickname=nickname,
            persona=_persona_hint(people),
            companion_type=_companion_type_from_people(people),
            age_group=_age_group_from_people(people),
            language="ko",
        )

        ordered = sorted(request.chapters, key=lambda c: c.sequence)

        places = [
            EbookPlaceInput(
                place_id=str(c.place_id),
                place_name=c.place_name,
                sequence=c.sequence,
                address=c.place_address,
                category="문화유산",
            )
            for c in ordered
        ]

        missions = [
            EbookMissionInput(
                sequence=c.sequence,
                title=c.mission_title,
                description=c.mission_description,
                type=c.mission_type,
                story=c.story_content,
                place_name=c.place_name,
            )
            for c in ordered
        ]

        # MVP 정책: 미션은 PHOTO 만 사용 — image_url 만 photo_urls 로 흘린다.
        # user_result.choice 는 현재 사용하지 않으며, 어떤 경로로도 본문에 노출되지 않는다.
        # (E-book 은 미션 보고서가 아니라 이야기책이므로 user_answer / selected_keywords /
        # quote 어디로도 매핑하지 않는다.)
        mission_results: list[EbookMissionResultInput] = []
        for c in ordered:
            ur = c.user_result
            has_image = bool(ur.image_url)
            if not has_image:
                # 사진이 없으면 mission_result 자체를 만들지 않는다.
                continue
            meta: dict = {"wire_mission_type": c.mission_type}
            if nickname:
                meta["nickname"] = nickname
            mission_results.append(EbookMissionResultInput(
                sequence=c.sequence,
                place_id=str(c.place_id),
                place_name=c.place_name,
                mission_title=c.mission_title,
                completed=True,
                user_answer=None,         # MVP: 사용하지 않음
                photo_urls=[ur.image_url],
                selected_keywords=[],     # MVP: 사용하지 않음
                metadata=meta,
            ))

        return EbookDraftGenerationRequest(
            request_id=f"ebook-job-{request.story_id}-{request.user_id}",
            user_profile=profile,
            places=places,
            story_source=EbookStorySourceInput(
                intro=request.story.intro,
                outro=request.story.outro,
                missions=missions,
            ),
            mission_results=mission_results,
            options=EbookDraftOptions(
                # API.md response 에 sources / table_of_contents / mission_result
                # 페이지가 없으므로 내부 생성도 끈다 (변환 시 제외하긴 하지만
                # 불필요한 페이지 만들지 않는 편이 단순).
                include_table_of_contents=False,
                include_mission_results=False,
                include_sources=False,
                include_image_prompts=True,
            ),
            metadata={
                "wire_story_id": request.story_id,
                "wire_user_id": request.user_id,
                "wire_story_title": request.story.title,
            },
        )

    # ---- draft → wire response -----------------------------------------

    def to_ebook_content(
        self,
        story_id: int,
        request: EbookJobRequest,
        draft: EbookDraft,
    ) -> EbookJobData:
        # 156 internal place_story pages carry `place_name` but not `sequence`,
        # so we look up the original chapter by name. (place_name is unique
        # within a single course in API.md.)
        chapters_by_name = {c.place_name: c for c in request.chapters}

        wire_pages: list[EbookPage] = []
        page_no = 1

        for page in draft.pages:
            wire_type = _INTERNAL_TO_WIRE_PAGE_TYPE.get(page.page_type)
            if wire_type is None:
                # internal-only page (table_of_contents / mission_result / sources)
                continue
            wire_pages.append(self._build_wire_page(
                page=page,
                wire_type=wire_type,
                page_number=page_no,
                chapters_by_name=chapters_by_name,
                request=request,
            ))
            page_no += 1

        # Always append a BACK_COVER page (not in 156 draft).
        wire_pages.append(self._build_back_cover_page(
            page_number=page_no,
            request=request,
        ))

        people = list(request.story.protagonist_info.people_information)
        author = _author_label(people)
        meta_subtitle = f"{author}의 경주 탐험 이야기"

        cover_thumb_hint = (
            (draft.cover.image_prompt if draft.cover and draft.cover.image_prompt else None)
            or self._fallback_cover_hint(request)
        )

        content = EbookContent(
            meta=EbookMeta(
                title=request.story.title,
                subtitle=meta_subtitle,
                author=author,
                page_count=len(wire_pages),
                language="ko",
            ),
            cover=EbookCover(
                title=request.story.title,
                background_color=_DEFAULT_BACKGROUND_COLOR,
                thumbnail_hint=cover_thumb_hint,
            ),
            pages=wire_pages,
        )
        return EbookJobData(story_id=story_id, ebook_content=content)

    # ---- per-page builders --------------------------------------------

    def _build_wire_page(
        self,
        *,
        page: EbookPageDraft,
        wire_type: PageType,
        page_number: int,
        chapters_by_name: dict[str, EbookChapter],
        request: EbookJobRequest,
    ) -> EbookPage:
        if wire_type == "COVER":
            return EbookPage(
                page_number=page_number,
                type="COVER",
                layout="COVER",
                # cover.title / meta.title 과 일치시키기 위해 항상 wire title 사용.
                title=request.story.title,
                subtitle=page.subtitle,
                text=None,
                image_url=None,
                caption=None,
                quote=None,
            )

        if wire_type == "INTRO":
            return EbookPage(
                page_number=page_number,
                type="INTRO",
                layout="TEXT_ONLY",
                title=page.title or "이야기의 시작",
                subtitle=None,
                text=page.body or None,
                image_url=None,
                caption=None,
                quote=None,
            )

        if wire_type == "OUTRO":
            return EbookPage(
                page_number=page_number,
                type="OUTRO",
                layout="TEXT_ONLY",
                title=page.title or "그리고, 새로운 여행",
                subtitle=None,
                text=page.body or None,
                image_url=None,
                caption=None,
                quote=None,
            )

        # CHAPTER — match by place_name back to the original chapter.
        # MVP 정책: choice 는 본문 / quote / caption 어디로도 흘리지 않는다.
        chapter = chapters_by_name.get(page.place_name) if page.place_name else None
        ur = chapter.user_result if chapter else None
        image_url = ur.image_url if ur else None

        layout = self._layout_for_chapter(image_url=image_url)
        caption = (
            f"{page.place_name}에서 남긴 여행의 한 장면"
            if image_url and page.place_name
            else None
        )
        # MVP 정책: chapter title 은 mission_title 을 그대로 노출하지 않고
        # 이야기책 톤으로 합성한다 — sequence + 장소별 시적 후렴.
        seq_for_title = (
            chapter.sequence if chapter else None
        )
        place_for_title = page.place_name or (chapter.place_name if chapter else "")
        if seq_for_title is not None:
            wire_title = compose_storybook_chapter_title(seq_for_title, place_for_title)
        else:
            wire_title = place_for_title or "이야기"

        return EbookPage(
            page_number=page_number,
            type="CHAPTER",
            layout=layout,
            sequence=chapter.sequence if chapter else None,
            title=wire_title,
            subtitle=chapter.place_address if chapter else page.subtitle,
            text=page.body or None,
            image_url=image_url,
            caption=caption,
            quote=None,  # MVP: choice 미사용 → quote 항상 null
        )

    @staticmethod
    def _layout_for_chapter(*, image_url: str | None) -> PageLayout:
        # MVP: TEXT_WITH_QUOTE 미사용. 사진 유무로만 결정.
        if image_url:
            return "IMAGE_TOP_TEXT_BOTTOM"
        return "TEXT_ONLY"

    # ---- narrative → wire response (158) ------------------------------

    def to_ebook_content_from_narrative(
        self,
        story_id: int,
        request: EbookJobRequest,
        narrative: "EbookNarrativeDraft",
    ) -> EbookJobData:
        """LLM narrative draft → API.md wire response. wire schema 100% 유지."""
        people = list(request.story.protagonist_info.people_information)
        author = _author_label(people)
        meta_subtitle = f"{author}의 경주 탐험 이야기"

        wire_pages: list[EbookPage] = []
        page_no = 1

        # 1) cover
        wire_pages.append(EbookPage(
            page_number=page_no,
            type="COVER",
            layout="COVER",
            title=request.story.title,  # meta.title / cover.title 와 일치
            subtitle=narrative.cover_subtitle or None,
            text=None,
            image_url=None,
            caption=None,
            quote=None,
        ))
        page_no += 1

        # 2) intro
        wire_pages.append(EbookPage(
            page_number=page_no,
            type="INTRO",
            layout="TEXT_ONLY",
            title=narrative.intro_page.title or "이야기의 시작",
            subtitle=None,
            text=narrative.intro_page.text or None,
            image_url=None,
            caption=None,
            quote=None,
        ))
        page_no += 1

        # 3) chapters
        chapters_by_seq = {c.sequence: c for c in request.chapters}
        narrative_chapters_sorted = sorted(narrative.chapters, key=lambda c: c.sequence)
        for nch in narrative_chapters_sorted:
            chapter = chapters_by_seq.get(nch.sequence)
            image_url = (
                chapter.user_result.image_url
                if chapter and chapter.user_result and chapter.user_result.image_url
                else None
            )
            layout = self._layout_for_chapter(image_url=image_url)
            wire_pages.append(EbookPage(
                page_number=page_no,
                type="CHAPTER",
                layout=layout,
                sequence=nch.sequence,
                title=nch.title,
                subtitle=chapter.place_address if chapter else None,
                text=nch.text or None,
                image_url=image_url,
                caption=nch.caption,
                quote=None,  # MVP: choice 미사용
            ))
            page_no += 1

        # 4) outro
        wire_pages.append(EbookPage(
            page_number=page_no,
            type="OUTRO",
            layout="TEXT_ONLY",
            title=narrative.outro_page.title or "여행을 마치며",
            subtitle=None,
            text=narrative.outro_page.text or None,
            image_url=None,
            caption=None,
            quote=None,
        ))
        page_no += 1

        # 5) back cover
        wire_pages.append(EbookPage(
            page_number=page_no,
            type="BACK_COVER",
            layout="BACK_COVER",
            title=narrative.back_cover.title or "모험은 계속됩니다",
            subtitle="Atoria와 함께한 경주 여행",
            text=narrative.back_cover.text or None,
            image_url=None,
            caption=None,
            quote=None,
        ))

        content = EbookContent(
            meta=EbookMeta(
                title=request.story.title,
                subtitle=meta_subtitle,
                author=author,
                page_count=len(wire_pages),
                language="ko",
            ),
            cover=EbookCover(
                title=request.story.title,
                background_color=_DEFAULT_BACKGROUND_COLOR,
                thumbnail_hint=narrative.thumbnail_hint or self._fallback_cover_hint(request),
            ),
            pages=wire_pages,
        )
        return EbookJobData(story_id=story_id, ebook_content=content)

    def _build_back_cover_page(
        self, *, page_number: int, request: EbookJobRequest,
    ) -> EbookPage:
        ordered = sorted(request.chapters, key=lambda c: c.sequence)
        place_names = " · ".join(c.place_name for c in ordered)
        people = list(request.story.protagonist_info.people_information)
        author = _author_label(people)
        return EbookPage(
            page_number=page_number,
            type="BACK_COVER",
            layout="BACK_COVER",
            title=f"{author}의 모험은 계속됩니다",
            subtitle="Atoria와 함께한 경주 여행",
            text=f"방문한 장소: {place_names}" if place_names else None,
            image_url=None,
            caption=None,
            quote=None,
        )

    # ---- fallback hints -----------------------------------------------

    @staticmethod
    def _fallback_cover_hint(request: EbookJobRequest) -> str:
        ordered = sorted(request.chapters, key=lambda c: c.sequence)
        if not ordered:
            return "경주의 풍경"
        return f"{ordered[0].place_name} 앞에서의 한 장면"
