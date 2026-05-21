"""
E-book draft schemas (156).

This is the *content* contract — request shape, page DTOs, draft envelope.
The endpoint (157) and job-status (158) ride on top later. No file output here.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


PageType = Literal[
    "cover",
    "table_of_contents",
    "prologue",
    "place_story",
    "mission_result",
    "reflection",
    "epilogue",
    "sources",
]


def _utcnow_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


# ============================================================
# request inputs
# ============================================================


class EbookUserProfile(BaseModel):
    user_id: str | None = None
    nickname: str | None = None
    age_group: str | None = None
    companion_type: str | None = None
    story_theme: str | None = None
    story_tone: str | None = None
    language: str = "ko"


class EbookPlaceInput(BaseModel):
    place_id: str | int | None = None
    place_name: str
    sequence: int
    address: str | None = None
    category: str | None = None
    latitude: float | None = None
    longitude: float | None = None

    @field_validator("place_name")
    @classmethod
    def _name_not_blank(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("place_name은 비어 있을 수 없습니다.")
        return v.strip()

    @field_validator("sequence")
    @classmethod
    def _sequence_at_least_one(cls, v: int) -> int:
        if v < 1:
            raise ValueError("sequence는 1 이상이어야 합니다.")
        return v


class EbookMissionInput(BaseModel):
    sequence: int
    title: str
    description: str
    type: str  # ACTION / PHOTO / QUIZ / CHOICE 등 wire 형태 그대로 받음
    story: str
    place_name: str | None = None
    # Optional per-mission source trace. When present these flow into the
    # corresponding place_story page; otherwise the page falls back to
    # `story_source.used_chunk_ids` / `story_source.source_urls`.
    used_chunk_ids: list[str] = Field(default_factory=list)
    source_urls: list[str] = Field(default_factory=list)


class EbookMissionResultInput(BaseModel):
    sequence: int
    place_id: str | int | None = None
    place_name: str
    mission_title: str | None = None
    completed: bool = False
    user_answer: str | None = None
    photo_urls: list[str] = Field(default_factory=list)
    selected_keywords: list[str] = Field(default_factory=list)
    completed_at: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class EbookStorySourceInput(BaseModel):
    intro: str
    missions: list[EbookMissionInput]
    outro: str
    used_chunk_ids: list[str] = Field(default_factory=list)
    source_urls: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)

    @field_validator("intro", "outro")
    @classmethod
    def _not_blank(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("intro / outro 는 비어 있을 수 없습니다.")
        return v

    @field_validator("missions")
    @classmethod
    def _missions_not_empty(cls, v: list[EbookMissionInput]) -> list[EbookMissionInput]:
        if not v:
            raise ValueError("missions 는 비어 있을 수 없습니다.")
        return v


class EbookDraftOptions(BaseModel):
    title_style: Literal["personalized", "simple", "adventure"] = "personalized"
    include_cover: bool = True
    include_table_of_contents: bool = True
    include_mission_results: bool = True
    include_sources: bool = True
    include_image_prompts: bool = True
    include_incomplete_missions: bool = False
    include_missing_result_note_in_body: bool = False
    max_page_body_chars: int = 900
    target_reading_level: Literal["family", "child", "teen", "adult"] = "family"
    output_format: Literal["draft_json"] = "draft_json"

    @field_validator("max_page_body_chars")
    @classmethod
    def _body_chars_range(cls, v: int) -> int:
        if not 200 <= v <= 4000:
            raise ValueError("max_page_body_chars 는 200~4000 사이여야 합니다.")
        return v


class EbookDraftGenerationRequest(BaseModel):
    request_id: str | None = None
    user_profile: EbookUserProfile
    places: list[EbookPlaceInput]
    story_source: EbookStorySourceInput
    mission_results: list[EbookMissionResultInput] = Field(default_factory=list)
    options: EbookDraftOptions = Field(default_factory=EbookDraftOptions)
    metadata: dict[str, Any] = Field(default_factory=dict)

    @field_validator("places")
    @classmethod
    def _places_not_empty(cls, v: list[EbookPlaceInput]) -> list[EbookPlaceInput]:
        if not v:
            raise ValueError("places 는 비어 있을 수 없습니다.")
        return v


# ============================================================
# response drafts
# ============================================================


class EbookCoverDraft(BaseModel):
    title: str
    subtitle: str | None = None
    author_label: str | None = None
    theme: str | None = None
    image_prompt: str | None = None


class EbookPageDraft(BaseModel):
    page_number: int
    page_type: PageType
    title: str
    subtitle: str | None = None
    place_name: str | None = None
    body: str = ""
    mission_title: str | None = None
    mission_result_summary: str | None = None
    image_urls: list[str] = Field(default_factory=list)
    image_prompt: str | None = None
    used_chunk_ids: list[str] = Field(default_factory=list)
    source_urls: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class EbookDraft(BaseModel):
    draft_id: str
    title: str
    subtitle: str | None = None
    cover: EbookCoverDraft | None = None
    pages: list[EbookPageDraft]
    page_count: int
    source_urls: list[str] = Field(default_factory=list)
    used_chunk_ids: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class EbookDraftError(BaseModel):
    code: str
    message: str


class EbookDraftGenerationResponse(BaseModel):
    success: bool
    data: EbookDraft | None = None
    error: EbookDraftError | None = None
    timestamp: str = Field(default_factory=_utcnow_iso)
