"""
Korean particle (조사) helpers for natural deterministic sentence assembly.

Pure rules — no morphology library. Handles 받침 detection for Hangul,
treats Latin/digit/URL endings with sensible fallbacks, and is idempotent
for particles that look the same regardless of 받침 (e.g. "에서").
"""
from __future__ import annotations


_HANGUL_SYLLABLE_BASE = 0xAC00
_HANGUL_SYLLABLE_END = 0xD7A3

# Latin letters / digits whose pronunciation in Korean ends with a 받침.
# Conservative: pronounced with a final consonant.
_JONGSEONG_LATIN = set("LMNRlmnr")  # "L" → 엘, "M" → 엠, "N" → 엔, "R" → 알 (모두 받침 있음)
_JONGSEONG_DIGITS = set("0136780")   # 영, 일, 삼, 육, 칠, 팔, 공 (모두 받침 있음)


_PAIRS: dict[str, tuple[str, str]] = {
    # (받침 있는 경우, 받침 없는 경우)
    "은/는": ("은", "는"),
    "이/가": ("이", "가"),
    "을/를": ("을", "를"),
    "와/과": ("과", "와"),
    "으로/로": ("으로", "로"),
}

# Particles that don't depend on 받침.
_INVARIANT: dict[str, str] = {
    "에서": "에서",
    "에": "에",
}


def has_jongseong(text: str) -> bool:
    """Return True if the *last meaningful character* ends with a 받침."""
    s = (text or "").strip()
    if not s:
        return False
    ch = s[-1]
    code = ord(ch)
    if _HANGUL_SYLLABLE_BASE <= code <= _HANGUL_SYLLABLE_END:
        return ((code - _HANGUL_SYLLABLE_BASE) % 28) != 0
    if ch.isalpha():
        return ch in _JONGSEONG_LATIN
    if ch.isdigit():
        return ch in _JONGSEONG_DIGITS
    # Punctuation / parens / quotes: peek further back.
    for prev in reversed(s[:-1]):
        if prev.isalnum() or _HANGUL_SYLLABLE_BASE <= ord(prev) <= _HANGUL_SYLLABLE_END:
            return has_jongseong(prev)
    return False


def append_josa(text: str, pair: str) -> str:
    """Return ``text`` followed by the appropriate particle.

    - For 받침-independent particles ("에서", "에") this is idempotent —
      e.g. ``append_josa("첨성대에서", "에서")`` returns ``"첨성대에서"``.
    - For empty/None text it returns the text unchanged.
    """
    if not text:
        return text or ""
    s = text
    if pair in _INVARIANT:
        suffix = _INVARIANT[pair]
        if s.endswith(suffix):
            return s
        return s + suffix
    if pair not in _PAIRS:
        # Unknown pair: just append as-is (defensive).
        return s + pair
    with_jong, without_jong = _PAIRS[pair]
    return s + (with_jong if has_jongseong(s) else without_jong)


# Convenience alias matching the spec.
with_josa = append_josa
