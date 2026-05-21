"""create rag pgvector tables (147)

Revision ID: 20260505_0001
Revises:
Create Date: 2026-05-05

NOTE: Embedding column dimension is hard-coded to 1536 here. If
``settings.RAG_EMBEDDING_DIM`` changes, generate a new migration
(ALTER TABLE ... ALTER COLUMN embedding TYPE vector(N)).
"""
from __future__ import annotations

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from pgvector.sqlalchemy import Vector
from sqlalchemy.dialects.postgresql import JSONB

from app.core.config import settings


# revision identifiers, used by Alembic.
revision: str = "20260505_0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


SCHEMA = settings.RAG_DB_SCHEMA
EMBED_DIM = settings.RAG_EMBEDDING_DIM


def _qualified(table: str) -> str:
    if SCHEMA and SCHEMA != "public":
        return f'"{SCHEMA}"."{table}"'
    return f'"{table}"'


def upgrade() -> None:
    # 1) Extensions ---------------------------------------------------------
    op.execute("CREATE EXTENSION IF NOT EXISTS vector;")
    op.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;")

    if SCHEMA and SCHEMA != "public":
        op.execute(f'CREATE SCHEMA IF NOT EXISTS "{SCHEMA}";')

    # 2) rag_source_documents ---------------------------------------------
    op.create_table(
        "rag_source_documents",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("source_record_id", sa.String(256), nullable=False),
        sa.Column("source_type", sa.String(64), nullable=False),
        sa.Column("source_site", sa.String(128), nullable=True),
        sa.Column("source_urls", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("content_role", sa.String(64), nullable=False),
        sa.Column("heritage_name", sa.String(128), nullable=False),
        sa.Column("title", sa.String(512), nullable=True),
        sa.Column("factuality_level", sa.String(32), nullable=True),
        sa.Column("narrative_type", sa.String(64), nullable=True),
        sa.Column("folklore_status", sa.String(64), nullable=True),
        sa.Column("source_confidence", sa.String(32), nullable=True),
        sa.Column("needs_review", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("era", sa.String(128), nullable=True),
        sa.Column("region", sa.String(128), nullable=True),
        sa.Column("related_heritages", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("related_people", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("related_places", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("motifs", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("tone_tags", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("story_hooks", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("mission_keywords", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("extra_metadata", JSONB, nullable=False, server_default=sa.text("'{}'::jsonb")),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False,
                  server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False,
                  server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.UniqueConstraint("source_record_id", name="uq_rag_source_documents_source_record_id"),
        schema=SCHEMA,
    )
    op.create_index("ix_rag_source_documents_heritage_name", "rag_source_documents",
                    ["heritage_name"], schema=SCHEMA)
    op.create_index("ix_rag_source_documents_source_type", "rag_source_documents",
                    ["source_type"], schema=SCHEMA)
    op.create_index("ix_rag_source_documents_content_role", "rag_source_documents",
                    ["content_role"], schema=SCHEMA)
    op.create_index("ix_rag_source_documents_factuality_level", "rag_source_documents",
                    ["factuality_level"], schema=SCHEMA)
    for col in ("related_heritages", "related_people", "related_places", "motifs", "extra_metadata"):
        op.create_index(
            f"ix_rag_source_documents_{col}",
            "rag_source_documents",
            [col],
            schema=SCHEMA,
            postgresql_using="gin",
        )

    # 3) rag_chunks --------------------------------------------------------
    op.create_table(
        "rag_chunks",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("chunk_id", sa.String(256), nullable=False),
        sa.Column(
            "source_document_id",
            sa.BigInteger,
            sa.ForeignKey(f"{SCHEMA}.rag_source_documents.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("source_record_id", sa.String(256), nullable=False),
        sa.Column("chunk_index", sa.Integer, nullable=False),
        sa.Column("source_type", sa.String(64), nullable=False),
        sa.Column("source_site", sa.String(128), nullable=True),
        sa.Column("source_urls", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("content_role", sa.String(64), nullable=False),
        sa.Column("heritage_name", sa.String(128), nullable=False),
        sa.Column("title", sa.String(512), nullable=True),
        sa.Column("factuality_level", sa.String(32), nullable=True),
        sa.Column("narrative_type", sa.String(64), nullable=True),
        sa.Column("folklore_status", sa.String(64), nullable=True),
        sa.Column("source_confidence", sa.String(32), nullable=True),
        sa.Column("needs_review", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("era", sa.String(128), nullable=True),
        sa.Column("region", sa.String(128), nullable=True),
        sa.Column("related_heritages", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("related_people", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("related_places", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("motifs", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("tone_tags", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("story_hooks", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("mission_keywords", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("mission_hooks", JSONB, nullable=False, server_default=sa.text("'[]'::jsonb")),
        sa.Column("raw_text", sa.Text, nullable=False),
        sa.Column("context_text", sa.Text, nullable=False),
        sa.Column("char_count", sa.Integer, nullable=False),
        sa.Column("embedding_status", sa.String(32), nullable=False,
                  server_default=sa.text("'READY_FOR_EMBEDDING'")),
        sa.Column("embedding_model", sa.String(128), nullable=True),
        sa.Column("embedding", Vector(EMBED_DIM), nullable=True),
        sa.Column("embedded_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("extra_metadata", JSONB, nullable=False, server_default=sa.text("'{}'::jsonb")),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False,
                  server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False,
                  server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.UniqueConstraint("chunk_id", name="uq_rag_chunks_chunk_id"),
        schema=SCHEMA,
    )
    for col in ("source_record_id", "source_document_id", "heritage_name",
                "source_type", "content_role", "factuality_level", "embedding_status"):
        op.create_index(f"ix_rag_chunks_{col}", "rag_chunks", [col], schema=SCHEMA)
    op.create_index("ix_rag_chunks_heritage_role", "rag_chunks",
                    ["heritage_name", "content_role"], schema=SCHEMA)
    op.create_index("ix_rag_chunks_heritage_factuality", "rag_chunks",
                    ["heritage_name", "factuality_level"], schema=SCHEMA)
    for col in ("related_heritages", "related_people", "related_places",
                "motifs", "mission_keywords", "mission_hooks", "extra_metadata"):
        op.create_index(
            f"ix_rag_chunks_{col}",
            "rag_chunks",
            [col],
            schema=SCHEMA,
            postgresql_using="gin",
        )

    # 4) pgvector HNSW index (raw SQL — Alembic op.create_index doesn't model HNSW).
    # If the running pgvector build doesn't support HNSW, switch to ivfflat:
    #   CREATE INDEX ... USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
    op.execute(
        f"CREATE INDEX IF NOT EXISTS idx_rag_chunks_embedding_hnsw "
        f"ON {_qualified('rag_chunks')} USING hnsw (embedding vector_cosine_ops);"
    )


def downgrade() -> None:
    index_prefix = f'"{SCHEMA}".' if SCHEMA and SCHEMA != "public" else ""

    op.execute(
        f"DROP INDEX IF EXISTS "
        f"{index_prefix}idx_rag_chunks_embedding_hnsw;"
    )

    op.drop_table("rag_chunks", schema=SCHEMA)
    op.drop_table("rag_source_documents", schema=SCHEMA)
    # Extensions are intentionally NOT dropped — other features may use them.
