"""
Tests for entity_linker — verifying that heritage, people, and place
mentions are correctly extracted from Korean text.
"""
from __future__ import annotations

from app.data_pipeline.processors.entity_linker import (
    extract_related_heritages,
    extract_related_people,
    extract_related_places,
    link_entities,
)


class TestEntityLinker:
    SAMPLE_TEXT = (
        "선덕여왕릉은 경주 남산 기슭에 위치한 신라 제27대 선덕여왕의 능이다. "
        "선덕여왕은 첨성대를 축조한 것으로 유명하며, 불국사와도 관련이 깊다. "
        "김유신 장군과 함께 신라를 이끈 위대한 군주였다."
    )

    def test_extract_heritages(self):
        result = extract_related_heritages(self.SAMPLE_TEXT)
        assert "첨성대" in result
        assert "선덕여왕릉" in result

    def test_extract_people(self):
        result = extract_related_people(self.SAMPLE_TEXT)
        assert "선덕여왕" in result
        assert "김유신" in result

    def test_extract_places(self):
        result = extract_related_places(self.SAMPLE_TEXT)
        assert "경주" in result
        assert "남산" in result
        assert "불국사" in result

    def test_link_entities_returns_all_keys(self):
        result = link_entities(self.SAMPLE_TEXT)
        assert "related_heritages" in result
        assert "related_people" in result
        assert "related_places" in result

    def test_empty_text(self):
        result = link_entities("")
        assert result["related_heritages"] == []
        assert result["related_people"] == []
        assert result["related_places"] == []

    def test_emille_bell_alias(self):
        text = "성덕대왕신종은 에밀레종이라는 별칭으로도 불린다."
        result = extract_related_heritages(text)
        assert "에밀레종" in result

    def test_cheonmachong_gold_crown(self):
        text = "천마총에서 출토된 천마총 금관은 신라의 대표적인 유물이다."
        result = extract_related_heritages(text)
        assert "천마총" in result
        assert "천마총 금관" in result
