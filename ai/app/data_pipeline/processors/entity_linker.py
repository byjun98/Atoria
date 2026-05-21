"""
Entity linker — matches the 15 selected heritages, known people, and
known places in free text via alias-dictionary lookup.
"""
from __future__ import annotations

from app.data_pipeline.registry import (
    SELECTED_HERITAGES,
    KNOWN_PEOPLE,
    KNOWN_PLACES,
    _ALIAS_MAP,
)


def _extract_hits(text: str, vocab: list[str]) -> list[str]:
    """Return terms from *vocab* that appear in *text*, preserving order."""
    out: list[str] = []
    for term in vocab:
        if term in text and term not in out:
            out.append(term)
    return out


def extract_related_heritages(text: str) -> list[str]:
    """Find canonical heritage names mentioned in *text*."""
    seen: set[str] = set()
    result: list[str] = []
    for name, entity in _ALIAS_MAP.items():
        if name in text and entity.canonical_name not in seen:
            seen.add(entity.canonical_name)
            result.append(entity.canonical_name)
    return result


def extract_related_people(text: str) -> list[str]:
    """Find known historical figures mentioned in *text*."""
    return _extract_hits(text, KNOWN_PEOPLE)


def extract_related_places(text: str) -> list[str]:
    """Find known place names mentioned in *text*."""
    return _extract_hits(text, KNOWN_PLACES)


def link_entities(text: str) -> dict[str, list[str]]:
    """
    Run all entity extractors on *text* and return a dict with keys
    ``related_heritages``, ``related_people``, ``related_places``.
    """
    return {
        "related_heritages": extract_related_heritages(text),
        "related_people": extract_related_people(text),
        "related_places": extract_related_places(text),
    }
