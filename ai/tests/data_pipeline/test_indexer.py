"""
Tests for the index builder.

Creates sample enriched JSON files in a temp directory, runs the indexer,
and verifies the output CSVs.
"""
from __future__ import annotations

import csv
import json
import shutil
from pathlib import Path
from unittest import mock

import pytest

from app.data_pipeline import indexer
from app.data_pipeline.collectors.base import ENRICHED_DIR, LEGENDS_DIR, HERITAGE_CTX_DIR


@pytest.fixture()
def sample_enriched(tmp_path: Path):
    """Create sample JSON files and patch directory constants."""
    legends = tmp_path / "legends"
    heritage_ctx = tmp_path / "heritage_context"
    legends.mkdir()
    heritage_ctx.mkdir()

    # Sample legend
    legend_data = {
        "legend_id": "legend-gyeongju-test-001",
        "source_type": "legend",
        "title": "첨성대 설화",
        "source_site": "test",
        "source_url": "https://example.com/legend",
        "region": "경상북도 경주",
        "related_heritages": ["첨성대"],
        "related_people": ["선덕여왕"],
        "related_places": ["경주"],
        "motifs": ["천문"],
        "tone_tags": ["신비"],
        "story_hooks": ["별을 관측하던 신라인들"],
        "factuality_level": "legend",
        "era": "신라",
        "category": "설화",
        "summary": "첨성대에 대한 이야기",
        "original_text": "옛날 옛적에 첨성대가 세워졌으니 선덕여왕이 별을 관측하고자 하였다.",
        "metadata": {"crawl_source": "web", "language": "ko", "needs_review": False},
    }
    (legends / "legend-gyeongju-test-001.json").write_text(
        json.dumps(legend_data, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    # Sample heritage context
    heritage_data = {
        "record_id": "heritage-cheomseongdae-test-001",
        "source_type": "heritage_context",
        "heritage_name": "첨성대",
        "heritage_aliases": ["점성대"],
        "title": "첨성대 - 테스트",
        "source_site": "test",
        "source_url": "https://example.com/heritage",
        "region": "경상북도 경주",
        "era": "신라",
        "category": ["건축"],
        "definition": "천문 관측대",
        "summary": "신라 선덕여왕 대에 축조된 천문 관측대이다.",
        "key_facts": {},
        "narrative_excerpts": [],
        "related_heritages": [],
        "related_people": ["선덕여왕"],
        "related_places": ["경주"],
        "motifs": ["천문"],
        "tone_tags": ["신비"],
        "story_hooks": [],
        "factuality_level": "history",
        "metadata": {"crawl_source": "web", "language": "ko", "needs_review": False},
    }
    (heritage_ctx / "heritage-cheomseongdae-test-001.json").write_text(
        json.dumps(heritage_data, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    # Patch directory paths
    with (
        mock.patch.object(indexer, "LEGENDS_DIR", legends),
        mock.patch.object(indexer, "HERITAGE_CTX_DIR", heritage_ctx),
        mock.patch.object(indexer, "ENRICHED_DIR", tmp_path),
        mock.patch.object(indexer, "HERITAGE_INDEX_PATH", tmp_path / "heritage_index.csv"),
        mock.patch.object(indexer, "LEGEND_INDEX_PATH", tmp_path / "legend_index.csv"),
    ):
        yield tmp_path


class TestIndexer:
    def test_rebuild_indexes(self, sample_enriched: Path):
        stats = indexer.rebuild_indexes()
        assert stats["total_records"] == 2

        # Heritage index should exist and contain cheomseongdae
        h_path = sample_enriched / "heritage_index.csv"
        assert h_path.exists()
        with h_path.open(encoding="utf-8") as f:
            reader = csv.DictReader(f)
            rows = list(reader)
        heritage_ids = {r["heritage_id"] for r in rows}
        assert "cheomseongdae" in heritage_ids
        # All 15 heritages should be present (some as placeholders)
        from app.data_pipeline.registry import SELECTED_HERITAGES
        for h in SELECTED_HERITAGES:
            assert h.heritage_id in heritage_ids, f"Missing {h.heritage_id}"

        # Legend index
        l_path = sample_enriched / "legend_index.csv"
        assert l_path.exists()
        with l_path.open(encoding="utf-8") as f:
            reader = csv.DictReader(f)
            legend_rows = list(reader)
        assert len(legend_rows) == 1
        assert legend_rows[0]["legend_id"] == "legend-gyeongju-test-001"
