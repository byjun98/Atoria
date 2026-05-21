"""Request/response schemas for the 149 RAG search API."""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator, model_validator

from app.core.config import settings


ContentRole = Literal["fact_context", "legend_material", "symbolic_material"]
FactualityLevel = Literal["history", "legend", "mixed", "symbolic", "literary", "unknown"]


def _utcnow_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


# --- requests ------------------------------------------------------------


class RagSearchRequest(BaseModel):
    query: str
    top_k: int | None = None
    heritage_names: list[str] = Field(default_factory=list)
    content_roles: list[ContentRole] = Field(default_factory=list)
    factuality_levels: list[FactualityLevel] = Field(default_factory=list)
    source_types: list[str] = Field(default_factory=list)
    include_context_text: bool = True
    include_raw_text: bool = False
    distance_threshold: float | None = None

    @field_validator("query")
    @classmethod
    def _query_not_blank(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("query는 비어 있을 수 없습니다.")
        return v.strip()

    @field_validator("top_k")
    @classmethod
    def _top_k_bounds(cls, v: int | None) -> int | None:
        if v is None:
            return None
        if v < 1:
            raise ValueError("top_k는 1 이상이어야 합니다.")
        if v > settings.RAG_SEARCH_MAX_TOP_K:
            raise ValueError(
                f"top_k는 {settings.RAG_SEARCH_MAX_TOP_K} 를 넘을 수 없습니다."
            )
        return v

    @field_validator("distance_threshold")
    @classmethod
    def _threshold_non_negative(cls, v: float | None) -> float | None:
        if v is not None and v < 0:
            raise ValueError("distance_threshold는 0 이상이어야 합니다.")
        return v


class RagSearchByHeritageRequest(BaseModel):
    heritage_name: str
    query: str | None = None
    top_k: int | None = None
    content_roles: list[ContentRole] = Field(default_factory=list)
    factuality_levels: list[FactualityLevel] = Field(default_factory=list)
    include_context_text: bool = True
    include_raw_text: bool = False
    distance_threshold: float | None = None

    @field_validator("heritage_name")
    @classmethod
    def _heritage_not_blank(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("heritage_name는 비어 있을 수 없습니다.")
        return v.strip()

    @model_validator(mode="after")
    def _query_fallback(self) -> "RagSearchByHeritageRequest":
        if self.query is None or not self.query.strip():
            self.query = self.heritage_name
        else:
            self.query = self.query.strip()
        return self


# --- responses -----------------------------------------------------------


class RagSearchResultItem(BaseModel):
    chunk_id: str
    source_record_id: str
    heritage_name: str
    title: str | None = None
    content_role: str
    source_type: str
    factuality_level: str | None = None
    narrative_type: str | None = None
    folklore_status: str | None = None
    source_site: str | None = None
    source_urls: list[str] = Field(default_factory=list)
    distance: float
    similarity: float | None = None
    raw_text: str | None = None
    context_text: str | None = None
    mission_hooks: list[str] = Field(default_factory=list)
    related_heritages: list[str] = Field(default_factory=list)
    related_people: list[str] = Field(default_factory=list)
    related_places: list[str] = Field(default_factory=list)
    motifs: list[str] = Field(default_factory=list)
    tone_tags: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class RagSearchData(BaseModel):
    query: str
    embedding_model: str
    top_k: int
    result_count: int
    results: list[RagSearchResultItem]
    filters: dict[str, Any] = Field(default_factory=dict)


class RagSearchError(BaseModel):
    code: str
    message: str


class RagSearchResponse(BaseModel):
    success: bool
    data: RagSearchData | None = None
    error: RagSearchError | None = None
    timestamp: str = Field(default_factory=_utcnow_iso)
