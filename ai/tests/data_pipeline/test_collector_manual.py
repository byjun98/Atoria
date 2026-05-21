"""
Tests for manual_webfetch collector — verifying that pre-existing JSON
is correctly absorbed into the standard schema.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.data_pipeline.collectors.manual_webfetch import ManualWebfetchCollector
from app.schemas.data.legend import LegendRecord
from app.schemas.data.heritage_context import HeritageContextRecord


@pytest.fixture()
def legend_json(tmp_path: Path) -> Path:
    data = {
        "legend_id": "legend-test-manual-001",
        "source_type": "legend",
        "title": "수동 입력 설화",
        "source_site": "manual",
        "source_url": "https://example.com",
        "summary": "테스트 요약",
        "original_text": "옛날 옛적에...",
    }
    fp = tmp_path / "legend.json"
    fp.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return fp


@pytest.fixture()
def heritage_json(tmp_path: Path) -> Path:
    data = {
        "record_id": "heritage-test-manual-001",
        "source_type": "heritage_context",
        "heritage_name": "테스트 문화재",
        "heritage_aliases": [],
        "title": "테스트",
        "source_site": "manual",
        "source_url": "https://example.com",
        "definition": "테스트 정의",
        "summary": "테스트 요약",
    }
    fp = tmp_path / "heritage.json"
    fp.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return fp


class TestManualWebfetchCollector:
    def test_load_legend(self, legend_json: Path):
        collector = ManualWebfetchCollector()
        records = list(collector.collect(str(legend_json)))
        assert len(records) == 1
        assert isinstance(records[0], LegendRecord)
        assert records[0].legend_id == "legend-test-manual-001"

    def test_load_heritage_context(self, heritage_json: Path):
        collector = ManualWebfetchCollector()
        records = list(collector.collect(str(heritage_json)))
        assert len(records) == 1
        assert isinstance(records[0], HeritageContextRecord)
        assert records[0].record_id == "heritage-test-manual-001"

    def test_load_directory(self, legend_json: Path, heritage_json: Path):
        # Both files are in the same tmp_path directory
        collector = ManualWebfetchCollector()
        records = list(collector.collect(str(legend_json.parent)))
        assert len(records) == 2

    def test_nonexistent_path(self, tmp_path: Path):
        collector = ManualWebfetchCollector()
        records = list(collector.collect(str(tmp_path / "nonexistent.json")))
        assert len(records) == 0
