"""
Repository for RagSourceDocument / RagChunk persistence.

Idempotent upsert by `source_record_id` (documents) and `chunk_id` (chunks).
Does NOT call OpenAI / generate embeddings. The `update_chunk_embedding`
method is provided for the next ticket (148) to use.
"""
from __future__ import annotations

from collections import Counter
from datetime import datetime, timezone
from typing import Any, Iterable

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.models.rag_chunk import RagChunk
from app.db.models.rag_document import RagSourceDocument


# Fields shared between the input chunk dict and BOTH tables.
_DOC_FIELDS = (
    "source_type", "source_site", "source_urls", "content_role",
    "heritage_name", "title",
    "factuality_level", "narrative_type", "folklore_status", "source_confidence",
    "needs_review", "era", "region",
    "related_heritages", "related_people", "related_places",
    "motifs", "tone_tags", "story_hooks", "mission_keywords",
)

_CHUNK_FIELDS = _DOC_FIELDS + (
    "chunk_index", "mission_hooks", "raw_text", "context_text", "char_count",
    "embedding_model",
)

# Embedding states that should NOT be downgraded by re-ingestion.
_PROTECTED_STATUSES = {"EMBEDDED"}


def _pick(d: dict, keys: Iterable[str]) -> dict:
    return {k: d[k] for k in keys if k in d}


class RagChunkRepository:
    def __init__(self, session: Session) -> None:
        self.session = session

    # ---- documents ------------------------------------------------------

    def upsert_source_document(self, chunk_data: dict) -> tuple[RagSourceDocument, bool]:
        """Insert-or-update a source document keyed by source_record_id.

        Returns ``(document, was_inserted)``.
        """
        record_id = chunk_data["source_record_id"]
        existing = self.session.execute(
            select(RagSourceDocument).where(
                RagSourceDocument.source_record_id == record_id
            )
        ).scalar_one_or_none()

        fields = _pick(chunk_data, _DOC_FIELDS)
        meta = chunk_data.get("metadata") or {}

        if existing is None:
            doc = RagSourceDocument(
                source_record_id=record_id,
                **fields,
                extra_metadata=meta,
            )
            self.session.add(doc)
            self.session.flush()
            return doc, True

        for k, v in fields.items():
            setattr(existing, k, v)
        existing.extra_metadata = meta
        existing.updated_at = datetime.now(timezone.utc)
        self.session.flush()
        return existing, False

    # ---- chunks ---------------------------------------------------------

    def upsert_chunk(
        self, source_document: RagSourceDocument, chunk_data: dict
    ) -> tuple[RagChunk, bool]:
        chunk_id = chunk_data["chunk_id"]
        existing = self.session.execute(
            select(RagChunk).where(RagChunk.chunk_id == chunk_id)
        ).scalar_one_or_none()

        fields = _pick(chunk_data, _CHUNK_FIELDS)
        # source_record_id is required on the row even though it's also on the doc
        fields["source_record_id"] = chunk_data["source_record_id"]
        meta = chunk_data.get("metadata") or {}
        incoming_status = chunk_data.get("embedding_status") or "READY_FOR_EMBEDDING"

        if existing is None:
            chunk = RagChunk(
                chunk_id=chunk_id,
                source_document_id=source_document.id,
                embedding_status=incoming_status,
                extra_metadata=meta,
                **fields,
            )
            self.session.add(chunk)
            self.session.flush()
            return chunk, True

        for k, v in fields.items():
            setattr(existing, k, v)
        existing.extra_metadata = meta
        existing.source_document_id = source_document.id
        # Preserve EMBEDDED state — embedding columns must not be touched here.
        # TODO(148): if context_text changed, reset to READY_FOR_EMBEDDING.
        if existing.embedding_status not in _PROTECTED_STATUSES:
            existing.embedding_status = incoming_status
        existing.updated_at = datetime.now(timezone.utc)
        self.session.flush()
        return existing, False

    # ---- queries --------------------------------------------------------

    def get_chunk_by_chunk_id(self, chunk_id: str) -> RagChunk | None:
        return self.session.execute(
            select(RagChunk).where(RagChunk.chunk_id == chunk_id)
        ).scalar_one_or_none()

    def count_chunks(self) -> int:
        return int(self.session.execute(select(func.count(RagChunk.id))).scalar_one())

    def count_by_embedding_status(self) -> dict[str, int]:
        rows = self.session.execute(
            select(RagChunk.embedding_status, func.count(RagChunk.id))
            .group_by(RagChunk.embedding_status)
        ).all()
        return {status: int(n) for status, n in rows}

    # ---- 148 hooks ------------------------------------------------------

    def list_chunks_ready_for_embedding(self, limit: int = 100) -> list[RagChunk]:
        return list(
            self.session.execute(
                select(RagChunk)
                .where(RagChunk.embedding_status == "READY_FOR_EMBEDDING")
                .order_by(RagChunk.id)
                .limit(limit)
            ).scalars()
        )

    def update_chunk_embedding(
        self,
        chunk_id: str,
        embedding: list[float],
        embedding_model: str,
    ) -> RagChunk:
        """Apply embedding + bookkeeping. The 148 task calls this after OpenAI."""
        chunk = self.get_chunk_by_chunk_id(chunk_id)
        if chunk is None:
            raise LookupError(f"chunk_id not found: {chunk_id}")
        chunk.embedding = embedding
        chunk.embedding_model = embedding_model
        chunk.embedding_status = "EMBEDDED"
        chunk.embedded_at = datetime.now(timezone.utc)
        chunk.updated_at = chunk.embedded_at
        self.session.flush()
        return chunk

    def mark_chunk_embedding_failed(
        self,
        chunk_id: str,
        error_message: str | None = None,
    ) -> RagChunk:
        """Mark a chunk as EMBEDDING_FAILED and stash the reason in metadata."""
        chunk = self.get_chunk_by_chunk_id(chunk_id)
        if chunk is None:
            raise LookupError(f"chunk_id not found: {chunk_id}")
        chunk.embedding_status = "EMBEDDING_FAILED"
        if error_message:
            meta = dict(chunk.extra_metadata or {})
            meta["embedding_error"] = error_message[:1000]
            chunk.extra_metadata = meta
        chunk.updated_at = datetime.now(timezone.utc)
        self.session.flush()
        return chunk
