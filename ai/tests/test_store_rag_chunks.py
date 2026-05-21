"""End-to-end smoke test for the store_rag_chunks script.

Skipped automatically when PostgreSQL + pgvector is not reachable.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest
from sqlalchemy import text
from sqlalchemy.exc import OperationalError

from app.db.session import engine


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
    reason="PostgreSQL with pgvector not reachable — skipping script test.",
)


def test_run_writes_report_and_inserts_rows(tmp_path: Path):
    from scripts.store_rag_chunks import run

    chunk = {
        "chunk_id": "legend_script_test_chunk_000",
        "source_record_id": "legend_script_test",
        "chunk_index": 0,
        "source_type": "historical_anecdote",
        "source_site": "우리역사넷",
        "source_urls": ["https://contents.history.go.kr/x"],
        "content_role": "legend_material",
        "heritage_name": "첨성대",
        "title": "스크립트 테스트",
        "factuality_level": "mixed",
        "needs_review": False,
        "related_heritages": ["첨성대"],
        "related_people": [],
        "related_places": [],
        "motifs": [],
        "tone_tags": [],
        "story_hooks": [],
        "mission_keywords": [],
        "mission_hooks": [],
        "raw_text": "본문",
        "context_text": "[문화재: 첨성대]\n본문",
        "char_count": 2,
        "embedding_status": "READY_FOR_EMBEDDING",
        "metadata": {},
    }
    jsonl = tmp_path / "chunks.jsonl"
    jsonl.write_text(json.dumps(chunk, ensure_ascii=False) + "\n", encoding="utf-8")
    report = tmp_path / "report.json"

    result = run(input_file=jsonl, report_file=report, fail_on_error=True)
    assert result.total_chunks == 1
    assert result.failed_chunks == 0
    assert report.exists()
    data = json.loads(report.read_text(encoding="utf-8"))
    assert data["status"] == "COMPLETED"
    assert data["by_content_role"]["legend_material"] == 1


def test_run_raises_when_input_missing(tmp_path: Path):
    from scripts.store_rag_chunks import run

    with pytest.raises(FileNotFoundError):
        run(input_file=tmp_path / "no.jsonl", report_file=tmp_path / "r.json")
