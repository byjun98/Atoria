"""Tests for RagIngestionService and writers."""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.services.ingestion.rag_ingestion_service import (
    READY_STATUS,
    RagIngestionService,
    write_json,
    write_jsonl,
)


LEGEND_ITEM = {
    "legend_id": "legend_test_001",
    "title": "테스트 설화",
    "heritage_name": "첨성대",
    "related_heritages": ["첨성대"],
    "source_type": "historical_anecdote",
    "narrative_type": "historical_episode",
    "factuality_level": "mixed",
    "primary_source_site": "우리역사넷",
    "source_sites": ["우리역사넷"],
    "source_urls": ["https://contents.history.go.kr/x"],
    "source_confidence": "high",
    "needs_review": False,
    "folklore_status": "associated_legend",
    "region": "경상북도 경주",
    "era": "신라",
    "related_people": ["선덕여왕"],
    "related_places": ["경주"],
    "motifs": ["별"],
    "tone_tags": ["신비"],
    "raw_text": "선덕여왕은 첨성대에서 별을 살피며 백성을 헤아렸다고 전해진다.",
    "story_summary": "별을 읽는 여왕.",
    "story_hooks": ["별을 바라보는 장면"],
    "mission_keywords": ["별", "하늘"],
    "is_reconstructed": True,
    "metadata": {"language": "ko"},
}


HERITAGE_ITEM = {
    "source_type": "heritage_context",
    "source_site": "한국민족문화대백과",
    "source_url": "https://encykorea.aks.ac.kr/Article/E0002916",
    "region": "경상북도 경주",
    "era": "신라",
    "related_heritages": ["동궁과월지"],
    "related_people": ["문무왕"],
    "related_places": ["경주"],
    "motifs": ["왕권"],
    "tone_tags": [],
    "story_hooks": [],
    "factuality_level": "history",
    "metadata": {"needs_review": False},
    "record_id": "heritage-donggung-wolji-test-001",
    "heritage_name": "동궁과월지",
    "title": "동궁과월지 - 한국민족문화대백과",
    "definition": "경상북도 경주시에 있는 통일신라의 별궁이 자리했던 궁궐터.",
    "summary": "경주 동궁과 월지\n부수 心 총획 14",
    "key_facts": {},
    "narrative_excerpts": [],
}


@pytest.fixture
def service() -> RagIngestionService:
    return RagIngestionService()


def test_legend_item_yields_ready_chunk(service):
    result = service.ingest_items([LEGEND_ITEM])
    assert result.success_items == 1 and result.failed_items == 0
    assert len(result.chunks) >= 1
    c = result.chunks[0]
    assert c["embedding_status"] == READY_STATUS
    assert c["embedded_at"] is None
    assert c["embedding_model"] is None
    assert c["vector_id"] is None
    assert c["content_role"] == "legend_material"


def test_heritage_context_item_yields_fact_context_chunk(service):
    result = service.ingest_items([HERITAGE_ITEM])
    assert result.success_items == 1
    c = result.chunks[0]
    assert c["content_role"] == "fact_context"
    assert c["factuality_level"] == "history"
    assert c["source_type"] == "heritage_context"


def test_aggregated_stats(service):
    result = service.ingest_items([LEGEND_ITEM, HERITAGE_ITEM])
    assert result.total_chunks >= 2
    assert result.by_source_type["heritage_context"] >= 1
    assert result.by_source_type["historical_anecdote"] >= 1
    assert result.by_content_role["fact_context"] >= 1
    assert result.by_content_role["legend_material"] >= 1
    assert result.by_factuality_level["history"] >= 1
    assert result.by_factuality_level["mixed"] >= 1
    assert result.by_heritage_name["첨성대"] >= 1


def test_failures_do_not_abort(service):
    bad = {"foo": "bar"}  # neither legend_id nor record_id
    result = service.ingest_items([bad, LEGEND_ITEM])
    assert result.failed_items == 1
    assert result.success_items == 1
    assert result.failures[0]["error_code"] == "ValueError"


def test_legend_without_text_fails_gracefully(service):
    bad_legend = {**LEGEND_ITEM, "legend_id": "legend_empty", "raw_text": "", "story_summary": "", "story_hooks": []}
    result = service.ingest_items([bad_legend])
    assert result.failed_items == 1
    assert result.failures[0]["source_record_id"] == "legend_empty"


def test_chunk_context_text_nonempty_and_urls_preserved(service):
    result = service.ingest_items([LEGEND_ITEM])
    c = result.chunks[0]
    assert c["context_text"].strip()
    assert c["source_urls"] == ["https://contents.history.go.kr/x"]


# ---- writers ------------------------------------------------------------


def test_write_jsonl_creates_file_and_lines(tmp_path: Path):
    out = tmp_path / "nested" / "chunks.jsonl"
    write_jsonl([{"a": 1, "ko": "한글"}, {"a": 2}], out)
    assert out.exists()
    lines = out.read_text(encoding="utf-8").splitlines()
    assert len(lines) == 2
    assert json.loads(lines[0]) == {"a": 1, "ko": "한글"}


def test_write_json_creates_file(tmp_path: Path):
    out = tmp_path / "report.json"
    write_json({"k": "v", "ko": "값"}, out)
    assert json.loads(out.read_text(encoding="utf-8")) == {"k": "v", "ko": "값"}


def test_writers_create_parent_directory(tmp_path: Path):
    out_jsonl = tmp_path / "a" / "b" / "x.jsonl"
    write_jsonl([{"x": 1}], out_jsonl)
    assert out_jsonl.exists()
    out_json = tmp_path / "a" / "c" / "y.json"
    write_json([], out_json)
    assert out_json.exists() and json.loads(out_json.read_text(encoding="utf-8")) == []
