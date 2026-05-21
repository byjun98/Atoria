"""
Story hook generator — creates narrative hooks from motifs, people, and heritage names.
"""
from __future__ import annotations


def generate_story_hooks(
    title: str,
    motifs: list[str],
    people: list[str],
    heritages: list[str] | None = None,
    *,
    max_hooks: int = 4,
) -> list[str]:
    """
    Generate up to *max_hooks* story hooks by combining motifs, people,
    and heritage names.
    """
    hooks: list[str] = []

    if people:
        hooks.append(f"{people[0]}의 눈을 통해 바라본 {title}")
    if "탄생" in motifs:
        hooks.append(f"{title}에(를) 얽힌 신비한 탄생 이야기를 아시나요?")
    if "전쟁" in motifs or "황금" in motifs:
        hooks.append("전장에서 빛나던 황금 장식이 전하는 1천년 전 이야기")
    if "사랑" in motifs:
        hooks.append(f"{title}에(를) 담긴 애틋한 사랑 이야기")
    if "신비" in motifs:
        hooks.append(f"신비로운 전설이 깃든 {title}의 비밀")
    if "불교" in motifs:
        hooks.append(f"부처의 가르침이 돌에 새겨진 {title}")
    if "천문" in motifs:
        hooks.append(f"별을 관측하던 신라인들, {title}에서 무엇을 보았을까")

    if heritages:
        for h in heritages[:2]:
            if h != title:
                hooks.append(f"{title}과(와) {h}를 잇는 숨겨진 이야기")

    if not hooks:
        hooks.append(f"옛 이야기가 숨쉬는 {title}")

    return hooks[:max_hooks]
