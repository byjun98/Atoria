"""Unit tests for RagEmbeddingService using fakes (no DB, no OpenAI)."""
from __future__ import annotations

from datetime import datetime, timezone
from types import SimpleNamespace

import pytest

from app.services.embedding.rag_embedding_service import RagEmbeddingService


def _chunk(chunk_id, *, content_role="legend_material", factuality="mixed",
           heritage="첨성대", context="[h]\nbody"):
    return SimpleNamespace(
        chunk_id=chunk_id,
        heritage_name=heritage,
        content_role=content_role,
        factuality_level=factuality,
        context_text=context,
        embedding_status="READY_FOR_EMBEDDING",
        embedding=None,
        embedding_model=None,
        embedded_at=None,
        extra_metadata={},
    )


class FakeRepo:
    def __init__(self, chunks):
        self._chunks = chunks
        self.updated: list[tuple[str, list[float], str]] = []
        self.failed: list[tuple[str, str]] = []

    def list_chunks_ready_for_embedding(self, limit=100):
        return [c for c in self._chunks if c.embedding_status == "READY_FOR_EMBEDDING"][:limit]

    def update_chunk_embedding(self, chunk_id, embedding, embedding_model):
        chunk = next(c for c in self._chunks if c.chunk_id == chunk_id)
        chunk.embedding = embedding
        chunk.embedding_model = embedding_model
        chunk.embedding_status = "EMBEDDED"
        chunk.embedded_at = datetime.now(timezone.utc)
        self.updated.append((chunk_id, embedding, embedding_model))
        return chunk

    def mark_chunk_embedding_failed(self, chunk_id, error_message=None):
        chunk = next(c for c in self._chunks if c.chunk_id == chunk_id)
        chunk.embedding_status = "EMBEDDING_FAILED"
        self.failed.append((chunk_id, error_message or ""))
        return chunk


class FakeClient:
    def __init__(self, dim=4, model="fake-model", raise_exc=None):
        self.expected_dim = dim
        self.model = model
        self._raise = raise_exc

    def embed_texts(self, texts):
        if self._raise:
            raise self._raise
        return [[0.1] * self.expected_dim for _ in texts]


def _service(repo, client, batch_size=2):
    return RagEmbeddingService(
        repository=repo, embedding_client=client, batch_size=batch_size
    )


def test_embed_marks_chunks_embedded():
    chunks = [_chunk("c1"), _chunk("c2")]
    repo = FakeRepo(chunks)
    svc = _service(repo, FakeClient(dim=4))
    res = svc.embed_ready_chunks()
    assert res.total_candidates == 2
    assert res.embedded_count == 2
    assert res.failed_count == 0
    assert all(c.embedding_status == "EMBEDDED" for c in chunks)
    assert all(len(c.embedding) == 4 for c in chunks)
    assert res.embedded_chunk_ids == ["c1", "c2"]


def test_empty_context_text_skipped_and_marked_failed():
    chunks = [_chunk("c1", context="   "), _chunk("c2")]
    repo = FakeRepo(chunks)
    svc = _service(repo, FakeClient())
    res = svc.embed_ready_chunks()
    assert res.skipped_count == 1
    assert res.embedded_count == 1
    assert chunks[0].embedding_status == "EMBEDDING_FAILED"


def test_api_failure_marks_whole_batch_failed_and_continues():
    chunks = [_chunk("c1"), _chunk("c2"), _chunk("c3"), _chunk("c4")]
    repo = FakeRepo(chunks)
    # Client raises on first batch only
    class FlakyClient(FakeClient):
        def __init__(self):
            super().__init__(dim=4)
            self.calls = 0

        def embed_texts(self, texts):
            self.calls += 1
            if self.calls == 1:
                raise RuntimeError("rate limit")
            return super().embed_texts(texts)

    svc = _service(repo, FlakyClient(), batch_size=2)
    res = svc.embed_ready_chunks()
    # Batch 1 (c1, c2) failed; batch 2 (c3, c4) succeeded.
    assert res.embedded_count == 2
    assert res.failed_count == 2
    assert {c.chunk_id for c in chunks if c.embedding_status == "EMBEDDED"} == {"c3", "c4"}
    assert {c.chunk_id for c in chunks if c.embedding_status == "EMBEDDING_FAILED"} == {"c1", "c2"}


def test_no_candidates_returns_empty_result():
    repo = FakeRepo([])
    svc = _service(repo, FakeClient())
    res = svc.embed_ready_chunks()
    assert res.total_candidates == 0
    assert res.embedded_count == 0
    assert res.failures == []


def test_statistics_aggregated():
    chunks = [
        _chunk("c1", content_role="fact_context", factuality="history", heritage="첨성대"),
        _chunk("c2", content_role="legend_material", factuality="mixed", heritage="첨성대"),
        _chunk("c3", content_role="symbolic_material", factuality="symbolic", heritage="다보탑"),
    ]
    repo = FakeRepo(chunks)
    svc = _service(repo, FakeClient(), batch_size=10)
    res = svc.embed_ready_chunks()
    assert res.by_content_role == {
        "fact_context": 1, "legend_material": 1, "symbolic_material": 1,
    }
    assert res.by_factuality_level == {"history": 1, "mixed": 1, "symbolic": 1}
    assert res.by_heritage_name == {"첨성대": 2, "다보탑": 1}
    assert res.embedding_model == "fake-model"
    assert res.embedding_dim == 4


def test_invalid_batch_size_raises():
    with pytest.raises(ValueError):
        RagEmbeddingService(repository=FakeRepo([]), embedding_client=FakeClient(), batch_size=0)
