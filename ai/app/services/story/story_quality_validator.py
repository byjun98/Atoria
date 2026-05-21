"""
Validate a `StoryIntroDraft` against structural / length / mission /
RAG-usage / fact-vs-legend rules. Pure read-only — no mutation here.
"""
from __future__ import annotations

from app.core.config import settings
from app.schemas.story_schema import (
    CoursePlaceInput,
    PlaceStoryDraft,
    RagContextByPlace,
    StoryIntroDraft,
    StoryIntroGenerationRequest,
)
from app.services.story.story_quality_models import StoryQualityReport


# Internal mission_type set defined in 151 (`MissionType` Literal).
DEFAULT_ALLOWED_MISSION_TYPES: set[str] = {
    "observation", "photo", "quiz", "imagination", "route",
}

# Action-verb keywords that suggest the mission is field-doable.
_OBSERVABLE_KEYWORDS = (
    "관찰", "찾", "사진", "촬영", "방향", "모양", "소리", "상상",
    "비교", "걷", "기록", "세어", "둘러", "바라보", "맞춰",
    "그려", "써", "순서", "위치", "정하", "고르", "클리어",
)

# 단정 어조가 설화/상징 자료에서 나오면 경고.
_ASSERTIVE_PHRASES = ("실제로", "반드시", "사실이다", "확실히")

# 설화/전승 표현으로 권장되는 어구.
_LEGEND_FRIENDLY_PHRASES = (
    "전해진다", "전해져", "이야기된다", "알려져", "이야기 한", "한다고",
    "상상해", "재구성", "라는 이야기",
)

# 상징 표현으로 권장되는 어구.
_SYMBOLIC_FRIENDLY_PHRASES = (
    "상징", "상상해", "의미로", "비유", "해석할 수", "비추어",
)

# 흔한 일반 문장 (장소 고유 단서가 없으면 경고).
_GENERIC_SENTENCES = (
    "재미있는 여행을 떠나볼까요",
    "문화재를 잘 관찰해보세요",
    "오늘의 탐험은 여기까지입니다",
)

# Mission_title 이 이 단어들로만 구성되면 너무 일반적이다.
_GENERIC_TITLE_TOKENS = (
    "탐험", "조사", "찾기", "관찰", "둘러보기", "둘러보", "문화재", "유적", "이야기",
    "여행", "체험", "찾아라",
)

# intro / outro 의 흔한 클로징.
_GENERIC_INTRO_PHRASES = (
    "탐험할 준비가 되었나요",
    "준비가 되었나요",
    "옛날 이야기 속으로 들어가 보아요",
    "재미있는 여행을 떠나볼까요",
    "함께 떠나봐요",
    "숨겨진 이야기를 풀어보세요",
    "어떤 모험이 기다리고 있을까",
    "어떤 모험이 기다릴까요",
)
_GENERIC_OUTRO_PHRASES = (
    "오늘의 탐험은 여기까지입니다",
    "다음에 또 만나요",
    "다음에도 또 다른 이야기를 찾아 떠나봐요",
    "다음에 또 다른 모험",
    "단순한 탐방",
    "마음속에 오래도록 남길 바랍니다",
    "오래도록 남을 것이다",
)

_APP_GUIDE_INTRO_PHRASES = (
    "준비가 되었나요",
    "어떤 모험이 기다리고 있을까",
    "어떤 모험이 기다릴까요",
    "어떤 이야기들이 기다리고 있을까요",
    "어떤 단서들이 기다리고 있을까요",
    "숨겨진 이야기를 풀어보세요",
    "숨겨진 이야기",
    "숨겨진 비밀",
    "비밀을 풀",
    "역사의 숨결",
    "신라의 숨결",
    "숨결",
    "퀘스트를 클리어",
    "시작해볼까요",
    "떠나볼까요",
    "특별한 여행",
)

_APP_GUIDE_OUTRO_PHRASES = (
    "단순한 탐방",
    "진짜 모험",
    "다음에 또",
    "새로운 모험",
    "다음 여정",
    "또 다른 역사 탐험",
    "잊지 못할 추억",
    "숨겨진 이야기",
    "비밀을 풀",
    "마음속에 오래도록",
    "마음속에 간직",
    "오래도록 남을 것",
    "준비가 되었나요",
)

_PHOTO_AFTERTHOUGHT_PHRASES = (
    "사진에 담아보세요",
    "사진으로 남겨보세요",
    "그 모습을 사진으로 찍어보세요",
    "사진을 찍어보세요",
    "촬영해보세요",
    "담아보세요",
    "포착하세요",
)

_CLEAR_CONDITION_TERMS = (
    "클리어",
    "한 화면",
    "함께 보",
    "단서",
    "열쇠",
    "표식",
    "증거",
    "성공",
    "인증",
)

_GAMEFUL_TERMS = (
    "클리어",
    "단서",
    "열쇠",
    "표식",
    "암호",
    "관문",
    "지도",
    "문장",
    "성공",
)

_ACTION_TERMS = (
    "찾",
    "세어",
    "세고",
    "세기",
    "비교",
    "맞춰",
    "정하",
    "고르",
    "살펴",
    "살피",
    "순서",
    "위치",
    "방향",
    "따라",
)

_PASSIVE_MISSION_PHRASES = (
    "유심히 보세요",
    "나란히 보세요",
    "바라보세요",
    "관찰해 보세요",
)

_SCENE_DISCOVERY_TERMS = (
    "찾",
    "발견",
    "알아차",
    "보이자",
    "드러",
    "열리",
    "이어",
    "남았",
    "잡히",
)

_SCENE_VISIBLE_OBJECT_TERMS = (
    "계단",
    "난간",
    "기단",
    "탑신",
    "옥개석",
    "처마",
    "기둥",
    "현판",
    "창",
    "입구",
    "지붕선",
    "돌",
    "문",
    "마당",
    "아치",
    "몸체",
)

_EXPOSITORY_STORY_PHRASES = (
    "이 문화재는",
    "볼 수 있습니다",
    "느낄 수 있습니다",
    "이해할 수 있습니다",
    "도움이 됩니다",
    "경험할 수 있습니다",
)

_SEOKBINGGO_FORBIDDEN_TERMS = (
    "내부",
    "안쪽",
    "천장",
    "배수로",
    "바닥 중앙",
    "들어가",
    "들어가서",
)

_DAEUNGJEON_FORBIDDEN_TERMS = (
    "내부",
    "불단",
    "불상",
    "법당 안",
    "안쪽",
)


def _collect_place_keywords(
    place_name: str | None,
    rag_for_place,  # type: ignore[no-untyped-def]
) -> set[str]:
    """장소명 + RAG 의 heritage_name / motifs / mission_hooks / tone_tags / title
    단어를 한 set 으로 모은다. (RagContextItem 에 노출된 필드 기준)"""
    out: set[str] = set()
    if place_name:
        out.add(place_name)
    if rag_for_place is None:
        return out
    for bucket in (
        rag_for_place.fact_contexts,
        rag_for_place.story_materials,
        rag_for_place.symbolic_materials,
    ):
        for it in bucket:
            if it.heritage_name:
                out.add(it.heritage_name)
            out.update(m for m in (it.motifs or []) if m)
            out.update(h for h in (it.mission_hooks or []) if h)
            out.update(t for t in (it.tone_tags or []) if t)
            if it.title:
                # title 에서 띄어쓰기로 분리한 토큰 중 2글자 이상만 키워드로
                out.update(t for t in it.title.split() if len(t) >= 2)
    return out


def _count_keyword_hits(text: str, keywords: set[str]) -> int:
    if not text:
        return 0
    return sum(1 for k in keywords if k and k in text)


def _has_field_action(text: str) -> bool:
    if not text:
        return False
    return any(term in text for term in _ACTION_TERMS)


def _nickname_aliases(nickname: str | None) -> set[str]:
    if not nickname:
        return set()
    nickname = nickname.strip()
    aliases = {nickname}
    if len(nickname) >= 3:
        aliases.add(nickname[-2:])
    return {a for a in aliases if a}


class StoryQualityValidator:
    def __init__(
        self,
        min_intro_chars: int | None = None,
        max_intro_chars: int | None = None,
        min_outro_chars: int | None = None,
        max_outro_chars: int | None = None,
        min_story_fragment_chars: int | None = None,
        max_story_fragment_chars: int | None = None,
        min_mission_description_chars: int | None = None,
        allowed_mission_types: set[str] | None = None,
    ) -> None:
        self.min_intro = _or(min_intro_chars, settings.STORY_MIN_INTRO_CHARS)
        self.max_intro = _or(max_intro_chars, settings.STORY_MAX_INTRO_CHARS)
        self.min_outro = _or(min_outro_chars, settings.STORY_MIN_OUTRO_CHARS)
        self.max_outro = _or(max_outro_chars, settings.STORY_MAX_OUTRO_CHARS)
        self.min_story = _or(min_story_fragment_chars, settings.STORY_MIN_PLACE_STORY_CHARS)
        self.max_story = _or(max_story_fragment_chars, settings.STORY_MAX_PLACE_STORY_CHARS)
        self.min_mission_desc = _or(
            min_mission_description_chars, settings.STORY_MIN_MISSION_DESCRIPTION_CHARS
        )
        self.allowed_mission_types = allowed_mission_types or DEFAULT_ALLOWED_MISSION_TYPES

    # ---- public --------------------------------------------------------

    def validate_intro_draft(
        self,
        draft: StoryIntroDraft,
        request: StoryIntroGenerationRequest,
    ) -> StoryQualityReport:
        report = StoryQualityReport()
        self._check_overall_structure(draft, request, report)
        self._check_lengths(draft, report)
        self._check_intro_outro_genericness(draft, request, report)
        self._check_app_guide_tone(draft, report)
        rag_lookup = {c.place_name: c for c in request.rag_contexts}
        user_aliases = _nickname_aliases(request.user_profile.nickname)
        seen_titles: set[str] = set()
        seen_descriptions: set[str] = set()
        # Per-place checks (only for the prefix we actually got back).
        request_places_by_seq = {p.sequence: p for p in request.places}
        for idx, place in enumerate(draft.places):
            req_place = request_places_by_seq.get(place.sequence)
            self._check_place(
                place=place,
                req_place=req_place,
                idx=idx,
                report=report,
                rag_for_place=rag_lookup.get(place.place_name),
                seen_titles=seen_titles,
                seen_descriptions=seen_descriptions,
                user_aliases=user_aliases,
            )
        self._check_global_rag_usage(draft, request, report)
        self._check_global_rag_term_underuse(draft, request, report)
        return report

    # ---- intro / outro genericness -------------------------------------

    @staticmethod
    def _check_intro_outro_genericness(
        draft: StoryIntroDraft,
        request: StoryIntroGenerationRequest,
        report: StoryQualityReport,
    ) -> None:
        place_names = [p.place_name for p in request.places if p.place_name]
        nick = (request.user_profile.nickname or "").strip()

        if draft.intro and draft.intro.strip():
            intro = draft.intro
            generic_hits = [p for p in _GENERIC_INTRO_PHRASES if p in intro]
            named_places = sum(1 for n in place_names if n in intro)
            has_nickname = bool(nick) and nick in intro
            # 관용구가 있고, 장소명이 2개 미만이면서 닉네임 호명도 없으면 일반 인사말로 본다
            if generic_hits and named_places < 2 and not has_nickname:
                report.add_issue(
                    "INTRO_TOO_GENERIC", "warning",
                    f"intro 에 흔한 인사 문구만 들어 있습니다 (hits={generic_hits}, "
                    f"장소명 {named_places}/{len(place_names)}).",
                    location="intro",
                )

        if draft.outro and draft.outro.strip():
            outro = draft.outro
            generic_hits = [p for p in _GENERIC_OUTRO_PHRASES if p in outro]
            named_places = sum(1 for n in place_names if n in outro)
            if generic_hits and named_places < 2:
                report.add_issue(
                    "OUTRO_TOO_GENERIC", "warning",
                    f"outro 에 흔한 마무리 문구만 들어 있습니다 (hits={generic_hits}, "
                    f"장소명 {named_places}/{len(place_names)}).",
                    location="outro",
                )

    @staticmethod
    def _check_app_guide_tone(
        draft: StoryIntroDraft,
        report: StoryQualityReport,
    ) -> None:
        intro = draft.intro or ""
        intro_hits = [p for p in _APP_GUIDE_INTRO_PHRASES if p in intro]
        if intro_hits:
            report.add_issue(
                "INTRO_APP_GUIDE_TONE",
                "warning",
                f"intro가 앱 안내문/홍보 문장처럼 보입니다: {intro_hits}",
                location="intro",
            )

        outro = draft.outro or ""
        outro_hits = [p for p in _APP_GUIDE_OUTRO_PHRASES if p in outro]
        if outro_hits:
            report.add_issue(
                "OUTRO_APP_GUIDE_TONE",
                "warning",
                f"outro가 앱 안내문/홍보 문장처럼 보입니다: {outro_hits}",
                location="outro",
            )

    # ---- A. structure --------------------------------------------------

    def _check_overall_structure(
        self,
        draft: StoryIntroDraft,
        request: StoryIntroGenerationRequest,
        report: StoryQualityReport,
    ) -> None:
        if not draft.title or not draft.title.strip():
            report.add_issue(
                "EMPTY_TITLE", "warning", "title이 비어 있습니다.", location="title"
            )
        if not draft.intro or not draft.intro.strip():
            report.add_issue(
                "EMPTY_INTRO", "error", "intro가 비어 있습니다.", location="intro"
            )
        if not draft.outro or not draft.outro.strip():
            report.add_issue(
                "EMPTY_OUTRO", "error", "outro가 비어 있습니다.", location="outro"
            )
        if not draft.places:
            report.add_issue(
                "PLACE_COUNT_MISMATCH",
                "error",
                "draft.places가 비어 있습니다.",
                location="places",
            )
            return
        if len(draft.places) != len(request.places):
            report.add_issue(
                "PLACE_COUNT_MISMATCH",
                "error",
                f"places 개수 불일치: requested={len(request.places)}, "
                f"returned={len(draft.places)}",
                location="places",
            )

        req_seq = sorted(p.sequence for p in request.places)
        draft_seq = sorted(p.sequence for p in draft.places)
        if req_seq != draft_seq:
            report.add_issue(
                "PLACE_SEQUENCE_MISMATCH",
                "warning",
                f"sequence 집합 불일치: requested={req_seq}, returned={draft_seq}",
                location="places",
                auto_fixable=True,
            )

    # ---- B. lengths ----------------------------------------------------

    def _check_lengths(self, draft: StoryIntroDraft, report: StoryQualityReport) -> None:
        intro_len = len((draft.intro or "").strip())
        if 0 < intro_len < self.min_intro:
            report.add_issue(
                "INTRO_TOO_SHORT", "warning",
                f"intro가 너무 짧습니다: {intro_len} < {self.min_intro}",
                location="intro",
            )
        if intro_len > self.max_intro:
            report.add_issue(
                "INTRO_TOO_LONG", "warning",
                f"intro가 너무 깁니다: {intro_len} > {self.max_intro}",
                location="intro",
            )
        outro_len = len((draft.outro or "").strip())
        if 0 < outro_len < self.min_outro:
            report.add_issue(
                "OUTRO_TOO_SHORT", "warning",
                f"outro가 너무 짧습니다: {outro_len} < {self.min_outro}",
                location="outro",
            )
        if outro_len > self.max_outro:
            report.add_issue(
                "OUTRO_TOO_LONG", "warning",
                f"outro가 너무 깁니다: {outro_len} > {self.max_outro}",
                location="outro",
            )

    # ---- C+D+E+F. per-place --------------------------------------------

    def _check_place(
        self,
        *,
        place: PlaceStoryDraft,
        req_place: CoursePlaceInput | None,
        idx: int,
        report: StoryQualityReport,
        rag_for_place: RagContextByPlace | None,
        seen_titles: set[str],
        seen_descriptions: set[str],
        user_aliases: set[str],
    ) -> None:
        loc = f"places[{idx}]"
        if req_place and place.place_name and req_place.place_name != place.place_name:
            report.add_issue(
                "PLACE_NAME_MISMATCH", "warning",
                f"place_name 불일치: requested={req_place.place_name}, "
                f"returned={place.place_name}",
                location=loc,
            )

        # story fragment length
        story_text = (place.story_fragment or "").strip()
        if not story_text:
            report.add_issue(
                "MISSING_PLACE_STORY", "error",
                "story_fragment가 비어 있습니다.", location=f"{loc}.story_fragment",
            )
        else:
            if len(story_text) < self.min_story:
                severity = "error" if len(story_text) < max(40, self.min_story - 20) else "warning"
                report.add_issue(
                    "PLACE_STORY_TOO_SHORT", severity,
                    f"story_fragment가 너무 짧습니다: {len(story_text)} < {self.min_story}",
                    location=f"{loc}.story_fragment",
                )
            if len(story_text) > self.max_story:
                report.add_issue(
                    "PLACE_STORY_TOO_LONG", "warning",
                    f"story_fragment가 너무 깁니다: {len(story_text)} > {self.max_story}",
                    location=f"{loc}.story_fragment",
                )

        # mission
        mission = place.mission
        if mission is None:
            report.add_issue(
                "MISSING_MISSION", "error",
                "mission이 누락되었습니다.", location=f"{loc}.mission",
            )
        else:
            if not mission.mission_title or not mission.mission_title.strip():
                report.add_issue(
                    "MISSION_TITLE_EMPTY", "error",
                    "mission_title이 비어 있습니다.",
                    location=f"{loc}.mission.mission_title",
                )
            instruction = (mission.mission_instruction or "").strip()
            if not instruction:
                report.add_issue(
                    "MISSION_DESCRIPTION_EMPTY", "error",
                    "mission_instruction이 비어 있습니다.",
                    location=f"{loc}.mission.mission_instruction",
                )
            elif len(instruction) < self.min_mission_desc:
                report.add_issue(
                    "MISSION_DESCRIPTION_TOO_SHORT", "warning",
                    f"mission_instruction이 너무 짧습니다: "
                    f"{len(instruction)} < {self.min_mission_desc}",
                    location=f"{loc}.mission.mission_instruction",
                )
            if mission.mission_type not in self.allowed_mission_types:
                report.add_issue(
                    "MISSION_TYPE_INVALID", "error",
                    f"허용되지 않은 mission_type: {mission.mission_type}",
                    location=f"{loc}.mission.mission_type",
                    auto_fixable=True,
                )
            else:
                # Observable-action heuristic (not a strict rule).
                blob = f"{mission.mission_title} {instruction}"
                if instruction and not any(k in blob for k in _OBSERVABLE_KEYWORDS):
                    report.add_issue(
                        "MISSION_NOT_OBSERVABLE", "warning",
                        "mission이 현장에서 수행 가능한 행동 키워드를 포함하지 않습니다.",
                        location=f"{loc}.mission",
                    )
            if not mission.related_place_name:
                report.add_issue(
                    "MISSION_RELATED_PLACE_MISSING", "warning",
                    "mission.related_place_name이 비어 있습니다.",
                    location=f"{loc}.mission.related_place_name",
                    auto_fixable=True,
                )
            # duplicate detection
            t = (mission.mission_title or "").strip()
            d = (mission.mission_instruction or "").strip()
            if t and t in seen_titles:
                report.add_issue(
                    "DUPLICATE_MISSION", "warning",
                    f"mission_title 중복: '{t}'", location=f"{loc}.mission.mission_title",
                )
            if d and d in seen_descriptions:
                report.add_issue(
                    "DUPLICATE_MISSION", "warning",
                    "mission_instruction이 다른 장소와 동일합니다.",
                    location=f"{loc}.mission.mission_instruction",
                )
            seen_titles.add(t)
            seen_descriptions.add(d)

        # per-place RAG usage
        if rag_for_place is not None and (
            rag_for_place.fact_contexts
            or rag_for_place.story_materials
            or rag_for_place.symbolic_materials
        ):
            if not place.used_chunk_ids:
                report.add_issue(
                    "PLACE_USED_CHUNK_MISSING", "warning",
                    "RAG context가 있었지만 used_chunk_ids가 비어 있습니다.",
                    location=f"{loc}.used_chunk_ids",
                    auto_fixable=True,
                )
            if not place.source_urls:
                report.add_issue(
                    "PLACE_SOURCE_URLS_MISSING", "warning",
                    "RAG context가 있었지만 source_urls가 비어 있습니다.",
                    location=f"{loc}.source_urls",
                    auto_fixable=True,
                )

        # E. fact / legend / symbolic 표현 검증
        self._check_factuality_expression(
            story_text=story_text,
            rag_for_place=rag_for_place,
            report=report,
            loc=loc,
        )

        # F. 일반적 문장 검증
        self._check_genericness(
            story_text=story_text,
            mission_blob=(mission.mission_instruction if mission else "") or "",
            place_name=place.place_name,
            rag_for_place=rag_for_place,
            report=report,
            loc=loc,
        )

        # G. 154-prompt-pass: place specificity & mission title genericness
        self._check_place_specificity(
            place=place,
            mission=mission,
            rag_for_place=rag_for_place,
            report=report,
            loc=loc,
        )

        # H. Atoria-specific quest / photo-clear / safety rules.
        self._check_atoria_quest_quality(
            place=place,
            mission=mission,
            report=report,
            loc=loc,
            user_aliases=user_aliases,
        )

    # ---- E. factuality expression --------------------------------------

    @staticmethod
    def _check_factuality_expression(
        *,
        story_text: str,
        rag_for_place: RagContextByPlace | None,
        report: StoryQualityReport,
        loc: str,
    ) -> None:
        if not story_text or rag_for_place is None:
            return
        has_legendish = bool(rag_for_place.story_materials) or any(
            (i.factuality_level in ("legend", "mixed", "literary"))
            for i in rag_for_place.story_materials
        )
        has_symbolic = bool(rag_for_place.symbolic_materials)

        # legend/mixed 문맥에서 단정 어조 사용
        if has_legendish:
            assertive_hits = [p for p in _ASSERTIVE_PHRASES if p in story_text]
            has_legend_phrase = any(p in story_text for p in _LEGEND_FRIENDLY_PHRASES)
            if assertive_hits and not has_legend_phrase:
                report.add_issue(
                    "FACT_LEGEND_CONFUSION", "warning",
                    f"설화/전승 자료가 있는데 단정 어조 사용: {assertive_hits}",
                    location=f"{loc}.story_fragment",
                )

        # symbolic 자료가 있을 때 "상징/상상" 어구가 전혀 없으면 경고
        if has_symbolic:
            if not any(p in story_text for p in _SYMBOLIC_FRIENDLY_PHRASES):
                report.add_issue(
                    "SYMBOLIC_AS_FACT", "warning",
                    "symbolic_material이 있는데 상징적 표현 어구가 보이지 않습니다.",
                    location=f"{loc}.story_fragment",
                )

    # ---- F. genericness ------------------------------------------------

    @staticmethod
    def _check_genericness(
        *,
        story_text: str,
        mission_blob: str,
        place_name: str,
        rag_for_place: RagContextByPlace | None,
        report: StoryQualityReport,
        loc: str,
    ) -> None:
        text = f"{story_text} {mission_blob}".strip()
        if not text:
            return
        if any(s in text for s in _GENERIC_SENTENCES):
            report.add_issue(
                "STORY_TOO_GENERIC", "warning",
                "흔한 일반 문장이 포함되어 있습니다.", location=loc,
            )
            return
        unique_keywords: set[str] = set()
        if place_name:
            unique_keywords.add(place_name)
        if rag_for_place:
            for bucket in (
                rag_for_place.fact_contexts,
                rag_for_place.story_materials,
                rag_for_place.symbolic_materials,
            ):
                for it in bucket:
                    if it.heritage_name:
                        unique_keywords.add(it.heritage_name)
                    unique_keywords.update(k for k in it.motifs if k)
                    unique_keywords.update(h for h in it.mission_hooks if h)
        if unique_keywords and not any(k for k in unique_keywords if k and k in text):
            report.add_issue(
                "STORY_TOO_GENERIC", "warning",
                "장소 고유 키워드(이름·모티프·hook)가 본문/미션에 보이지 않습니다.",
                location=loc,
            )

    # ---- G. 154-prompt-pass place specificity --------------------------

    @staticmethod
    def _check_place_specificity(
        *,
        place: PlaceStoryDraft,
        mission,  # type: ignore[no-untyped-def]
        rag_for_place: RagContextByPlace | None,
        report: StoryQualityReport,
        loc: str,
    ) -> None:
        keywords = _collect_place_keywords(place.place_name, rag_for_place)
        keywords.discard("")
        # Keywords beyond just the place_name itself — only these count toward
        # "RAG-derived specificity" (so a place without RAG isn't penalised for
        # having no extra hits to land).
        extra_keywords = {k for k in keywords if k and k != place.place_name}
        require_two_hits = bool(extra_keywords)

        story_text = (place.story_fragment or "").strip()
        if story_text and keywords:
            hits = _count_keyword_hits(story_text, keywords)
            place_name_in = bool(place.place_name and place.place_name in story_text)
            if not place_name_in or (require_two_hits and hits < 2):
                report.add_issue(
                    "STORY_NOT_PLACE_SPECIFIC",
                    "warning",
                    "story_fragment 에 장소명 또는 RAG 고유 키워드 반영이 부족합니다.",
                    location=f"{loc}.story_fragment",
                )

        if mission is None:
            return

        # mission title genericness
        title = (mission.mission_title or "").strip()
        if title:
            generic_token_hits = sum(1 for tok in _GENERIC_TITLE_TOKENS if tok in title)
            place_specific_in_title = bool(
                place.place_name and place.place_name in title
            ) or any(k in title for k in extra_keywords)
            if generic_token_hits >= 1 and not place_specific_in_title:
                report.add_issue(
                    "MISSION_TITLE_TOO_GENERIC", "warning",
                    f"mission_title 이 일반적입니다 (generic 단어={generic_token_hits}, "
                    f"장소 고유 키워드 없음): '{title}'",
                    location=f"{loc}.mission.mission_title",
                )

        # mission place specificity
        instr = (mission.mission_instruction or "").strip()
        blob = f"{title}\n{instr}"
        if keywords and blob.strip():
            hits = _count_keyword_hits(blob, keywords)
            place_name_in = bool(place.place_name and place.place_name in blob)
            if not place_name_in or (require_two_hits and hits < 2):
                report.add_issue(
                    "MISSION_NOT_PLACE_SPECIFIC",
                    "error" if not place_name_in else "warning",
                    "mission(title+instruction) 에 장소명 또는 RAG 고유 키워드 반영이 부족합니다.",
                    location=f"{loc}.mission",
                )

    # ---- H. Atoria quest quality ---------------------------------------

    @staticmethod
    def _check_atoria_quest_quality(
        *,
        place: PlaceStoryDraft,
        mission,  # type: ignore[no-untyped-def]
        report: StoryQualityReport,
        loc: str,
        user_aliases: set[str],
    ) -> None:
        place_name = (place.place_name or "").strip()
        story_text = (place.story_fragment or "").strip()

        if story_text and place_name and place_name not in story_text[:140]:
            report.add_issue(
                "PLACE_NAME_NOT_EARLY",
                "warning",
                "story_fragment 초반에 전체 장소명이 보이지 않습니다.",
                location=f"{loc}.story_fragment",
            )

        expository_hits = [p for p in _EXPOSITORY_STORY_PHRASES if p in story_text]
        if expository_hits:
            report.add_issue(
                "STORY_EXPOSITORY_TONE",
                "warning",
                f"story_fragment가 설명문 어투에 가깝습니다: {expository_hits}",
                location=f"{loc}.story_fragment",
            )

        if story_text:
            scene_hits = [p for p in _SCENE_DISCOVERY_TERMS if p in story_text]
            object_hits = [p for p in _SCENE_VISIBLE_OBJECT_TERMS if p in story_text]
            if user_aliases and not any(alias in story_text for alias in user_aliases):
                report.add_issue(
                    "STORY_SCENE_WEAK",
                    "warning",
                    "story_fragment에 사용자 이름이나 자연스러운 별칭이 보이지 않습니다.",
                    location=f"{loc}.story_fragment",
                )
            if not scene_hits or not object_hits:
                report.add_issue(
                    "STORY_SCENE_WEAK",
                    "warning",
                    f"story_fragment가 현장 장면보다 설명에 가깝습니다 "
                    f"(발견어={scene_hits}, 구체물={object_hits}).",
                    location=f"{loc}.story_fragment",
                )

        if mission is None:
            return

        title = (mission.mission_title or "").strip()
        instruction = (mission.mission_instruction or "").strip()
        verification_hint = (mission.verification_hint or "").strip()
        mission_blob = f"{title}\n{instruction}\n{verification_hint}"

        photo_afterthought_hits = [
            p for p in _PHOTO_AFTERTHOUGHT_PHRASES if p in mission_blob
        ]
        if photo_afterthought_hits:
            report.add_issue(
                "MISSION_PHOTO_AFTERTHOUGHT",
                "error",
                f"사진 인증이 미션 클리어 조건이 아니라 사후 촬영 지시처럼 보입니다: "
                f"{photo_afterthought_hits}",
                location=f"{loc}.mission.mission_instruction",
            )

        if instruction and not any(term in mission_blob for term in _CLEAR_CONDITION_TERMS):
            report.add_issue(
                "MISSION_CLEAR_CONDITION_MISSING",
                "error",
                "mission_instruction에 사진 클리어 조건이나 성공 기준이 보이지 않습니다.",
                location=f"{loc}.mission.mission_instruction",
            )

        passive_hits = [p for p in _PASSIVE_MISSION_PHRASES if p in instruction]
        if instruction and (not _has_field_action(instruction) or passive_hits):
            report.add_issue(
                "MISSION_NOT_GAMEFUL",
                "error",
                "mission_instruction에 사용자가 직접 해결할 현장 행동이 부족합니다."
                + (f" 수동 표현={passive_hits}" if passive_hits else ""),
                location=f"{loc}.mission.mission_instruction",
            )

        if instruction and not any(term in mission_blob for term in _GAMEFUL_TERMS):
            report.add_issue(
                "MISSION_NOT_GAMEFUL",
                "warning",
                "mission_instruction이 퀘스트 보상이나 게임적 성공감을 충분히 드러내지 않습니다.",
                location=f"{loc}.mission.mission_instruction",
            )

        if not verification_hint:
            report.add_issue(
                "MISSION_VERIFICATION_HINT_MISSING",
                "warning",
                "verification_hint가 비어 있습니다.",
                location=f"{loc}.mission.verification_hint",
                auto_fixable=True,
            )

        if "석빙고" in place_name or "석빙고" in mission_blob:
            forbidden_hits = [
                term for term in _SEOKBINGGO_FORBIDDEN_TERMS if term in mission_blob
            ]
            if forbidden_hits:
                report.add_issue(
                    "FORBIDDEN_ACCESS_INSTRUCTION",
                    "error",
                    f"석빙고 접근 제한에 맞지 않는 미션 표현입니다: {forbidden_hits}",
                    location=f"{loc}.mission",
                )

        if "대웅전" in place_name or "대웅전" in mission_blob:
            forbidden_hits = [
                term for term in _DAEUNGJEON_FORBIDDEN_TERMS if term in mission_blob
            ]
            if forbidden_hits:
                report.add_issue(
                    "FORBIDDEN_ACCESS_INSTRUCTION",
                    "error",
                    f"불국사 대웅전 촬영/접근 제한에 맞지 않는 미션 표현입니다: {forbidden_hits}",
                    location=f"{loc}.mission",
                )

    # ---- D. global RAG usage -------------------------------------------

    @staticmethod
    def _check_global_rag_usage(
        draft: StoryIntroDraft,
        request: StoryIntroGenerationRequest,
        report: StoryQualityReport,
    ) -> None:
        any_rag_provided = any(
            (c.fact_contexts or c.story_materials or c.symbolic_materials)
            for c in request.rag_contexts
        )
        if any_rag_provided and not draft.used_chunk_ids:
            report.add_issue(
                "MISSING_RAG_USAGE", "warning",
                "RAG context가 제공되었지만 draft.used_chunk_ids가 비어 있습니다.",
                location="used_chunk_ids",
                auto_fixable=True,
            )
        if any_rag_provided and not draft.source_urls:
            report.add_issue(
                "MISSING_SOURCE_TRACE", "warning",
                "RAG context가 제공되었지만 draft.source_urls가 비어 있습니다.",
                location="source_urls",
                auto_fixable=True,
            )

    @staticmethod
    def _check_global_rag_term_underuse(
        draft: StoryIntroDraft,
        request: StoryIntroGenerationRequest,
        report: StoryQualityReport,
    ) -> None:
        """RAG 의 motifs / related_people / related_places 가 실제 본문에 거의
        반영되지 않은 경우 RAG_CONTEXT_UNDERUSED 를 띄운다."""
        if not request.rag_contexts:
            return
        rag_terms: set[str] = set()
        for ctx in request.rag_contexts:
            for bucket in (ctx.fact_contexts, ctx.story_materials, ctx.symbolic_materials):
                for it in bucket:
                    rag_terms.update(m for m in (it.motifs or []) if m)
                    rag_terms.update(m for m in (it.mission_hooks or []) if m)
        if not rag_terms:
            return
        body_parts: list[str] = []
        if draft.intro:
            body_parts.append(draft.intro)
        if draft.outro:
            body_parts.append(draft.outro)
        for p in draft.places:
            if p.story_fragment:
                body_parts.append(p.story_fragment)
            if p.mission and p.mission.mission_instruction:
                body_parts.append(p.mission.mission_instruction)
            if p.mission and p.mission.mission_title:
                body_parts.append(p.mission.mission_title)
        body = "\n".join(body_parts)
        hit_terms = [t for t in rag_terms if t and t in body]
        if not hit_terms:
            report.add_issue(
                "RAG_CONTEXT_UNDERUSED", "warning",
                f"RAG 의 motifs / related_people / related_places ({len(rag_terms)}개) 중 "
                "본문에 반영된 단어가 없습니다.",
                location="<global>",
            )


def _or(value: int | None, fallback: int) -> int:
    return value if value is not None else fallback
