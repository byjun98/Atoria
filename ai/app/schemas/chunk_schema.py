"""
RAG chunk schemas.

`ChunkingSourceDocument` is the normalised input to the semantic chunker.
`RagChunk` is the output emitted per chunk, ready for embedding / DB load
in a later task. This module does not touch DB / embeddings / LLM.
"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field, field_validator, model_validator


class ChunkingSourceDocument(BaseModel):
    """Common normalised document fed into the chunker.

    Both heritage_context (사실) and heritage_legend_materials (설화/상징)
    records collapse into this shape via SourceDocumentNormalizer.
    """

    source_record_id: str
    source_type: str
    source_site: str | None = None
    source_urls: list[str] = Field(default_factory=list)

    content_role: str

    heritage_name: str
    title: str | None = None
    text: str

    factuality_level: str | None = None
    narrative_type: str | None = None
    folklore_status: str | None = None
    source_confidence: str | None = None
    needs_review: bool = False

    era: str | None = None
    region: str | None = None
    related_heritages: list[str] = Field(default_factory=list)
    related_people: list[str] = Field(default_factory=list)
    related_places: list[str] = Field(default_factory=list)
    motifs: list[str] = Field(default_factory=list)
    tone_tags: list[str] = Field(default_factory=list)
    story_hooks: list[str] = Field(default_factory=list)
    mission_keywords: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class RagChunk(BaseModel):
    """One semantic chunk ready for embedding / RAG retrieval."""

    chunk_id: str
    source_record_id: str
    source_type: str
    source_site: str | None = None
    source_urls: list[str] = Field(default_factory=list)

    content_role: str

    heritage_name: str
    related_heritages: list[str] = Field(default_factory=list)
    related_people: list[str] = Field(default_factory=list)
    related_places: list[str] = Field(default_factory=list)

    title: str | None = None
    chunk_index: int

    factuality_level: str | None = None
    narrative_type: str | None = None
    folklore_status: str | None = None
    source_confidence: str | None = None
    needs_review: bool = False

    era: str | None = None
    region: str | None = None
    motifs: list[str] = Field(default_factory=list)
    tone_tags: list[str] = Field(default_factory=list)
    story_hooks: list[str] = Field(default_factory=list)
    mission_keywords: list[str] = Field(default_factory=list)
    mission_hooks: list[str] = Field(default_factory=list)

    raw_text: str
    context_text: str
    char_count: int

    metadata: dict[str, Any] = Field(default_factory=dict)

    @field_validator("raw_text")
    @classmethod
    def _raw_text_not_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("raw_text must not be empty")
        return v

    @model_validator(mode="after")
    def _check_char_count(self) -> "RagChunk":
        if self.char_count != len(self.raw_text):
            raise ValueError(
                f"char_count ({self.char_count}) must equal len(raw_text) ({len(self.raw_text)})"
            )
        return self
