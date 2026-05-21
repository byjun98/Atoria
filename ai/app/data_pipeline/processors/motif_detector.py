"""
Motif detector — keyword + rule-based motif labelling.
"""
from __future__ import annotations


# Motif → trigger keywords
MOTIF_RULES: dict[str, list[str]] = {
    "탄생": ["태어나", "탄생", "알에서", "알을 낳", "건국", "탄생"],
    "전쟁": ["전쟁", "전투", "군사 행", "싸움", "침략", "정벌"],
    "왕권": ["왕", "왕비", "임금", "궁궐"],
    "황금": ["황금", "금관", "금보"],
    "사랑": ["사랑", "장사", "애틋", "연인"],
    "동물": ["호랑이", "용", "거북", "말", "새", "봉황"],
    "신비": ["신비", "기적", "신령", "부처"],
    "효도": ["효도", "효자", "효녀", "은혜"],
    "불교": ["불교", "부처", "석가", "보살", "범종", "절"],
    "천문": ["천문", "별", "해", "달", "하늘", "관측"],
}


def detect_motifs(text: str) -> list[str]:
    """Return motif labels whose trigger keywords appear in *text*."""
    return [
        motif
        for motif, keywords in MOTIF_RULES.items()
        if any(kw in text for kw in keywords)
    ]
