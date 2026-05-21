"""DB-backed tests for RagSearchRepository — skipped if PG+pgvector unreachable."""
from __future__ import annotations

import pytest
from sqlalchemy import text
from sqlalchemy.exc import OperationalError

from app.db.session import SessionLocal, engine
from app.db.models.rag_chunk import RagChunk
from app.db.models.rag_document import RagSourceDocument
from app.repositories.rag_search_repository import RagSearchRepository


def _db_available() -> bool:
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
            return bool(
                conn.execute(text("SELECT 1 FROM pg_extension WHERE extname='vector'")).scalar()
            )
    except OperationalError:
        return False
    except Exception:
        return False


pytestmark = pytest.mark.skipif(
    not _db_available(),
    reason="PostgreSQL with pgvector not reachable.",
)


@pytest.fixture
def session():
    s = SessionLocal()
    s.begin_nested()
    try:
        yield s
    finally:
        s.rollback()
        s.close()


def _seed(session, *, chunk_id, status="EMBEDDED", embedding=None, **overrides):
    doc = RagSourceDocument(
        source_record_id=chunk_id + "_doc",
        source_type="historical_anecdote",
        source_site="우리역사넷",
        source_urls=[],
        content_role="legend_material",
        heritage_name="첨성대",
        title="t",
        factuality_level="mixed",
        needs_review=False,
        related_heritages=[], related_people=[], related_places=[],
        motifs=[], tone_tags=[], story_hooks=[], mission_keywords=[],
        extra_metadata={},
    )
    session.add(doc)
    session.flush()
    chunk = RagChunk(
        chunk_id=chunk_id,
        source_document_id=doc.id,
        source_record_id=doc.source_record_id,
        chunk_index=0,
        source_type=overrides.get("source_type", "historical_anecdote"),
        source_site="우리역사넷",
        source_urls=[],
        content_role=overrides.get("content_role", "legend_material"),
        heritage_name=overrides.get("heritage_name", "첨성대"),
        title="t",
        factuality_level=overrides.get("factuality_level", "mixed"),
        needs_review=False,
        related_heritages=[], related_people=[], related_places=[],
        motifs=[], tone_tags=[], story_hooks=[], mission_keywords=[],
        mission_hooks=[],
        raw_text="raw",
        context_text="[h]\nbody",
        char_count=2,
        embedding_status=status,
        embedding=embedding,
        extra_metadata={},
    )
    session.add(chunk)
    session.flush()
    return chunk


def _vec(seed: float):
    # 1536-dim vector with one component shifted to control distance ordering.
    v = [0.0] * 1536
    v[0] = seed
    return v


def test_only_embedded_chunks_returned(session):
    _seed(session, chunk_id="ready_test", status="READY_FOR_EMBEDDING", embedding=None)
    _seed(session, chunk_id="emb_test", status="EMBEDDED", embedding=_vec(1.0))
    repo = RagSearchRepository(session)
    out = repo.search_similar_chunks(query_embedding=_vec(1.0), top_k=10)
    assert {c.chunk_id for c, _ in out} == {"emb_test"}


def test_distance_ordering_ascending(session):
    _seed(session, chunk_id="near", embedding=_vec(1.0))
    _seed(session, chunk_id="far", embedding=_vec(-1.0))
    repo = RagSearchRepository(session)
    out = repo.search_similar_chunks(query_embedding=_vec(1.0), top_k=10)
    ids = [c.chunk_id for c, _ in out]
    assert ids[0] == "near"
    assert all(d1 <= d2 for (_, d1), (_, d2) in zip(out, out[1:]))


def test_top_k_limits_count(session):
    for i in range(5):
        _seed(session, chunk_id=f"c{i}", embedding=_vec(1.0 - i * 0.1))
    repo = RagSearchRepository(session)
    out = repo.search_similar_chunks(query_embedding=_vec(1.0), top_k=3)
    assert len(out) == 3


def test_heritage_and_role_filters(session):
    _seed(session, chunk_id="cs_legend", embedding=_vec(1.0),
          heritage_name="첨성대", content_role="legend_material")
    _seed(session, chunk_id="cs_fact", embedding=_vec(1.0),
          heritage_name="첨성대", content_role="fact_context")
    _seed(session, chunk_id="sk_legend", embedding=_vec(1.0),
          heritage_name="석가탑", content_role="legend_material")
    repo = RagSearchRepository(session)
    out = repo.search_similar_chunks(
        query_embedding=_vec(1.0),
        top_k=10,
        heritage_names=["첨성대"],
        content_roles=["legend_material"],
    )
    assert {c.chunk_id for c, _ in out} == {"cs_legend"}
