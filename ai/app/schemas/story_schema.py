"""
Schemas for the 151/152/153 story-generation pipeline.

This module defines the request / response contracts and the RAG-context
DTOs that the prompt builder consumes. It does NOT call OpenAI, the DB,
or the RAG search API.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


def _utcnow_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


# ============================================================
# user / course inputs
# ============================================================


class StoryUserProfile(BaseModel):
    nickname: str | None = None
    persona: str | None = None
    companion_type: str | None = None  # family / couple / friends / solo
    story_theme: str | None = None
    story_tone: str | None = None
    age_group: str | None = None  # child / teen / adult / family
    age: int | None = None
    interest_tags: list[str] = Field(default_factory=list)
    tendency_tags: list[str] = Field(default_factory=list)
    language: str = "ko"


class CoursePlaceInput(BaseModel):
    place_id: str | None = None
    place_name: str
    sequence: int
    expected_activity: str | None = None
    user_selected: bool = True
    metadata: dict[str, Any] = Field(default_factory=dict)

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


# ============================================================
# generation options
# ============================================================


OutputFormat = Literal["json"]


class StoryGenerationOptions(BaseModel):
    include_missions: bool = True
    mission_count_per_place: int = 1
    max_intro_chars: int = 800
    max_place_story_chars: int = 700
    max_outro_chars: int = 700
    use_rag: bool = True
    output_format: OutputFormat = "json"

    @field_validator("mission_count_per_place")
    @classmethod
    def _mission_count_range(cls, v: int) -> int:
        if not 1 <= v <= 3:
            raise ValueError("mission_count_per_place는 1 이상 3 이하여야 합니다.")
        return v

    @field_validator("max_intro_chars", "max_place_story_chars", "max_outro_chars")
    @classmethod
    def _char_limit_range(cls, v: int) -> int:
        if not 200 <= v <= 2000:
            raise ValueError("문자 수 한도는 200~2000 사이여야 합니다.")
        return v


# ============================================================
# RAG context (149 search result → prompt-friendly DTO)
# ============================================================


ContentRole = Literal["fact_context", "legend_material", "symbolic_material"]


class RagContextItem(BaseModel):
    chunk_id: str
    heritage_name: str
    title: str | None = None
    content_role: ContentRole
    source_type: str
    factuality_level: str | None = None
    source_site: str | None = None
    source_urls: list[str] = Field(default_factory=list)
    context_text: str
    mission_hooks: list[str] = Field(default_factory=list)
    motifs: list[str] = Field(default_factory=list)
    tone_tags: list[str] = Field(default_factory=list)
    distance: float | None = None


class RagContextByPlace(BaseModel):
    place_name: str
    fact_contexts: list[RagContextItem] = Field(default_factory=list)
    story_materials: list[RagContextItem] = Field(default_factory=list)
    symbolic_materials: list[RagContextItem] = Field(default_factory=list)


# ============================================================
# requests
# ============================================================


class WeatherContext(BaseModel):
    temperature: float | None = None
    rainfall: float | None = None
    humidity: float | None = None
    wind_speed: float | None = None
    wind_direction: float | None = None


class StoryIntroGenerationRequest(BaseModel):
    request_id: str | None = None
    user_id: str | None = None
    course_id: str | None = None
    story_session_id: str | None = None
    user_profile: StoryUserProfile
    places: list[CoursePlaceInput]
    options: StoryGenerationOptions = Field(default_factory=StoryGenerationOptions)
    rag_contexts: list[RagContextByPlace] = Field(default_factory=list)
    weather_context: WeatherContext | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)

    @field_validator("places")
    @classmethod
    def _places_not_empty(cls, v: list[CoursePlaceInput]) -> list[CoursePlaceInput]:
        if not v:
            raise ValueError("places는 비어 있을 수 없습니다.")
        return v


class StoryUpdateGenerationRequest(BaseModel):
    """Used by 153 to extend a story session as the user progresses.
    151 only defines the schema; no update logic is implemented yet.
    """
    request_id: str | None = None
    story_session_id: str
    current_place_name: str
    previous_story_state: dict[str, Any] = Field(default_factory=dict)
    completed_mission_result: dict[str, Any] = Field(default_factory=dict)
    user_profile: StoryUserProfile
    rag_contexts: list[RagContextByPlace] = Field(default_factory=list)
    weather_context: WeatherContext | None = None
    options: StoryGenerationOptions = Field(default_factory=StoryGenerationOptions)
    metadata: dict[str, Any] = Field(default_factory=dict)

    @field_validator("current_place_name")
    @classmethod
    def _current_place_not_blank(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("current_place_name은 비어 있을 수 없습니다.")
        return v.strip()


# ============================================================
# response drafts
# ============================================================


MissionType = Literal["observation", "photo", "quiz", "imagination", "route"]


class MissionDraft(BaseModel):
    mission_title: str
    mission_instruction: str
    mission_type: MissionType
    verification_hint: str | None = None
    related_place_name: str
    related_chunk_ids: list[str] = Field(default_factory=list)
    mission_keywords: list[str] = Field(default_factory=list)


class PlaceStoryDraft(BaseModel):
    sequence: int
    place_id: str | None = None
    place_name: str
    story_fragment: str
    mission: MissionDraft | None = None
    used_chunk_ids: list[str] = Field(default_factory=list)
    source_urls: list[str] = Field(default_factory=list)


class StoryIntroDraft(BaseModel):
    title: str
    intro: str
    places: list[PlaceStoryDraft]
    outro: str
    used_chunk_ids: list[str] = Field(default_factory=list)
    source_urls: list[str] = Field(default_factory=list)
    prompt_version: str = "story_intro_v1"
    warnings: list[str] = Field(default_factory=list)


class StoryGenerationError(BaseModel):
    code: str
    message: str


class StoryGenerationResponse(BaseModel):
    success: bool
    data: StoryIntroDraft | None = None
    error: StoryGenerationError | None = None
    timestamp: str = Field(default_factory=_utcnow_iso)
