"""
Tone tagger — keyword-based mood / tone classification.
"""
from __future__ import annotations


TONE_RULES: dict[str, list[str]] = {
    "비극적": ["슬픔", "죽음", "희생", "눈물", "한탄", "비통", "절망", "황금", "죽"],
    "신비": ["신비", "기이", "천상", "신령", "기적", "영험"],
    "영웅": ["영웅", "용맹", "승리", "전투", "무공", "위업"],
    "교훈": ["가르침", "깨달음", "교훈", "지혜", "경계"],
    "서정": ["아름", "정취", "풍류", "경치", "서정"],
    "유머": ["웃음", "익살", "풍자", "해학"],
}


def detect_tone_tags(text: str) -> list[str]:
    """Return tone labels whose trigger keywords appear in *text*."""
    return [
        tone
        for tone, keywords in TONE_RULES.items()
        if any(kw in text for kw in keywords)
    ]
