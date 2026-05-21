"""
Tests for data pipeline schemas — serialisation, validation, and
backward-compatibility with the practice repo's cheomseongdae JSON.
"""
from __future__ import annotations

import json
import pytest

from app.schemas.data.legend import LegendRecord
from app.schemas.data.heritage_context import HeritageContextRecord
from app.schemas.data.heritage_entity import HeritageEntity


# ---------------------------------------------------------------------------
# LegendRecord
# ---------------------------------------------------------------------------

class TestLegendRecord:
    def test_minimal(self):
        rec = LegendRecord(
            legend_id="legend-test-001",
            title="테스트 설화",
            source_site="test",
            source_url="https://example.com",
        )
        assert rec.source_type == "legend"
        assert rec.legend_id == "legend-test-001"
        assert rec.metadata.language == "ko"

    def test_full_serialisation(self):
        rec = LegendRecord(
            legend_id="legend-gyeongju-encykorea-첨성대-001",
            title="첨성대 설화",
            source_site="한국민족문화대백과",
            source_url="https://encykorea.aks.ac.kr/Article/E0056197",
            region="경상북도 경주",
            related_heritages=["첨성대"],
            related_people=["선덕여왕"],
            motifs=["천문", "왕권"],
            tone_tags=["신비"],
            story_hooks=["별을 관측하던 신라인들"],
            factuality_level="legend",
            summary="첨성대에 얽힌 이야기",
            original_text="옛날 옛적에 첨성대가 있었으니...",
        )
        data = rec.model_dump()
        assert data["source_type"] == "legend"
        assert data["legend_id"].startswith("legend-")
        # Round-trip
        rec2 = LegendRecord.model_validate(data)
        assert rec2.title == rec.title

    def test_factuality_enum(self):
        for level in ("legend", "history", "folktale", "mixed"):
            rec = LegendRecord(
                legend_id="x", title="t", source_site="s", source_url="u",
                factuality_level=level,
            )
            assert rec.factuality_level == level

    def test_invalid_factuality_rejected(self):
        with pytest.raises(Exception):
            LegendRecord(
                legend_id="x", title="t", source_site="s", source_url="u",
                factuality_level="invalid",
            )


# ---------------------------------------------------------------------------
# HeritageContextRecord
# ---------------------------------------------------------------------------

class TestHeritageContextRecord:
    def test_minimal(self):
        rec = HeritageContextRecord(
            record_id="heritage-test-001",
            heritage_name="테스트",
            title="테스트 문화재",
            source_site="test",
            source_url="https://example.com",
        )
        assert rec.source_type == "heritage_context"

    def test_full_serialisation(self):
        rec = HeritageContextRecord(
            record_id="heritage-cheomseongdae-encykorea-001",
            heritage_name="첨성대",
            heritage_aliases=["瞻星臺", "점성대"],
            title="첨성대 - 한국민족문화대백과",
            source_site="한국민족문화대백과",
            source_url="https://encykorea.aks.ac.kr/Article/E0056197",
            region="경상북도 경주",
            era="신라",
            category=["건축", "과학"],
            definition="별을 관측하기 위하여 쌓은 석조 건축물(臺).",
            summary="신라 선덕여왕 대에 축조된 천문 관측대.",
            key_facts={"재질": "화강암", "높이": "약 9.5m"},
            narrative_excerpts=["옛날 이야기..."],
            story_hooks=["별을 관측하던 신라인들"],
            motifs=["천문", "왕권"],
            tone_tags=["신비"],
            factuality_level="history",
        )
        data = rec.model_dump()
        assert data["source_type"] == "heritage_context"
        # Round-trip
        rec2 = HeritageContextRecord.model_validate(data)
        assert rec2.heritage_name == "첨성대"


# ---------------------------------------------------------------------------
# Backward-compatibility: cheomseongdae JSON from practice repo
# ---------------------------------------------------------------------------

CHEOMSEONGDAE_JSON = {
    "record_id": "heritage-cheomseongdae-encykorea-001",
    "source_type": "heritage_context",
    "heritage_name": "첨성대",
    "heritage_aliases": ["점성대", "별보는대", "첨성"],
    "title": "첨성대 (瞻星臺) - 한국민족문화대백과",
    "source_site": "한국민족문화대백과",
    "source_url": "https://encykorea.aks.ac.kr/Article/E0056197",
    "region": "경상북도 경주",
    "era": "신라 선덕여왕 대",
    "category": ["건축", "과학"],
    "definition": "별을 관측하기 위하여 쌓은 석조 건축물(臺).",
    "summary": "신라 선덕여왕 대에 축조된 현존 최고의 천문대.",
    "related_heritages": ["문화재청 관측시설", "경주 첨성대"],
    "related_people": ["선덕여왕", "원효"],
    "related_places": ["경주 인왕동"],
    "key_facts": {"재질": "화강암(풍화암)"},
    "narrative_excerpts": ["옛날 이야기 발췌"],
    "story_hooks": ["별을 관측하던 신라인들"],
    "motifs": ["천문", "왕권"],
    "tone_tags": ["비극적", "신비"],
    "factuality_level": "history",
    "metadata": {
        "crawl_source": "web",
        "crawl_method": "manual_webfetch",
        "language": "ko",
        "fetched_at": "2026-04-29",
        "needs_review": False,
    },
}


class TestCheomseongdaeBackwardCompat:
    """Ensure the practice repo's cheomseongdae JSON validates with the new schema."""

    def test_validate(self):
        rec = HeritageContextRecord.model_validate(CHEOMSEONGDAE_JSON)
        assert rec.record_id == "heritage-cheomseongdae-encykorea-001"
        assert rec.heritage_name == "첨성대"
        assert rec.factuality_level == "history"

    def test_roundtrip(self):
        rec = HeritageContextRecord.model_validate(CHEOMSEONGDAE_JSON)
        data = json.loads(rec.model_dump_json())
        rec2 = HeritageContextRecord.model_validate(data)
        assert rec2.record_id == rec.record_id
        assert rec2.metadata.crawl_method == "manual_webfetch"


# ---------------------------------------------------------------------------
# HeritageEntity
# ---------------------------------------------------------------------------

class TestHeritageEntity:
    def test_create(self):
        e = HeritageEntity(
            heritage_id="cheomseongdae",
            canonical_name="첨성대",
            aliases=["瞻星臺"],
            designation="국보",
        )
        assert e.heritage_id == "cheomseongdae"
        assert "瞻星臺" in e.aliases
