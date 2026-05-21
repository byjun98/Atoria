"""
Parse and validate OpenAI Chat Completion responses for /story/intro.
"""
from __future__ import annotations

import json
import re

from pydantic import ValidationError

from app.schemas.story_schema import StoryIntroDraft


_CODE_FENCE_RE = re.compile(r"^\s*```(?:json)?\s*(.*?)\s*```\s*$", re.DOTALL)


class StoryResponseError(RuntimeError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class StoryResponseParser:
    def parse_intro_response(self, raw_content: str) -> StoryIntroDraft:
        if not raw_content or not raw_content.strip():
            raise StoryResponseError(
                "STORY_LLM_EMPTY_RESPONSE", "LLM returned empty content."
            )
        cleaned = self._strip_code_fence(raw_content)
        try:
            payload = json.loads(cleaned)
        except json.JSONDecodeError as e:
            raise StoryResponseError(
                "STORY_LLM_INVALID_JSON", f"LLM response is not valid JSON: {e}"
            ) from e
        try:
            return StoryIntroDraft.model_validate(payload)
        except ValidationError as e:
            raise StoryResponseError(
                "STORY_LLM_SCHEMA_VALIDATION_ERROR",
                f"LLM response did not match StoryIntroDraft schema: {e}",
            ) from e

    @staticmethod
    def _strip_code_fence(text: str) -> str:
        m = _CODE_FENCE_RE.match(text.strip())
        return m.group(1) if m else text
