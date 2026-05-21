"""DB-backed tests for RagChunkRepository.

Skipped automatically when PostgreSQL + pgvector is not reachable.
Each test runs inside a SAVEPOINT and is rolled back at teardown.
"""
from __future__ import annotations

import pytest
from sqlalchemy import text
from sqlalchemy.exc import OperationalError

from app.db.session import SessionLocal, engine
from app.repositories.rag_chunk_repository import RagChunkRepository


def _db_available() -> bool:
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
            ext = conn.execute(
                text("SELECT 1 FROM pg_extension WHERE extname = 'vector'")
            ).scalar()
            return bool(ext)
    except OperationalError:
        return False
    except Exception:
        return False


pytestmark = pytest.mark.skipif(
    not _db_available(),
    reason="PostgreSQL with pgvector not reachable — skipping DB-backed tests.",
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


def _sample_chunk(chunk_id="legend_test_x_chunk_000", record_id="legend_test_x"):
    return {
        "chunk_id": chunk_id,
        "source_record_id": record_id,
        "chunk_index": 0,
        "source_type": "historical_anecdote",
        "source_site": "우리역사넷",
        "source_urls": ["https://contents.history.go.kr/x"],
        "content_role": "legend_material",
        "heritage_name": "첨성대",
        "title": "테스트",
        "factuality_level": "mixed",
        "narrative_type": "historical_episode",
        "folklore_status": "associated_legend",
        "source_confidence": "high",
        "needs_review": False,
        "era": "신라",
        "region": "경상북도 경주",
        "related_heritages": ["첨성대"],
        "related_people": ["선덕여왕"],
        "related_places": ["경주"],
        "motifs": ["별"],
        "tone_tags": ["신비"],
        "story_hooks": ["별을 바라보는 장면"],
        "mission_keywords": ["별"],
        "mission_hooks": ["하늘과 별을 상징하는 요소를 찾는 미션으로 활용 가능"],
        "raw_text": "본문",
        "context_text": "[문화재: 첨성대]\n본문",
        "char_count": 2,
        "embedding_status": "READY_FOR_EMBEDDING",
        "metadata": {"language": "ko"},
    }


def test_upsert_source_document_then_chunk(session):
    repo = RagChunkRepository(session)
    chunk = _sample_chunk()
    doc, inserted_doc = repo.upsert_source_document(chunk)
    assert inserted_doc is True
    rag_chunk, inserted_chunk = repo.upsert_chunk(doc, chunk)
    assert inserted_chunk is True
    assert rag_chunk.source_document_id == doc.id
    assert rag_chunk.embedding_status == "READY_FOR_EMBEDDING"
    assert rag_chunk.embedding is None


def test_repeat_upsert_does_not_duplicate(session):
    repo = RagChunkRepository(session)
    chunk = _sample_chunk()
    doc, _ = repo.upsert_source_document(chunk)
    repo.upsert_chunk(doc, chunk)
    doc2, doc_inserted = repo.upsert_source_document(chunk)
    _, chunk_inserted = repo.upsert_chunk(doc2, chunk)
    assert doc_inserted is False
    assert chunk_inserted is False
    assert repo.count_chunks() >= 1


def test_jsonb_fields_preserved(session):
    repo = RagChunkRepository(session)
    chunk = _sample_chunk()
    doc, _ = repo.upsert_source_document(chunk)
    repo.upsert_chunk(doc, chunk)
    fetched = repo.get_chunk_by_chunk_id(chunk["chunk_id"])
    assert fetched is not None
    assert fetched.source_urls == ["https://contents.history.go.kr/x"]
    assert fetched.motifs == ["별"]
    assert fetched.mission_hooks[0].startswith("하늘과 별")
    assert fetched.extra_metadata == {"language": "ko"}


def test_count_by_embedding_status(session):
    repo = RagChunkRepository(session)
    chunk = _sample_chunk(chunk_id="legend_test_x_chunk_001", record_id="legend_test_x_b")
    doc, _ = repo.upsert_source_document(chunk)
    repo.upsert_chunk(doc, chunk)
    counts = repo.count_by_embedding_status()
    assert counts.get("READY_FOR_EMBEDDING", 0) >= 1


def test_protected_embedded_status_is_preserved_on_reupsert(session):
    repo = RagChunkRepository(session)
    chunk = _sample_chunk(chunk_id="legend_test_x_chunk_emb", record_id="legend_test_x_emb")
    doc, _ = repo.upsert_source_document(chunk)
    rag_chunk, _ = repo.upsert_chunk(doc, chunk)
    rag_chunk.embedding_status = "EMBEDDED"
    session.flush()
    repo.upsert_chunk(doc, chunk)
    refetched = repo.get_chunk_by_chunk_id(chunk["chunk_id"])
    assert refetched.embedding_status == "EMBEDDED"
