"""
LLM-driven E-book narrative generation (158).

Calls OpenAI Chat Completion in JSON mode, parses into `EbookNarrativeDraft`,
checks forbidden phrases, retries once if violations are found, then raises
`NarrativeGenerationFailed` on final failure. The job service catches that
and falls back to the deterministic 156 path.
"""
from __future__ import annotations

import json
import logging
from typing import Any

from pydantic import BaseModel, Field, ValidationError

from app.clients.openai_chat_client import OpenAIChatClient
from app.core.config import settings
from app.schemas.story import EbookJobRequest
from app.services.ebook.ebook_narrative_prompt_builder import (
    CHAPTER_OPENING_FORBIDDEN_TOKENS,
    CHAPTER_TITLE_FORBIDDEN_TOKENS,
    EbookNarrativePromptBuilder,
    NARRATIVE_FORBIDDEN_PHRASES,
)

_logger = logging.getLogger(__name__)


# ---- helpers ------------------------------------------------------------

import re

# 한국어 종결 부호 또는 큰따옴표/줄바꿈 직전까지를 첫 문장으로 본다.
_FIRST_SENT_RE = re.compile(r"^(.+?)(?:[.!?。]|$|\n)", re.DOTALL)
_HANGUL_TOKEN_RE = re.compile(r"[가-힣]{2,}")


def _first_sentence(text: str) -> str:
    if not text:
        return ""
    s = text.strip()
    m = _FIRST_SENT_RE.match(s)
    return (m.group(1).strip() if m else s).strip()


def _korean_tokens(text: str) -> list[str]:
    if not text:
        return []
    return _HANGUL_TOKEN_RE.findall(text)


# 이전 chapter 와의 연결을 시사하는 어구 (158-v4).
_CONTINUITY_CUES: tuple[str, ...] = (
    "앞서", "조금 전", "아까", "첫 번째", "두 번째", "세 번째",
    "그 빛", "그 조각", "그 선", "그 무늬", "그 문장",
    "이전", "이어", "다음", "가리킨", "이끌", "따라",
)


def _has_chapter_link_signal(text: str, symbolic_object: str | None) -> bool:
    """chapter text 가 이전 chapter 와 연결되는 신호를 가지고 있으면 True.

    조건 (하나라도 충족):
      - symbolic_object 가 본문에 등장
      - _CONTINUITY_CUES 어구 중 하나가 등장
    """
    if not text:
        return False
    if symbolic_object and symbolic_object.strip() and symbolic_object in text:
        return True
    return any(cue in text for cue in _CONTINUITY_CUES)


def _last_sentence(text: str) -> str:
    """본문의 마지막 문장 (마지막 종결 부호 직전까지의 짧은 토막) 을 반환."""
    if not text:
        return ""
    s = text.strip()
    # remove trailing terminator
    while s and s[-1] in ".!?。":
        s = s[:-1]
    # split on Korean sentence terminators
    parts = re.split(r"[.!?。]", s)
    parts = [p.strip() for p in parts if p.strip()]
    return parts[-1] if parts else s.strip()


def _has_repetitive_pattern(sentences: list[str]) -> bool:
    """문장 끝 마지막 6글자가 3회 이상 같으면 반복 패턴으로 본다."""
    cleaned = [s for s in sentences if s]
    if len(cleaned) < 3:
        return False
    suffixes = [s[-6:] for s in cleaned if len(s) >= 4]
    if len(suffixes) < 3:
        return False
    from collections import Counter
    counts = Counter(suffixes)
    most_common, n = counts.most_common(1)[0]
    return n >= 3 and len(most_common) >= 3


def _has_korean_dialogue(text: str) -> bool:
    """한국어 둥근따옴표 “ ” 한 쌍 이상이 있으면 True. 직선따옴표 \" \" 또는
    홑따옴표 ‘ ’ / ' ' 도 보조로 허용."""
    if not text:
        return False
    pairs = (("“", "”"), ("‘", "’"), ('"', '"'), ("'", "'"))
    for opener, closer in pairs:
        if opener == closer:
            if text.count(opener) >= 2:
                return True
        else:
            if opener in text and closer in text:
                return True
    return False


# ---- internal narrative draft schema ------------------------------------


class EbookNarrativePageBlock(BaseModel):
    title: str
    text: str


class EbookNarrativeChapter(BaseModel):
    sequence: int
    title: str
    text: str
    caption: str | None = None


class EbookNarrativeDraft(BaseModel):
    cover_title: str
    cover_subtitle: str | None = None
    thumbnail_hint: str | None = None
    # 158-v2: 전체 책을 관통하는 상징 오브젝트. 외부 wire response 에는 노출되지 않으며
    # 내부 검증 (intro/chapter/outro 연속성) 용도로만 사용한다.
    symbolic_object: str | None = None
    # 158-v3 (fairy-tale pass): 보조 인물 / 핵심 질문 — 모두 내부 검증용.
    supporting_character: str | None = None
    story_question: str | None = None
    # 158-v4 (continuous-story pass): 옴니버스 방지용 서사 메타데이터 — 내부 검증용.
    story_arc: str | None = None
    continuity_notes: list[str] = Field(default_factory=list)
    intro_page: EbookNarrativePageBlock
    chapters: list[EbookNarrativeChapter] = Field(default_factory=list)
    outro_page: EbookNarrativePageBlock
    back_cover: EbookNarrativePageBlock


# ---- error types --------------------------------------------------------


class NarrativeGenerationFailed(RuntimeError):
    def __init__(self, code: str, message: str, violations: list[str] | None = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.violations = violations or []


# ---- service ------------------------------------------------------------


class EbookNarrativeService:
    def __init__(
        self,
        chat_client: OpenAIChatClient | None = None,
        prompt_builder: EbookNarrativePromptBuilder | None = None,
        max_retries: int | None = None,
    ) -> None:
        self.chat_client = chat_client  # may be None — lazy-construct on first use
        self.prompt_builder = prompt_builder or EbookNarrativePromptBuilder()
        self.max_retries = (
            max_retries if max_retries is not None else settings.EBOOK_NARRATIVE_MAX_RETRIES
        )

    # ---- public --------------------------------------------------------

    def generate_narrative(self, request: EbookJobRequest) -> EbookNarrativeDraft:
        client = self._get_client()
        messages = self.prompt_builder.build_messages(request)

        last_violations: list[str] = []
        for attempt in range(self.max_retries + 1):
            try:
                raw = client.create_json_chat_completion(
                    messages=messages,
                    temperature=settings.EBOOK_NARRATIVE_TEMPERATURE,
                )
            except Exception as e:
                raise NarrativeGenerationFailed(
                    "EBOOK_LLM_API_ERROR", f"OpenAI chat completion failed: {e}"
                ) from e

            try:
                draft = self._parse_and_validate(raw, request)
            except NarrativeGenerationFailed as e:
                last_violations = e.violations
                if attempt >= self.max_retries:
                    raise
                _logger.warning(
                    "ebook_narrative_violation narrative_code=%s",
                    e.code,
                    extra={"narrative_violations": e.violations},
                )
                messages = self.prompt_builder.build_repair_messages(
                    request, e.violations
                )
                continue
            return draft

        # Should never reach here.
        raise NarrativeGenerationFailed(
            "EBOOK_LLM_RETRY_EXHAUSTED",
            "narrative generation retries exhausted",
            last_violations,
        )

    # ---- internal helpers ---------------------------------------------

    def _get_client(self) -> OpenAIChatClient:
        if self.chat_client is None:
            self.chat_client = OpenAIChatClient()
        return self.chat_client

    def _parse_and_validate(
        self, raw: str, request: EbookJobRequest
    ) -> EbookNarrativeDraft:
        if not raw or not raw.strip():
            raise NarrativeGenerationFailed(
                "EBOOK_LLM_EMPTY_RESPONSE", "LLM returned empty content."
            )
        cleaned = self._strip_code_fence(raw)
        try:
            payload = json.loads(cleaned)
        except json.JSONDecodeError as e:
            raise NarrativeGenerationFailed(
                "EBOOK_LLM_INVALID_JSON", f"LLM response is not valid JSON: {e}"
            ) from e
        try:
            draft = EbookNarrativeDraft.model_validate(payload)
        except ValidationError as e:
            raise NarrativeGenerationFailed(
                "EBOOK_LLM_SCHEMA_VALIDATION_ERROR",
                f"narrative response did not match schema: {e}",
            ) from e

        # Ensure chapter sequences cover the request.
        wire_seqs = sorted(c.sequence for c in request.chapters)
        draft_seqs = sorted(c.sequence for c in draft.chapters)
        if draft_seqs != wire_seqs:
            raise NarrativeGenerationFailed(
                "EBOOK_LLM_CHAPTER_MISMATCH",
                f"chapter sequence mismatch: requested={wire_seqs}, returned={draft_seqs}",
            )

        violations = self._collect_violations(draft)
        if violations:
            raise NarrativeGenerationFailed(
                "EBOOK_LLM_FORBIDDEN_PHRASE",
                "narrative response contains forbidden phrases",
                violations,
            )
        return draft

    @staticmethod
    def _strip_code_fence(text: str) -> str:
        s = text.strip()
        if s.startswith("```"):
            # remove leading ``` (optionally ```json) and trailing ```
            first_newline = s.find("\n")
            if first_newline != -1:
                s = s[first_newline + 1 :]
            if s.endswith("```"):
                s = s[:-3]
        return s.strip()

    @staticmethod
    def _collect_violations(draft: EbookNarrativeDraft) -> list[str]:
        violations: list[str] = []
        text_blobs: list[tuple[str, str]] = [
            ("intro_page.text", draft.intro_page.text),
            ("outro_page.text", draft.outro_page.text),
            ("back_cover.text", draft.back_cover.text),
            ("cover_title", draft.cover_title),
            ("cover_subtitle", draft.cover_subtitle or ""),
            ("thumbnail_hint", draft.thumbnail_hint or ""),
            ("intro_page.title", draft.intro_page.title),
            ("outro_page.title", draft.outro_page.title),
            ("back_cover.title", draft.back_cover.title),
        ]
        for ch in draft.chapters:
            text_blobs.append((f"chapters[{ch.sequence}].text", ch.text))
            text_blobs.append((f"chapters[{ch.sequence}].caption", ch.caption or ""))

        # A. 기존 금지 어구
        for location, blob in text_blobs:
            for phrase in NARRATIVE_FORBIDDEN_PHRASES:
                if phrase in blob:
                    violations.append(f"{location} contains '{phrase}'")

        # B. CHAPTER title 추가 금지 (mission-form 단어)
        for ch in draft.chapters:
            for token in CHAPTER_TITLE_FORBIDDEN_TOKENS:
                if token in ch.title:
                    violations.append(
                        f"chapters[{ch.sequence}].title contains mission-form '{token}'"
                    )

        # C. 158-v2: chapter 첫 문장이 장소 설명문 톤이면 violation
        for ch in draft.chapters:
            first = _first_sentence(ch.text)
            if not first:
                continue
            for token in CHAPTER_OPENING_FORBIDDEN_TOKENS:
                if token in first:
                    violations.append(
                        f"EBOOK_LLM_EXPOSITION_OPENING: chapters[{ch.sequence}].text "
                        f"first sentence is exposition-styled (contains '{token}')"
                    )
                    break
            # 첫 문장이 "{place_name}는 …" / "{place_name}은 …" 패턴인지 보조 검사
            # (place_name 자체를 모르므로 chapter title 의 한국어 토큰 중 길이≥2 인 토큰을 후보로)
            place_candidates = _korean_tokens(ch.title)
            for cand in place_candidates:
                if first.startswith(cand + "는") or first.startswith(cand + "은"):
                    violations.append(
                        f"EBOOK_LLM_EXPOSITION_OPENING: chapters[{ch.sequence}].text "
                        f"first sentence starts with place-as-subject '{cand}…'"
                    )
                    break

        # C2. 158-v3: 각 chapter 에 한국어 둥근따옴표 대화 “…” 1쌍 이상
        for ch in draft.chapters:
            if not _has_korean_dialogue(ch.text):
                violations.append(
                    f"EBOOK_LLM_DIALOGUE_MISSING: chapters[{ch.sequence}].text "
                    "lacks Korean dialogue ('“…”' pair)"
                )

        # C3. 158-v3: 각 chapter 가 3문단 이상 (\\n\\n 기준)
        for ch in draft.chapters:
            paragraphs = [p for p in (ch.text or "").split("\n\n") if p.strip()]
            if len(paragraphs) < 3:
                violations.append(
                    f"EBOOK_LLM_CHAPTER_TOO_FLAT: chapters[{ch.sequence}].text has "
                    f"{len(paragraphs)} paragraph(s); need ≥ 3"
                )

        # D. 158-v2: symbolic_object 사용 검사
        sym = (draft.symbolic_object or "").strip()
        if not sym:
            violations.append(
                "EBOOK_LLM_SYMBOLIC_OBJECT_MISSING: symbolic_object is empty"
            )
        else:
            if sym not in draft.intro_page.text:
                violations.append(
                    f"EBOOK_LLM_SYMBOLIC_OBJECT_UNDERUSED: intro_page.text "
                    f"does not mention '{sym}'"
                )
            for ch in draft.chapters:
                if sym not in ch.text:
                    violations.append(
                        f"EBOOK_LLM_SYMBOLIC_OBJECT_UNDERUSED: chapters[{ch.sequence}].text "
                        f"does not mention '{sym}'"
                    )
            if (sym not in draft.outro_page.text) and (sym not in draft.back_cover.text):
                violations.append(
                    "EBOOK_LLM_SYMBOLIC_OBJECT_UNDERUSED: neither outro_page.text "
                    f"nor back_cover.text mentions '{sym}'"
                )

        # E. 158-v3: supporting_character 검사 (warning level — retry 트리거)
        sup = (draft.supporting_character or "").strip()
        if not sup:
            violations.append(
                "EBOOK_LLM_SUPPORTING_CHARACTER_MISSING: supporting_character is empty"
            )

        # F. 158-v3: story_question 검사 (intro 에 등장 / outro 에 회수)
        question = (draft.story_question or "").strip()
        if not question:
            violations.append(
                "EBOOK_LLM_STORY_QUESTION_MISSING: story_question is empty"
            )

        # G. 158-v4: continuity_notes 검사 — chapters 가 2개 이상이면 최소 N-1 개
        n_ch = len(draft.chapters)
        if n_ch >= 2:
            non_empty_notes = [
                n for n in (draft.continuity_notes or []) if n and n.strip()
            ]
            if len(non_empty_notes) < n_ch - 1:
                violations.append(
                    f"EBOOK_LLM_CONTINUITY_MISSING: continuity_notes has "
                    f"{len(non_empty_notes)} non-empty entries; need ≥ {n_ch - 1}"
                )

        # H. 158-v4: chapter 2 이후에 이전 단서·연결 표현 / symbolic_object 등장
        for idx, ch in enumerate(sorted(draft.chapters, key=lambda c: c.sequence)):
            if idx == 0:
                continue
            if not _has_chapter_link_signal(ch.text, sym):
                violations.append(
                    f"EBOOK_LLM_CHAPTER_LINK_MISSING: chapters[{ch.sequence}].text "
                    "lacks a link expression to the previous chapter "
                    "(no symbolic_object reference and no continuity cue word)"
                )

        # I. 158-v4: chapter 마지막 문장 패턴이 3회 이상 동일하면 violation
        if n_ch >= 3:
            tails = [_last_sentence(ch.text) for ch in draft.chapters]
            if _has_repetitive_pattern(tails):
                violations.append(
                    "EBOOK_LLM_REPETITIVE_CHAPTER_PATTERN: chapter endings repeat the "
                    "same sentence pattern ≥ 3 times"
                )

        return violations
