"""
Normalise heritage_context and heritage_legend_materials JSON dicts into a
common ChunkingSourceDocument shape. The original JSON files are not modified.
"""
from __future__ import annotations

import re
from typing import Any

from app.schemas.chunk_schema import ChunkingSourceDocument


# --- summary noise filters (for 한국민족문화대백과 summary fields) ----------

# 한자 낱글자 풀이 줄: "慶", single CJK char alone on a line
_HANJA_SINGLE_RE = re.compile(r"^[㐀-鿿]$")
# 부수/총획 라벨
_RADICAL_LINE_RE = re.compile(r"^부수\s*\S+\s*총획\s*\d+$")
# 한자 풀이: "하례하다 [경]", "착하다 [경]"
_HANJA_GLOSS_RE = re.compile(r"^[\w가-힣\s]+\[[㐀-鿿가-힣]+\]$")
# 너무 짧은 줄 (1~2글자)
_SHORT_LINE_LEN = 2
# 반복되는 라벨/카테고리
_LABEL_NOISE = {
    "예술·체육", "유적", "남북국", "국가문화유산",
    "명칭", "분류", "소재지", "수량/면적", "지정종목", "지정일",
    "건립시기", "성격", "유형", "시대", "공유", "링크", "프린트",
    "닫기", "더보기", "이전", "다음", "메뉴", "검색",
}


class SourceDocumentNormalizer:
    """Convert raw heritage_context / heritage_legend_materials dicts into
    `ChunkingSourceDocument` instances."""

    # ---- public API ------------------------------------------------------

    def normalize(self, item: dict[str, Any]) -> ChunkingSourceDocument:
        if "legend_id" in item:
            return self.normalize_legend_material(item)
        if "record_id" in item or item.get("source_type") == "heritage_context":
            return self.normalize_heritage_context(item)
        raise ValueError(
            "Unknown source item: missing both 'legend_id' and 'record_id'"
        )

    def normalize_many(
        self, items: list[dict[str, Any]]
    ) -> list[ChunkingSourceDocument]:
        return [self.normalize(it) for it in items]

    # ---- heritage_context ------------------------------------------------

    def normalize_heritage_context(
        self, item: dict[str, Any]
    ) -> ChunkingSourceDocument:
        record_id = item.get("record_id") or ""
        if not record_id:
            raise ValueError("heritage_context item missing 'record_id'")

        source_url = item.get("source_url")
        source_urls = [source_url] if source_url else []

        text = self.build_context_text_source(item)
        if not text.strip():
            text = item.get("definition", "") or item.get("title", "") or " "

        meta_in = item.get("metadata") or {}
        needs_review = bool(meta_in.get("needs_review", False))

        return ChunkingSourceDocument(
            source_record_id=record_id,
            source_type="heritage_context",
            source_site=item.get("source_site"),
            source_urls=source_urls,
            content_role="fact_context",
            heritage_name=item.get("heritage_name") or "",
            title=item.get("title"),
            text=text,
            factuality_level=item.get("factuality_level") or "history",
            narrative_type=None,
            folklore_status=None,
            source_confidence=None,
            needs_review=needs_review,
            era=item.get("era"),
            region=item.get("region"),
            related_heritages=list(item.get("related_heritages") or []),
            related_people=list(item.get("related_people") or []),
            related_places=list(item.get("related_places") or []),
            motifs=list(item.get("motifs") or []),
            tone_tags=list(item.get("tone_tags") or []),
            story_hooks=list(item.get("story_hooks") or []),
            mission_keywords=[],
            metadata=dict(meta_in),
        )

    def build_context_text_source(self, item: dict[str, Any]) -> str:
        """Pick the best textual body for a heritage_context item.

        Priority:
            1) narrative_excerpts (joined as paragraphs)
            2) key_facts (rendered as 'k: v' lines)
            3) definition + cleaned summary
            4) definition only
        """
        excerpts = item.get("narrative_excerpts") or []
        if excerpts:
            joined = "\n\n".join(s for s in excerpts if isinstance(s, str) and s.strip())
            if joined.strip():
                return joined

        key_facts = item.get("key_facts") or {}
        if isinstance(key_facts, dict) and key_facts:
            lines = [f"{k}: {v}" for k, v in key_facts.items() if v]
            if lines:
                return "\n".join(lines)

        definition = (item.get("definition") or "").strip()
        cleaned = self._clean_encykorea_summary(item.get("summary") or "")
        if definition and cleaned:
            # Avoid duplicating definition if it appears verbatim in cleaned summary.
            if definition in cleaned:
                return cleaned
            return f"{definition}\n\n{cleaned}"
        if definition:
            return definition
        if cleaned:
            return cleaned
        # Fallback: use category description (encykorea sometimes inlines it).
        cat = item.get("category")
        if isinstance(cat, list) and cat:
            return str(cat[0])
        if isinstance(cat, str):
            return cat
        return ""

    @staticmethod
    def _clean_encykorea_summary(summary: str) -> str:
        """Strip 한자 풀이 / 부수·총획 / UI labels from encykorea summary."""
        if not summary:
            return ""
        out: list[str] = []
        for raw_line in summary.splitlines():
            line = raw_line.strip()
            if not line:
                continue
            if _HANJA_SINGLE_RE.match(line):
                continue
            if _RADICAL_LINE_RE.match(line):
                continue
            if _HANJA_GLOSS_RE.match(line):
                continue
            if line in _LABEL_NOISE:
                continue
            if len(line) <= _SHORT_LINE_LEN:
                continue
            out.append(line)
        return "\n".join(out).strip()

    # ---- heritage_legend_materials --------------------------------------

    def normalize_legend_material(
        self, item: dict[str, Any]
    ) -> ChunkingSourceDocument:
        legend_id = item.get("legend_id") or ""
        if not legend_id:
            raise ValueError("legend item missing 'legend_id'")

        text = self.build_legend_text_source(item)
        if not text.strip():
            raise ValueError(
                f"legend item '{legend_id}' has empty raw_text/story_summary"
            )

        factuality = item.get("factuality_level")
        content_role = (
            "symbolic_material" if factuality == "symbolic" else "legend_material"
        )

        meta = dict(item.get("metadata") or {})
        # Fold a few useful provenance fields into metadata for downstream use.
        for k in ("source_name", "source_note", "primary_source_site"):
            if item.get(k) and k not in meta:
                meta[k] = item[k]

        return ChunkingSourceDocument(
            source_record_id=legend_id,
            source_type=item.get("source_type") or "heritage_legend",
            source_site=item.get("primary_source_site"),
            source_urls=list(item.get("source_urls") or []),
            content_role=content_role,
            heritage_name=item.get("heritage_name") or "",
            title=item.get("title"),
            text=text,
            factuality_level=factuality,
            narrative_type=item.get("narrative_type"),
            folklore_status=item.get("folklore_status"),
            source_confidence=item.get("source_confidence"),
            needs_review=bool(item.get("needs_review", False)),
            era=item.get("era"),
            region=item.get("region"),
            related_heritages=list(item.get("related_heritages") or []),
            related_people=list(item.get("related_people") or []),
            related_places=list(item.get("related_places") or []),
            motifs=list(item.get("motifs") or []),
            tone_tags=list(item.get("tone_tags") or []),
            story_hooks=list(item.get("story_hooks") or []),
            mission_keywords=list(item.get("mission_keywords") or []),
            metadata=meta,
        )

    def build_legend_text_source(self, item: dict[str, Any]) -> str:
        raw_text = (item.get("raw_text") or "").strip()
        if raw_text:
            return raw_text
        story_summary = (item.get("story_summary") or "").strip()
        story_hooks = item.get("story_hooks") or []
        parts = []
        if story_summary:
            parts.append(story_summary)
        if story_hooks:
            parts.append("\n".join(f"- {h}" for h in story_hooks if h))
        return "\n\n".join(parts).strip()
