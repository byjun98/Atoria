"""Tests for the ingest_rag_documents script entry point."""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from scripts.ingest_rag_documents import run


LEGEND_SAMPLE = [
    {
        "legend_id": "legend_script_test_001",
        "title": "스크립트 테스트 설화",
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
        "mission_keywords": ["별"],
        "is_reconstructed": True,
        "metadata": {"language": "ko"},
    }
]


def _write_legend_file(dirpath: Path) -> Path:
    f = dirpath / "heritage_legend_materials.json"
    f.write_text(json.dumps(LEGEND_SAMPLE, ensure_ascii=False), encoding="utf-8")
    return f


def test_run_creates_outputs_with_legend_only(tmp_path: Path):
    legend = _write_legend_file(tmp_path)
    out_dir = tmp_path / "out"
    result = run(
        legend_file=legend,
        context_path=tmp_path / "no_such_context",
        output_dir=out_dir,
        json_output=True,
    )
    assert result.success_items == 1 and result.failed_items == 0
    assert (out_dir / "rag_chunks_ready.jsonl").exists()
    assert (out_dir / "rag_chunks_ready.json").exists()
    assert (out_dir / "rag_ingestion_report.json").exists()
    failures_path = out_dir / "rag_ingestion_failures.json"
    assert failures_path.exists()
    assert json.loads(failures_path.read_text(encoding="utf-8")) == []


def test_run_uses_explicit_context_file(tmp_path: Path):
    legend = _write_legend_file(tmp_path)
    ctx_file = tmp_path / "ctx.json"
    ctx_file.write_text(
        json.dumps(
            {
                "source_type": "heritage_context",
                "source_site": "한국민족문화대백과",
                "source_url": "https://encykorea.aks.ac.kr/x",
                "record_id": "heritage-script-test-001",
                "heritage_name": "첨성대",
                "title": "첨성대",
                "definition": "신라의 천문 관측 시설.",
                "summary": "",
                "key_facts": {},
                "narrative_excerpts": [],
                "factuality_level": "history",
                "metadata": {},
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    out_dir = tmp_path / "out2"
    result = run(legend_file=legend, context_path=ctx_file, output_dir=out_dir)
    assert result.success_items == 2
    report = json.loads((out_dir / "rag_ingestion_report.json").read_text(encoding="utf-8"))
    assert report["status"] == "COMPLETED"
    assert report["by_content_role"]["fact_context"] >= 1
    assert report["by_content_role"]["legend_material"] >= 1


def test_run_raises_when_legend_file_missing(tmp_path: Path):
    with pytest.raises(FileNotFoundError):
        run(legend_file=tmp_path / "missing.json", context_path=None, output_dir=tmp_path / "out")
