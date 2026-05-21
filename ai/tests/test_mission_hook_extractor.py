"""Tests for MissionHookExtractor."""
from __future__ import annotations

import pytest

from app.schemas.chunk_schema import ChunkingSourceDocument
from app.services.chunking.mission_hook_extractor import MissionHookExtractor


def _doc(**overrides) -> ChunkingSourceDocument:
    base = dict(
        source_record_id="r",
        source_type="heritage_legend",
        content_role="legend_material",
        heritage_name="첨성대",
        text="x",
    )
    base.update(overrides)
    return ChunkingSourceDocument(**base)


@pytest.fixture
def extractor() -> MissionHookExtractor:
    return MissionHookExtractor()


def test_tap_keyword_yields_tap_hook(extractor):
    hooks = extractor.extract("이 탑은 삼층 구조이다.", _doc())
    assert any("탑" in h for h in hooks)


def test_jong_keyword_yields_bell_hook(extractor):
    hooks = extractor.extract("종이 깊은 울림으로 퍼졌다.", _doc(heritage_name="에밀레종"))
    assert any("종소리" in h or "울림" in h for h in hooks)


def test_byeol_keyword_yields_star_hook(extractor):
    hooks = extractor.extract("밤하늘의 별을 살피던 신라인들.", _doc())
    assert any("별" in h for h in hooks)


def test_geumgwan_keyword_yields_crown_hook(extractor):
    hooks = extractor.extract("금관의 가지 장식이 빛났다.", _doc(heritage_name="천마총 금관"))
    assert any("금관" in h for h in hooks)


def test_pond_keyword_yields_pond_hook(extractor):
    hooks = extractor.extract("연못 위에 달빛이 비쳤다.", _doc())
    assert any("연못" in h for h in hooks)


def test_mission_keywords_drive_hooks_even_without_text_match(extractor):
    hooks = extractor.extract(
        "텍스트에는 일반 단어만.",
        _doc(mission_keywords=["천마"]),
    )
    assert any("하늘을 달리는 말" in h for h in hooks)


def test_duplicate_hooks_removed(extractor):
    # "탑" appears multiple times → only one "탑" hook should be emitted.
    hooks = extractor.extract("탑 탑 탑 탑 탑.", _doc(motifs=["탑"]))
    tap_hooks = [h for h in hooks if "탑의 층수" in h]
    assert len(tap_hooks) == 1


def test_max_hooks_caps_output(extractor):
    text = "탑과 별과 종과 연못과 금관과 다리가 모두 있다."
    hooks = extractor.extract(text, _doc(), max_hooks=3)
    assert len(hooks) <= 3


def test_no_keyword_returns_empty(extractor):
    hooks = extractor.extract("그저 평범한 한 줄.", _doc(heritage_name=""))
    assert hooks == []
