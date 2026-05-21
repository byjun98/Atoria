"""
Build embedding-ready `context_text` for a chunk.

`context_text` = "[key: value | key: value | ...]\n<raw_text>"
"""
from __future__ import annotations

from app.schemas.chunk_schema import ChunkingSourceDocument


def _join(values: list[str], limit: int = 8) -> str:
    cleaned = [v for v in (values or []) if v]
    if not cleaned:
        return ""
    if len(cleaned) > limit:
        cleaned = cleaned[:limit]
    return ", ".join(cleaned)


class ContextBuilder:
    """Render a single-line bracketed header in front of the chunk text."""

    def build_context_text(
        self,
        raw_text: str,
        document: ChunkingSourceDocument,
    ) -> str:
        parts: list[tuple[str, str]] = []

        if document.heritage_name:
            parts.append(("문화재", document.heritage_name))
        if document.title:
            parts.append(("제목", document.title))
        if document.content_role:
            parts.append(("콘텐츠 역할", document.content_role))
        if document.source_type:
            parts.append(("자료 유형", document.source_type))
        # factuality_level must always be present when known.
        if document.factuality_level:
            parts.append(("사실성", document.factuality_level))
        if document.narrative_type:
            parts.append(("서사 유형", document.narrative_type))
        if document.folklore_status:
            parts.append(("설화 상태", document.folklore_status))
        if document.era:
            parts.append(("시대", document.era))
        if document.region:
            parts.append(("지역", document.region))

        people = _join(document.related_people)
        if people:
            parts.append(("인물", people))
        places = _join(document.related_places)
        if places:
            parts.append(("관련 장소", places))
        rels = _join(document.related_heritages)
        if rels:
            parts.append(("관련 문화재", rels))
        motifs = _join(document.motifs)
        if motifs:
            parts.append(("모티프", motifs))
        tones = _join(document.tone_tags)
        if tones:
            parts.append(("분위기", tones))

        if document.source_site:
            parts.append(("출처", document.source_site))
        if document.source_confidence:
            parts.append(("출처 신뢰도", document.source_confidence))
        # Always emit review flag for traceability.
        parts.append(("검토 필요", "true" if document.needs_review else "false"))

        header = "[" + " | ".join(f"{k}: {v}" for k, v in parts) + "]"
        return f"{header}\n{raw_text}"
