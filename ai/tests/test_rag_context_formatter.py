"""Tests for RagContextFormatter."""
from __future__ import annotations

import pytest

from app.schemas.story_schema import RagContextByPlace, RagContextItem
from app.services.story.rag_context_formatter import RagContextFormatter


def _item(chunk_id, role, *, factuality="history", urls=None, hooks=None):
    return RagContextItem(
        chunk_id=chunk_id,
        heritage_name="첨성대",
        title=f"t-{chunk_id}",
        content_role=role,
        source_type="x",
        factuality_level=factuality,
        source_site="우리역사넷",
        source_urls=urls or [],
        context_text="본문 1줄\n본문 2줄",
        mission_hooks=hooks or [],
    )


def test_three_role_sections_rendered_separately():
    ctx = RagContextByPlace(
        place_name="첨성대",
        fact_contexts=[_item("f1", "fact_context")],
        story_materials=[_item("l1", "legend_material", factuality="mixed")],
        symbolic_materials=[_item("s1", "symbolic_material", factuality="symbolic")],
    )
    out = RagContextFormatter().format_single_place_context(ctx)
    assert "[장소: 첨성대]" in out
    assert "<사실 근거 fact_context>" in out
    assert "<스토리 소재 legend_material>" in out
    assert "<상징 소재 symbolic_material>" in out


def test_chunk_id_and_urls_and_factuality_rendered():
    ctx = RagContextByPlace(
        place_name="첨성대",
        fact_contexts=[_item("f1", "fact_context", urls=["https://x", "https://y"])],
    )
    out = RagContextFormatter().format_single_place_context(ctx)
    assert "chunk_id: f1" in out
    assert "factuality: history" in out
    assert "https://x" in out
    assert "https://y" in out


def test_mission_hooks_rendered():
    ctx = RagContextByPlace(
        place_name="첨성대",
        story_materials=[_item("l1", "legend_material",
                                factuality="mixed", hooks=["하늘과 별을 살피는 미션"])],
    )
    out = RagContextFormatter().format_single_place_context(ctx)
    assert "mission_hooks:" in out
    assert "하늘과 별을 살피는 미션" in out


def test_empty_section_marked_none():
    ctx = RagContextByPlace(place_name="첨성대")
    out = RagContextFormatter().format_single_place_context(ctx)
    assert "없음" in out


def test_truncation_when_exceeding_budget():
    big_text = "가" * 5000
    item = RagContextItem(
        chunk_id="big",
        heritage_name="첨성대",
        content_role="legend_material",
        source_type="x",
        context_text=big_text,
    )
    ctx = RagContextByPlace(place_name="첨성대", story_materials=[item])
    formatter = RagContextFormatter(max_context_chars_per_place=500)
    out = formatter.format_single_place_context(ctx)
    # The header line ([장소]) is OUTSIDE the budgeted body, so total len is small but bounded.
    assert "(생략)" in out


def test_no_contexts_returns_placeholder():
    out = RagContextFormatter().format_contexts_by_place([])
    assert out == "(RAG 자료 없음)"


def test_invalid_max_chars_rejected():
    with pytest.raises(ValueError):
        RagContextFormatter(max_context_chars_per_place=10)


# ---- 154-prompt-pass: usage_hint per role -------------------------------


def test_usage_hint_emitted_for_fact_context():
    ctx = RagContextByPlace(
        place_name="첨성대",
        fact_contexts=[_item("f1", "fact_context")],
    )
    out = RagContextFormatter().format_single_place_context(ctx)
    assert "content_role: fact_context" in out
    assert "usage_hint:" in out
    assert "사실 근거" in out


def test_usage_hint_emitted_for_legend_material_warns_against_assertion():
    ctx = RagContextByPlace(
        place_name="첨성대",
        story_materials=[_item("l1", "legend_material", factuality="mixed")],
    )
    out = RagContextFormatter().format_single_place_context(ctx)
    assert "content_role: legend_material" in out
    assert "단정 금지" in out


def test_usage_hint_emitted_for_symbolic_material():
    ctx = RagContextByPlace(
        place_name="첨성대",
        symbolic_materials=[_item("s1", "symbolic_material", factuality="symbolic")],
    )
    out = RagContextFormatter().format_single_place_context(ctx)
    assert "content_role: symbolic_material" in out
    assert "상징적" in out
