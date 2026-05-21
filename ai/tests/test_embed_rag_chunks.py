"""Tests for the embed_rag_chunks script (no real OpenAI / DB)."""
from __future__ import annotations

import json
from pathlib import Path
from types import SimpleNamespace

import pytest


def test_dry_run_does_not_call_openai_and_writes_report(tmp_path: Path, monkeypatch):
    """--dry-run reads candidates, writes a report, never touches OpenAI."""
    from scripts import embed_rag_chunks as mod

    fake_chunks = [SimpleNamespace(chunk_id=f"c{i}") for i in range(3)]

    class FakeRepo:
        def __init__(self, session):
            pass

        def list_chunks_ready_for_embedding(self, limit=100):
            return fake_chunks[:limit]

    class FakeSession:
        def close(self):
            pass

    monkeypatch.setattr(mod, "RagChunkRepository", FakeRepo)
    monkeypatch.setattr(mod, "SessionLocal", lambda: FakeSession())
    monkeypatch.setattr(
        "app.clients.openai_embedding_client.OpenAI",
        lambda *a, **kw: pytest.fail("OpenAI must NOT be constructed in dry-run"),
    )

    report_path = tmp_path / "report.json"
    report = mod.run(limit=5, dry_run=True, report_file=report_path)

    assert report["status"] == "DRY_RUN"
    assert report["total_candidates"] == 3
    assert report["embedded_count"] == 0
    assert report_path.exists()
    on_disk = json.loads(report_path.read_text(encoding="utf-8"))
    assert on_disk["candidate_chunk_ids"] == ["c0", "c1", "c2"]


def test_run_without_api_key_in_real_mode_raises(tmp_path: Path, monkeypatch):
    """Real (non dry-run) mode must reject empty OPENAI_API_KEY clearly."""
    from scripts import embed_rag_chunks as mod

    monkeypatch.setattr(mod.settings, "OPENAI_API_KEY", "")
    with pytest.raises(RuntimeError, match="OPENAI_API_KEY"):
        mod.run(limit=1, dry_run=False, report_file=tmp_path / "r.json")


def test_full_run_with_fakes_writes_report(tmp_path: Path, monkeypatch):
    """End-to-end with fake client + fake repo: report has embedded_count > 0."""
    from scripts import embed_rag_chunks as mod
    from app.services.embedding.rag_embedding_service import RagEmbeddingService

    fake_chunks = [
        SimpleNamespace(
            chunk_id="c1",
            heritage_name="첨성대",
            content_role="legend_material",
            factuality_level="mixed",
            context_text="[h]\nbody",
            embedding_status="READY_FOR_EMBEDDING",
            embedding=None,
            embedding_model=None,
            embedded_at=None,
            extra_metadata={},
        )
    ]

    class FakeRepo:
        def __init__(self, session):
            pass

        def list_chunks_ready_for_embedding(self, limit=100):
            return fake_chunks[:limit]

        def update_chunk_embedding(self, chunk_id, embedding, embedding_model):
            c = fake_chunks[0]
            c.embedding = embedding
            c.embedding_model = embedding_model
            c.embedding_status = "EMBEDDED"
            return c

        def mark_chunk_embedding_failed(self, chunk_id, error_message=None):
            return fake_chunks[0]

    class FakeSession:
        def commit(self):
            pass

        def rollback(self):
            pass

        def close(self):
            pass

    class FakeClient:
        expected_dim = 4
        model = "fake-model"

        def __init__(self):
            pass

        def embed_texts(self, texts):
            return [[0.0] * 4 for _ in texts]

    monkeypatch.setattr(mod.settings, "OPENAI_API_KEY", "test-key")
    monkeypatch.setattr(mod, "RagChunkRepository", FakeRepo)
    monkeypatch.setattr(mod, "SessionLocal", lambda: FakeSession())
    monkeypatch.setattr(
        "app.clients.openai_embedding_client.OpenAIEmbeddingClient",
        FakeClient,
    )
    # The script imports the client lazily inside run(); patch the real import target.
    import app.clients.openai_embedding_client as oec
    monkeypatch.setattr(oec, "OpenAIEmbeddingClient", FakeClient)

    report_path = tmp_path / "report.json"
    report = mod.run(limit=10, batch_size=10, dry_run=False, report_file=report_path)
    assert report["status"] == "COMPLETED"
    assert report["embedded_count"] == 1
    assert report["embedding_model"] == "fake-model"
    assert report["embedding_dim"] == 4
    assert report_path.exists()
