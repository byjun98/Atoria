"""SQLAlchemy model for `rag_source_documents` (147)."""
from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    Index,
    String,
    UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.config import settings
from app.db.base import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class RagSourceDocument(Base):
    """One source record (heritage_context fact OR legend material)."""

    __tablename__ = "rag_source_documents"
    __table_args__ = (
        UniqueConstraint("source_record_id", name="uq_rag_source_documents_source_record_id"),
        Index("ix_rag_source_documents_heritage_name", "heritage_name"),
        Index("ix_rag_source_documents_source_type", "source_type"),
        Index("ix_rag_source_documents_content_role", "content_role"),
        Index("ix_rag_source_documents_factuality_level", "factuality_level"),
        Index(
            "ix_rag_source_documents_related_heritages",
            "related_heritages",
            postgresql_using="gin",
        ),
        Index(
            "ix_rag_source_documents_related_people",
            "related_people",
            postgresql_using="gin",
        ),
        Index(
            "ix_rag_source_documents_related_places",
            "related_places",
            postgresql_using="gin",
        ),
        Index("ix_rag_source_documents_motifs", "motifs", postgresql_using="gin"),
        Index("ix_rag_source_documents_extra_metadata", "extra_metadata", postgresql_using="gin"),
        {"schema": settings.RAG_DB_SCHEMA},
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    source_record_id: Mapped[str] = mapped_column(String(256), nullable=False)
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

    # 'metadata' is reserved by SQLAlchemy's Declarative — store under 'extra_metadata'.
    # 'metadata' is reserved by SQLAlchemy Declarative — use 'extra_metadata' as DB col too.
    extra_metadata: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow, onupdate=_utcnow
    )

    chunks = relationship(
        "RagChunk",
        back_populates="source_document",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )
