"""SQLAlchemy model for `rag_chunks` (147)."""
from __future__ import annotations

from datetime import datetime, timezone

from pgvector.sqlalchemy import Vector
from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.config import settings
from app.db.base import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class RagChunk(Base):
    """One semantic chunk; embedding column is nullable until 148 fills it."""

    __tablename__ = "rag_chunks"
    __table_args__ = (
        UniqueConstraint("chunk_id", name="uq_rag_chunks_chunk_id"),
        Index("ix_rag_chunks_source_record_id", "source_record_id"),
        Index("ix_rag_chunks_source_document_id", "source_document_id"),
        Index("ix_rag_chunks_heritage_name", "heritage_name"),
        Index("ix_rag_chunks_source_type", "source_type"),
        Index("ix_rag_chunks_content_role", "content_role"),
        Index("ix_rag_chunks_factuality_level", "factuality_level"),
        Index("ix_rag_chunks_embedding_status", "embedding_status"),
        Index("ix_rag_chunks_heritage_role", "heritage_name", "content_role"),
        Index("ix_rag_chunks_heritage_factuality", "heritage_name", "factuality_level"),
        Index("ix_rag_chunks_related_heritages", "related_heritages", postgresql_using="gin"),
        Index("ix_rag_chunks_related_people", "related_people", postgresql_using="gin"),
        Index("ix_rag_chunks_related_places", "related_places", postgresql_using="gin"),
        Index("ix_rag_chunks_motifs", "motifs", postgresql_using="gin"),
        Index("ix_rag_chunks_mission_keywords", "mission_keywords", postgresql_using="gin"),
        Index("ix_rag_chunks_mission_hooks", "mission_hooks", postgresql_using="gin"),
        Index("ix_rag_chunks_extra_metadata", "extra_metadata", postgresql_using="gin"),
        # NOTE: pgvector HNSW index is created in the Alembic migration
        # via raw SQL (CREATE INDEX ... USING hnsw ... vector_cosine_ops).
        {"schema": settings.RAG_DB_SCHEMA},
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    chunk_id: Mapped[str] = mapped_column(String(256), nullable=False)
    source_document_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey(
            f"{settings.RAG_DB_SCHEMA}.rag_source_documents.id",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    source_record_id: Mapped[str] = mapped_column(String(256), nullable=False)
    chunk_index: Mapped[int] = mapped_column(Integer, nullable=False)

    source_type: Mapped[str] = mapped_column(String(64), nullable=False)
    source_site: Mapped[str | None] = mapped_column(String(128), nullable=True)
    source_urls: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
    content_role: Mapped[str] = mapped_column(String(64), nullable=False)
    heritage_name: Mapped[str] = mapped_column(String(128), nullable=False)
    title: Mapped[str | None] = mapped_column(String(512), nullable=True)

    factuality_level: Mapped[str | None] = mapped_column(String(32), nullable=True)
    narrative_type: Mapped[str | None] = mapped_column(String(64), nullable=True)
    folklore_status: Mapped[str | None] = mapped_column(String(64), nullable=True)
    source_confidence: Mapped[str | None] = mapped_column(String(32), nullable=True)
    needs_review: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)

    era: Mapped[str | None] = mapped_column(String(128), nullable=True)
    region: Mapped[str | None] = mapped_column(String(128), nullable=True)

    related_heritages: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
    related_people: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
    related_places: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
    motifs: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
    tone_tags: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
    story_hooks: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
    mission_keywords: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
    mission_hooks: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)

    raw_text: Mapped[str] = mapped_column(Text, nullable=False)
    context_text: Mapped[str] = mapped_column(Text, nullable=False)
    char_count: Mapped[int] = mapped_column(Integer, nullable=False)

    embedding_status: Mapped[str] = mapped_column(
        String(32), nullable=False, default="READY_FOR_EMBEDDING"
    )
    embedding_model: Mapped[str | None] = mapped_column(String(128), nullable=True)
    embedding = mapped_column(Vector(settings.RAG_EMBEDDING_DIM), nullable=True)
    embedded_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    extra_metadata: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow, onupdate=_utcnow
    )

    source_document = relationship("RagSourceDocument", back_populates="chunks")
