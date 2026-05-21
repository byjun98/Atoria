"""Unit tests for RagChunkStorageService using a fake in-memory repository.

These tests do not require PostgreSQL.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.services.storage.rag_chunk_storage_service import RagChunkStorageService


class _FakeDoc:
    _next_id = 1

    def __init__(self, source_record_id: str) -> None:
        self.id = _FakeDoc._next_id
        _FakeDoc._next_id += 1
        self.source_record_id = source_record_id


class _FakeChunk:
    def __init__(self, chunk_id: str, doc_id: int) -> None:
        self.chunk_id = chunk_id
        self.source_document_id = doc_id


class FakeRepo:
    """Mimics RagChunkRepository's surface for storage-service tests."""

    def __init__(self) -> None:
        self.docs: dict[str, _FakeDoc] = {}
        self.chunks: dict[str, _FakeChunk] = {}
        self.fail_on_chunk_id: str | None = None

    def upsert_source_document(self, chunk_data):
        rid = chunk_data["source_record_id"]
        if rid in self.docs:
            return self.docs[rid], False
        doc = _FakeDoc(rid)
        self.docs[rid] = doc
        return doc, True

    def upsert_chunk(self, source_document, chunk_data):
        cid = chunk_data["chunk_id"]
        if self.fail_on_chunk_id and cid == self.fail_on_chunk_id:
            raise ValueError("simulated failure")
        if cid in self.chunks:
            return self.chunks[cid], False
        chunk = _FakeChunk(cid, source_document.id)
        self.chunks[cid] = chunk
        return chunk, True


def _chunk(idx: int, *, content_role="legend_material", factuality="mixed", heritage="첨성대"):
    return {
        "chunk_id": f"legend_x_{idx:03d}",
        "source_record_id": f"legend_x_{idx:03d}",
        "chunk_index": 0,
        "source_type": "historical_anecdote",
        "source_site": "우리역사넷",
        "source_urls": ["https://contents.history.go.kr/x"],
        "content_role": content_role,
        "heritage_name": heritage,
        "title": f"t{idx}",
        "factuality_level": factuality,
        "narrative_type": "historical_episode",
        "folklore_status": "associated_legend",
        "source_confidence": "high",
        "needs_review": False,
        "era": "신라",
        "region": "경상북도 경주",
        "related_heritages": [heritage],
        "related_people": ["선덕여왕"],
        "related_places": ["경주"],
        "motifs": ["별"],
        "tone_tags": ["신비"],
        "story_hooks": [],
        "mission_keywords": ["별"],
        "mission_hooks": ["하늘과 별을 상징하는 요소를 찾는 미션으로 활용 가능"],
        "raw_text": "본문",
        "context_text": "[문화재: 첨성대]\n본문",
        "char_count": 2,
        "embedding_status": "READY_FOR_EMBEDDING",
        "embedded_at": None,
        "embedding_model": None,
        "vector_id": None,
        "metadata": {"language": "ko"},
    }


def test_store_multiple_chunks_inserts_documents_and_chunks():
    repo = FakeRepo()
    svc = RagChunkStorageService(repo)
    chunks = [_chunk(0), _chunk(1, heritage="석가탑"), _chunk(2, content_role="fact_context", factuality="history")]
    result = svc.store_ready_chunks(chunks)
    assert result.total_chunks == 3
    assert result.inserted_documents == 3
    assert result.inserted_chunks == 3
    assert result.failed_chunks == 0


def test_failed_chunk_recorded_in_failures_others_proceed():
    repo = FakeRepo()
    repo.fail_on_chunk_id = "legend_x_001"
    svc = RagChunkStorageService(repo)
    result = svc.store_ready_chunks([_chunk(0), _chunk(1), _chunk(2)])
    assert result.failed_chunks == 1
    assert result.inserted_chunks == 2
    assert result.failures[0]["chunk_id"] == "legend_x_001"
    assert result.failures[0]["error_code"] == "ValueError"


def test_statistics_calculated():
    repo = FakeRepo()
    svc = RagChunkStorageService(repo)
    chunks = [
        _chunk(0, content_role="fact_context", factuality="history", heritage="첨성대"),
        _chunk(1, content_role="legend_material", factuality="mixed", heritage="첨성대"),
        _chunk(2, content_role="symbolic_material", factuality="symbolic", heritage="다보탑"),
    ]
    result = svc.store_ready_chunks(chunks)
    assert result.by_content_role == {
        "fact_context": 1, "legend_material": 1, "symbolic_material": 1,
    }
    assert result.by_factuality_level == {"history": 1, "mixed": 1, "symbolic": 1}
    assert result.by_heritage_name["첨성대"] == 2
    assert result.by_embedding_status == {"READY_FOR_EMBEDDING": 3}


def test_store_from_jsonl(tmp_path: Path):
    repo = FakeRepo()
    svc = RagChunkStorageService(repo)
    jsonl = tmp_path / "chunks.jsonl"
    with jsonl.open("w", encoding="utf-8") as f:
        for c in (_chunk(0), _chunk(1)):
            f.write(json.dumps(c, ensure_ascii=False) + "\n")
    result = svc.store_ready_chunks_from_jsonl(jsonl)
    assert result.total_chunks == 2
    assert result.inserted_chunks == 2


def test_store_from_jsonl_missing_file(tmp_path: Path):
    repo = FakeRepo()
    svc = RagChunkStorageService(repo)
    with pytest.raises(FileNotFoundError):
        svc.store_ready_chunks_from_jsonl(tmp_path / "nope.jsonl")


def test_repeat_store_treated_as_update():
    repo = FakeRepo()
    svc = RagChunkStorageService(repo)
    svc.store_ready_chunks([_chunk(0)])
    second = svc.store_ready_chunks([_chunk(0)])
    assert second.inserted_chunks == 0 and second.updated_chunks == 1
    assert second.inserted_documents == 0 and second.updated_documents == 1
