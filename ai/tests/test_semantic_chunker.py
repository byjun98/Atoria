"""Tests for SemanticChunker."""
from __future__ import annotations

import pytest

from app.schemas.chunk_schema import ChunkingSourceDocument, RagChunk
from app.services.chunking.semantic_chunker import SemanticChunker


def _doc(text: str, **overrides) -> ChunkingSourceDocument:
    base = dict(
        source_record_id="rec-1",
        source_type="heritage_context",
        source_site="한국민족문화대백과",
        source_urls=["https://encykorea.aks.ac.kr/x"],
        content_role="fact_context",
        heritage_name="첨성대",
        title="첨성대 - 한국민족문화대백과",
        text=text,
        factuality_level="history",
        region="경상북도 경주",
        era="신라",
    )
    base.update(overrides)
    return ChunkingSourceDocument(**base)


@pytest.fixture
def chunker() -> SemanticChunker:
    return SemanticChunker()


def test_short_text_yields_one_chunk(chunker):
    doc = _doc("짧은 본문 한 줄.")
    out = chunker.chunk_document(doc)
    assert len(out) == 1
    assert isinstance(out[0], RagChunk)
    assert out[0].chunk_index == 0
    assert out[0].chunk_id == "rec-1_chunk_000"


def test_long_text_yields_multiple_chunks(chunker):
    sentence = "신라의 첨성대는 별을 관측하던 시설로 알려져 있다. "
    long_text = sentence * 80  # ~ thousands of chars
    doc = _doc(long_text)
    out = chunker.chunk_document(doc)
    assert len(out) >= 2


def test_chunk_indices_are_sequential(chunker):
    sentence = "문장입니다. " * 30
    paragraphs = "\n\n".join([sentence] * 6)
    out = chunker.chunk_document(_doc(paragraphs))
    assert [c.chunk_index for c in out] == list(range(len(out)))
    for i, c in enumerate(out):
        assert c.chunk_id == f"rec-1_chunk_{i:03d}"


def test_each_chunk_raw_text_nonempty_and_char_count_matches(chunker):
    doc = _doc("문장 하나. 문장 둘. 문장 셋.")
    out = chunker.chunk_document(doc)
    for c in out:
        assert c.raw_text.strip()
        assert c.char_count == len(c.raw_text)
        assert c.context_text.strip()


def test_legend_document_chunks_too(chunker):
    doc = _doc(
        "우리역사넷 자료에 따르면 선덕여왕은 별을 살피며 백성을 헤아렸다고 전해진다.",
        source_record_id="legend_x_001",
        source_type="historical_anecdote",
        source_site="우리역사넷",
        content_role="legend_material",
        factuality_level="mixed",
    )
    out = chunker.chunk_document(doc)
    assert len(out) >= 1
    assert out[0].content_role == "legend_material"
    assert out[0].source_type == "historical_anecdote"


def test_source_urls_preserved_on_chunk(chunker):
    doc = _doc("본문.", source_urls=["https://contents.history.go.kr/x"])
    out = chunker.chunk_document(doc)
    assert out[0].source_urls == ["https://contents.history.go.kr/x"]


def test_factuality_level_in_context_text(chunker):
    out = chunker.chunk_document(_doc("본문."))
    assert "사실성: history" in out[0].context_text

    legend_doc = _doc(
        "본문.",
        source_record_id="legend_y",
        content_role="symbolic_material",
        factuality_level="symbolic",
    )
    legend_out = chunker.chunk_document(legend_doc)
    assert "사실성: symbolic" in legend_out[0].context_text


def test_chunk_documents_aggregates(chunker):
    docs = [_doc("a문장."), _doc("b문장.", source_record_id="rec-2")]
    out = chunker.chunk_documents(docs)
    record_ids = {c.source_record_id for c in out}
    assert record_ids == {"rec-1", "rec-2"}


def test_empty_text_returns_no_chunks(chunker):
    out = chunker.chunk_document(_doc("   "))
    assert out == []
