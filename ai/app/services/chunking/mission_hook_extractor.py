"""
Rule-based extractor for `mission_hooks`. No LLM calls.

Inputs:
    raw_text, document.motifs, document.story_hooks, document.mission_keywords,
    document.heritage_name

Output: short hint strings ("...로 활용 가능") capped at `max_hooks`.
"""
from __future__ import annotations

from app.schemas.chunk_schema import ChunkingSourceDocument


# Keyword → mission hook hint. Order is preserved for deterministic output.
KEYWORD_HOOKS: dict[str, str] = {
    "탑": "탑의 층수와 형태를 관찰하는 미션으로 활용 가능",
    "비석": "비석이나 글자를 찾는 관찰 미션으로 활용 가능",
    "문": "문이나 입구를 통과하며 장소의 경계를 찾는 미션으로 활용 가능",
    "길": "길의 방향과 동선을 따라가는 탐색 미션으로 활용 가능",
    "연못": "연못에 비친 풍경을 관찰하는 미션으로 활용 가능",
    "성": "성 또는 성벽과 연결된 장소 관찰 미션으로 활용 가능",
    "나무": "오래된 나무나 가지 모양을 찾는 미션으로 활용 가능",
    "숲": "숲과 연결된 전설 탐색 미션으로 활용 가능",
    "용": "용 또는 수호 모티프를 활용한 상징 찾기 미션으로 활용 가능",
    "도깨비": "도깨비 설화를 바탕으로 숨겨진 단서를 찾는 미션으로 활용 가능",
    "왕": "왕의 권위와 흔적을 찾는 미션으로 활용 가능",
    "여왕": "여왕의 지혜와 선택을 따라가는 미션으로 활용 가능",
    "공주": "공주와 관련된 인물 단서를 찾는 미션으로 활용 가능",
    "신라": "신라 시대의 흔적을 찾는 미션으로 활용 가능",
    "불상": "불상의 자세나 표정을 관찰하는 미션으로 활용 가능",
    "절": "절의 중심 공간과 이동 동선을 찾는 미션으로 활용 가능",
    "종": "종소리와 울림을 상상하는 미션으로 활용 가능",
    "무덤": "왕릉이나 고분의 형태를 관찰하는 미션으로 활용 가능",
    "고분": "고분의 형태와 주변 지형을 관찰하는 미션으로 활용 가능",
    "다리": "다리를 건너며 세계가 바뀌는 상징을 찾는 미션으로 활용 가능",
    "별": "하늘과 별을 상징하는 요소를 찾는 미션으로 활용 가능",
    "달": "달빛이나 물에 비친 풍경을 상상하는 미션으로 활용 가능",
    "밤": "밤의 분위기와 어울리는 단서를 찾는 미션으로 활용 가능",
    "그림자": "그림자와 빛의 방향을 관찰하는 미션으로 활용 가능",
    "문양": "장식과 문양을 자세히 관찰하는 미션으로 활용 가능",
    "흔적": "사라진 장소의 흔적을 찾는 미션으로 활용 가능",
    "수호": "장소를 지키는 상징물을 찾는 미션으로 활용 가능",
    "변신": "모습이 바뀌는 전설 요소를 활용한 상상 미션으로 활용 가능",
    "금관": "금관의 장식과 왕권 상징을 찾는 미션으로 활용 가능",
    "얼음": "차가운 공기와 저장 구조를 관찰하는 미션으로 활용 가능",
    "천마": "하늘을 달리는 말의 상징을 찾는 미션으로 활용 가능",
    "구름": "구름과 하늘을 상징하는 요소를 찾는 미션으로 활용 가능",
    "능": "능 주변의 길과 풍경을 따라 인물의 이야기를 떠올리는 미션으로 활용 가능",
    "산": "산길과 주변 지형을 관찰하는 미션으로 활용 가능",
    "소리": "소리와 울림을 상상하는 미션으로 활용 가능",
    "빛": "빛과 반짝임을 관찰하는 미션으로 활용 가능",
}


class MissionHookExtractor:
    """Generate up to `max_hooks` mission hints from chunk metadata."""

    def __init__(self, keyword_hooks: dict[str, str] | None = None) -> None:
        self._hooks = keyword_hooks if keyword_hooks is not None else KEYWORD_HOOKS

    def extract(
        self,
        raw_text: str,
        document: ChunkingSourceDocument,
        max_hooks: int = 3,
    ) -> list[str]:
        if max_hooks <= 0:
            return []

        # Search corpus: chunk text + motifs + mission_keywords + heritage_name.
        # story_hooks are intentionally NOT copied verbatim — they are full sentences,
        # not the short "활용 가능" hints we want here.
        corpus_parts: list[str] = []
        if raw_text:
            corpus_parts.append(raw_text)
        for kw in document.mission_keywords:
            if kw:
                corpus_parts.append(kw)
        for m in document.motifs:
            if m:
                corpus_parts.append(m)
        if document.heritage_name:
            corpus_parts.append(document.heritage_name)
        corpus = " ".join(corpus_parts)

        if not corpus.strip():
            return []

        seen: set[str] = set()
        results: list[str] = []
        for keyword, hook in self._hooks.items():
            if keyword in corpus and hook not in seen:
                seen.add(hook)
                results.append(hook)
                if len(results) >= max_hooks:
                    break
        return results
