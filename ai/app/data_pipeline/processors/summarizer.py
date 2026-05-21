"""
Summariser — produces a short summary for a record.

When ``settings.ENABLE_LLM_SUMMARY`` is ``True`` and an OpenAI client is
available, an LLM-generated summary is attempted.  Otherwise, a simple
heuristic (first N sentences) is used.
"""
from __future__ import annotations

import re


def _first_sentences(text: str, max_chars: int = 220) -> str:
    """Return the first portion of *text* up to *max_chars*."""
    text = re.sub(r"\s+", " ", (text or "")).strip()
    if len(text) <= max_chars:
        return text
    # Try to cut at sentence boundary
    cut = text[:max_chars]
    last_period = cut.rfind(".")
    if last_period > max_chars // 2:
        return cut[: last_period + 1]
    return cut + "…"


def summarise(text: str, *, use_llm: bool = False) -> str:
    """
    Generate a summary for *text*.

    Parameters
    ----------
    text : str
        The full body text to summarise.
    use_llm : bool
        If ``True``, attempt an LLM call (requires OpenAI config).
        Falls back to heuristic on failure.
    """
    if use_llm:
        try:
            return _llm_summarise(text)
        except Exception:
            pass  # fall through to heuristic

    return _first_sentences(text)


def _llm_summarise(text: str) -> str:
    """Call the OpenAI API to produce a Korean-language summary."""
    from openai import OpenAI
    from app.core.config import settings

    client = OpenAI(
        api_key=settings.OPENAI_API_KEY,
        base_url=settings.OPENAI_BASE_URL,
    )
    resp = client.chat.completions.create(
        model=settings.OPENAI_MODEL,
        messages=[
            {"role": "system", "content": "당신은 한국 문화재·설화 요약 전문가입니다. "
             "주어진 텍스트를 200자 내외의 한국어 요약문으로 작성하세요."},
            {"role": "user", "content": text[:3000]},
        ],
        max_tokens=300,
        temperature=0.3,
    )
    return resp.choices[0].message.content.strip()
