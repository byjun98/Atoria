"""
Build image prompt *hints* for the E-book draft.

These are short Korean strings that downstream renderer / illustrator can
hand to an image generator. We do NOT call any image API here; we never
describe real people; user photos are referenced as "사용자가 남긴 사진을
배치할 영역" only.
"""
from __future__ import annotations

import re

from app.schemas.ebook_schema import (
    EbookDraftGenerationRequest,
    EbookMissionInput,
    EbookMissionResultInput,
    EbookPlaceInput,
)
from app.services.ebook._josa import append_josa


_BASE_STYLE = "따뜻한 동화책 삽화 스타일, 가족 친화적이고 부드러운 색감"
_NO_REAL_PERSON = "실제 인물 묘사는 피하고 뒷모습이나 상징적인 실루엣으로 표현"
_HISTORICAL = "실제 문화재의 형태를 과도하게 왜곡하지 않음"
_CHILD_SAFE = "어린이를 대상으로 하므로 공포·폭력 표현 금지"

# 시각 키워드에서 제외할 일반 단어 (장소 고유 단서가 되지 못함)
_VISUAL_STOPWORDS: set[str] = {
    "찾기", "찾아보기", "찾아", "조사", "탐험", "관찰", "관찰하기",
    "포착하기", "포착", "둘러보", "둘러보기",
    "미션", "이야기", "문화재", "유적", "여행", "체험",
    "해보세요", "보세요", "걸으며", "따라", "따라가", "따라가기",
    "가족", "함께", "오늘", "장소", "주변", "방문",
    "생각", "상상", "기록", "활동",
    # 짧은 조사·어미 (안전망)
    "이", "가", "은", "는", "을", "를", "와", "과", "의", "에", "에서",
}

# 명사 후보 추출용 — 한글 2자 이상 연속을 token 으로 본다 (조사 부착 X 가정).
_HANGUL_TOKEN_RE = re.compile(r"[가-힣]{2,}")


def _tokenise(text: str | None) -> list[str]:
    if not text:
        return []
    return _HANGUL_TOKEN_RE.findall(text)


def _strip_josa(token: str) -> str:
    """Heuristic — drop trailing single-char josa from a token if length permits."""
    if len(token) <= 2:
        return token
    if token[-1] in {"의", "은", "는", "이", "가", "을", "를", "와", "과", "에"}:
        return token[:-1]
    return token


def _is_meaningful_token(token: str, *, allow_short: bool = False) -> bool:
    """Decide whether a token survives the visual-keyword filter.

    `allow_short=True` is used for `selected_keywords` (trusted user input)
    where single-syllable terms like "별", "창", "돌" are valid.
    """
    if not token:
        return False
    if not allow_short and len(token) < 2:
        return False
    if token in _VISUAL_STOPWORDS:
        return False
    # Drop tokens that are clearly verb endings ("…보세요", "…해보세요", "…어요").
    if any(token.endswith(suf) for suf in ("세요", "해요", "어요", "아요")):
        return False
    return True


class EbookImagePromptBuilder:
    """Pure string composition. No external calls."""

    # ---- public --------------------------------------------------------

    def build_cover_image_prompt(self, request: EbookDraftGenerationRequest) -> str:
        nick = (request.user_profile.nickname or "").strip()
        if nick:
            actor_clause = f"{append_josa(nick, '와/과')} 가족"
        else:
            actor_clause = "사용자 가족"
        theme = request.user_profile.story_theme or "경주 문화재 탐방"
        place_names = [
            p.place_name for p in sorted(request.places, key=lambda p: p.sequence)
        ]
        place_phrase = " · ".join(place_names[:3]) if place_names else "경주 문화재"
        return (
            f"{_BASE_STYLE}, E-book 표지 일러스트, "
            f"{actor_clause}이 함께 탐방한 코스({place_phrase})의 상징 장면, "
            f"테마: {theme}, {_NO_REAL_PERSON}, {_HISTORICAL}, {_CHILD_SAFE}"
        )

    def build_place_page_image_prompt(
        self,
        place: EbookPlaceInput,
        mission: EbookMissionInput | None,
        mission_result: EbookMissionResultInput | None,
    ) -> str:
        keywords = self.extract_visual_keywords(place, mission, mission_result)
        focus = ", ".join(keywords) if keywords else place.place_name
        photo_clause = ""
        if mission_result and mission_result.photo_urls:
            photo_clause = ", 그리고 사용자가 남긴 사진을 배치할 영역을 한 곳 마련"
        place_eul = append_josa(place.place_name, "을/를")
        return (
            f"{_BASE_STYLE}, E-book 본문 삽화, "
            f"{place_eul} 중심으로 한 장면 — {focus}{photo_clause}, "
            f"{_NO_REAL_PERSON}, {_HISTORICAL}, {_CHILD_SAFE}"
        )

    # ---- visual keyword extraction ------------------------------------

    def extract_visual_keywords(
        self,
        place: EbookPlaceInput,
        mission: EbookMissionInput | None,
        mission_result: EbookMissionResultInput | None = None,
        *,
        max_keywords: int = 6,
    ) -> list[str]:
        """장소·미션·결과에서 시각적으로 유의미한 키워드를 우선순위대로 모은다.

        우선순위:
          1) selected_keywords  (사용자가 직접 고른 단서)
          2) mission.description  (현장에서 관찰 대상)
          3) mission.title  (장소 고유 소재)
          4) place.place_name
        """
        out: list[str] = []

        def _add(token: str, *, allow_short: bool = False) -> None:
            t = _strip_josa(token).strip()
            if not _is_meaningful_token(t, allow_short=allow_short):
                return
            # don't drop place_name itself; we'll re-include it last
            if t == place.place_name:
                return
            if t in out:
                return
            out.append(t)

        # 1) selected_keywords (믿을 만한 신호 — 단음절 허용)
        if mission_result and mission_result.selected_keywords:
            for kw in mission_result.selected_keywords:
                _add(kw, allow_short=True)

        # 2) mission.description
        if mission and mission.description:
            for tok in _tokenise(mission.description):
                _add(tok)

        # 3) mission.title (조사 / stopword 제거)
        if mission and mission.title:
            for tok in _tokenise(mission.title):
                _add(tok)

        # 4) place.place_name 은 항상 맨 앞에 한 번
        result = [place.place_name] + [k for k in out if k]
        # de-dup preserving order, cap
        seen: list[str] = []
        for k in result:
            if k and k not in seen:
                seen.append(k)
            if len(seen) >= max_keywords:
                break
        return seen
