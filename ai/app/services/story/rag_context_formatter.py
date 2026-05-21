"""Format RAG search results into a prompt-friendly block, grouped by place."""
from __future__ import annotations

from app.schemas.story_schema import RagContextByPlace, RagContextItem


_DEFAULT_MAX_CHARS_PER_PLACE = 0
_TRUNCATE_NOTICE = "…(생략)"


class RagContextFormatter:
    def __init__(self, max_context_chars_per_place: int = _DEFAULT_MAX_CHARS_PER_PLACE) -> None:
        if 0 < max_context_chars_per_place < 200:
            raise ValueError("max_context_chars_per_place는 200 이상이어야 합니다.")
        self.max_chars = max_context_chars_per_place

    # ---- public --------------------------------------------------------

    def format_contexts_by_place(self, rag_contexts: list[RagContextByPlace]) -> str:
        if not rag_contexts:
            return "(RAG 자료 없음)"
        return "\n\n".join(self.format_single_place_context(c) for c in rag_contexts)

    def format_single_place_context(self, context: RagContextByPlace) -> str:
        sections = [
            self._format_items("사실 근거 fact_context", context.fact_contexts),
            self._format_items("스토리 소재 legend_material", context.story_materials),
            self._format_items("상징 소재 symbolic_material", context.symbolic_materials),
        ]
        body = "\n\n".join(sections)
        body = self._enforce_budget(body)
        return f"[장소: {context.place_name}]\n\n{body}"

    # ---- helpers -------------------------------------------------------

    @staticmethod
    def _format_items(title: str, items: list[RagContextItem]) -> str:
        header = f"<{title}>"
        if not items:
            return f"{header}\n없음"
        rendered = [header]
        for item in items:
            rendered.append(_format_one_item(item))
        return "\n".join(rendered)

    def _enforce_budget(self, body: str) -> str:
        if self.max_chars <= 0:
            return body
        if len(body) <= self.max_chars:
            return body
        keep = self.max_chars - len(_TRUNCATE_NOTICE)
        return body[: max(0, keep)] + _TRUNCATE_NOTICE


_USAGE_HINT_BY_ROLE = {
    "fact_context": "역사적 사실 설명의 근거로만 사용. 사실로 단정 가능.",
    "legend_material": "전승/이야기 분위기와 미션 소재로 사용. '전해진다 / 이야기된다 / 상상해 볼 수 있다' 로 표현하고 사실로 단정 금지.",
    "symbolic_material": "상징적 해석과 감성적 장면 구성에 사용. '상징적으로 / 의미로 해석하면' 으로 표현하고 실제 설화처럼 말하지 말 것.",
}


def _format_one_item(item: RagContextItem) -> str:
    lines = [
        f"- chunk_id: {item.chunk_id}",
        f"  heritage: {item.heritage_name}",
        f"  content_role: {item.content_role}",
    ]
    hint = _USAGE_HINT_BY_ROLE.get(item.content_role)
    if hint:
        lines.append(f"  usage_hint: {hint}")
    if item.title:
        lines.append(f"  title: {item.title}")
    if item.factuality_level:
        lines.append(f"  factuality: {item.factuality_level}")
    if item.source_site:
        lines.append(f"  source: {item.source_site}")
    if item.source_urls:
        lines.append("  urls:")
        for url in item.source_urls:
            lines.append(f"    - {url}")
    if item.mission_hooks:
        lines.append("  mission_hooks:")
        for hook in item.mission_hooks:
            lines.append(f"    - {hook}")
    if item.motifs:
        lines.append(f"  motifs: {', '.join(item.motifs)}")
    if item.tone_tags:
        lines.append(f"  tone_tags: {', '.join(item.tone_tags)}")
    lines.append("  content:")
    for content_line in item.context_text.splitlines() or [""]:
        lines.append(f"    {content_line}")
    return "\n".join(lines)
