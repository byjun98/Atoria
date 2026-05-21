"""Tests for ContextBuilder."""
from __future__ import annotations

import pytest

from app.schemas.chunk_schema import ChunkingSourceDocument
from app.services.chunking.context_builder import ContextBuilder


def _doc(**overrides) -> ChunkingSourceDocument:
    base = dict(
        source_record_id="rec-1",
        source_type="heritage_context",
        source_site="한국민족문화대백과",
        source_urls=["https://encykorea.aks.ac.kr/Article/E0002916"],
        content_role="fact_context",
        heritage_name="동궁과월지",
        title="동궁과월지 - 한국민족문화대백과",
        text="원문 텍스트",
        factuality_level="history",
        era="신라",
        region="경상북도 경주",
        related_people=["문무왕", "혜공왕"],
        related_places=["경주", "월지"],
        related_heritages=["동궁과월지"],
        motifs=["왕권", "천문"],
        tone_tags=["고요"],
        needs_review=False,
    )
    base.update(overrides)
    return ChunkingSourceDocument(**base)


@pytest.fixture
def builder() -> ContextBuilder:
    return ContextBuilder()


def test_header_includes_heritage_and_title(builder):
    out = builder.build_context_text("본문", _doc())
    assert "문화재: 동궁과월지" in out
    assert "제목: 동궁과월지 - 한국민족문화대백과" in out
    assert out.endswith("본문")


def test_header_includes_content_role(builder):
    out = builder.build_context_text("x", _doc())
    assert "콘텐츠 역할: fact_context" in out


def test_header_includes_source_type_and_factuality(builder):
    out = builder.build_context_text("x", _doc())
    assert "자료 유형: heritage_context" in out
    assert "사실성: history" in out


def test_header_includes_era_people_places_motifs_tones(builder):
    out = builder.build_context_text("x", _doc())
    assert "시대: 신라" in out
    assert "인물: 문무왕, 혜공왕" in out
    assert "관련 장소: 경주, 월지" in out
    assert "모티프: 왕권, 천문" in out
    assert "분위기: 고요" in out


def test_needs_review_true_renders_in_header(builder):
    out = builder.build_context_text("x", _doc(needs_review=True))
    assert "검토 필요: true" in out


def test_symbolic_factuality_is_explicit(builder):
    out = builder.build_context_text(
        "x",
        _doc(
            source_type="symbolic_narrative",
            content_role="symbolic_material",
            factuality_level="symbolic",
            narrative_type="symbolic_story",
            folklore_status="symbolic_only",
            source_site="우리역사넷",
            source_confidence="high",
        ),
    )
    assert "사실성: symbolic" in out
    assert "서사 유형: symbolic_story" in out
    assert "설화 상태: symbolic_only" in out


def test_heritage_context_factuality_is_history(builder):
    out = builder.build_context_text("x", _doc())
    assert "사실성: history" in out


def test_source_site_rendered(builder):
    out_a = builder.build_context_text("x", _doc())
    assert "출처: 한국민족문화대백과" in out_a
    out_b = builder.build_context_text(
        "x", _doc(source_site="우리역사넷", source_confidence="high")
    )
    assert "출처: 우리역사넷" in out_b
    assert "출처 신뢰도: high" in out_b


def test_source_urls_are_not_inlined(builder):
    out = builder.build_context_text("x", _doc())
    # URLs should live on RagChunk.source_urls, not inside the header.
    assert "encykorea" not in out.split("\n", 1)[0]
