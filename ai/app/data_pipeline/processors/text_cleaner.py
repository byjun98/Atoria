"""
Text cleaner — normalises Korean text for the data pipeline.

Ported from practice repo ``normalize_csv.py`` with additional rules for
Hanja bracket notation and NFC normalisation.
"""
from __future__ import annotations

import re
import unicodedata


# -- compiled patterns ------------------------------------------------------

_CONTROL_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_REPEAT_QUOTES_RE = re.compile(r'"{2,}')
_MULTISPACE_RE = re.compile(r"[ \t\u3000]+")
_MULTINEWLINE_RE = re.compile(r"\n{3,}")
_TRAILING_MARK_RE = re.compile(r'(?:\s*["」]?\s*){2,}$')
_FOOTNOTE_RE = re.compile(r"\[각주\s*\d*\]|\[\d+\]|<ref[^>]*>.*?</ref>", re.DOTALL)


def clean_text(raw: str | None) -> str:
    """
    Full normalisation pass for body / original_text fields.

    Steps:
    1. Unicode NFC normalisation (한자 병기 보존).
    2. Strip control characters.
    3. Remove CSV quoting artefacts (repeated ``"``).
    4. Strip inline footnotes / reference tags.
    5. Normalise whitespace and newlines.
    """
    if not raw:
        return ""
    s = unicodedata.normalize("NFC", raw)
    s = _CONTROL_RE.sub("", s)
    s = _REPEAT_QUOTES_RE.sub(" ", s)
    s = _FOOTNOTE_RE.sub("", s)
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    s = _MULTISPACE_RE.sub(" ", s)
    s = _MULTINEWLINE_RE.sub("\n\n", s)
    s = _TRAILING_MARK_RE.sub("", s)
    return s.strip()


def clean_simple(raw: str | None) -> str:
    """Light normalisation for short metadata fields (title, era, etc.)."""
    if not raw:
        return ""
    s = unicodedata.normalize("NFC", raw)
    s = _CONTROL_RE.sub("", s)
    s = _MULTISPACE_RE.sub(" ", s)
    return s.strip()
