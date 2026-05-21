"""
Tests for text_cleaner — Korean text normalisation.
"""
from __future__ import annotations

from app.data_pipeline.processors.text_cleaner import clean_text, clean_simple


class TestCleanText:
    def test_nfc_normalisation(self):
        # Ensure NFC normalisation is applied (Hanja preserved)
        text = "첨성대(瞻星臺)는 천문 관측대이다."
        result = clean_text(text)
        assert "瞻星臺" in result  # Hanja preserved
        assert "첨성대" in result

    def test_control_chars_removed(self):
        text = "테스트\x00문자\x07열"
        result = clean_text(text)
        assert "\x00" not in result
        assert "\x07" not in result
        assert "테스트" in result

    def test_double_quotes_cleaned(self):
        text = '이것은 """"""큰따옴표"""""" 테스트'
        result = clean_text(text)
        assert '""' not in result

    def test_multi_newlines_collapsed(self):
        text = "첫 줄\n\n\n\n\n둘째 줄"
        result = clean_text(text)
        assert "\n\n\n" not in result
        assert "첫 줄" in result
        assert "둘째 줄" in result

    def test_footnotes_removed(self):
        text = "본문[각주 1]이 있다[2]."
        result = clean_text(text)
        assert "[각주 1]" not in result
        assert "[2]" not in result
        assert "본문" in result

    def test_empty_input(self):
        assert clean_text(None) == ""
        assert clean_text("") == ""

    def test_whitespace_normalisation(self):
        text = "여러   공백이\t있는\u3000텍스트"
        result = clean_text(text)
        assert "  " not in result
        assert "\t" not in result


class TestCleanSimple:
    def test_basic(self):
        assert clean_simple("  신라  ") == "신라"

    def test_none(self):
        assert clean_simple(None) == ""

    def test_control_chars(self):
        assert "\x07" not in clean_simple("테스트\x07")
