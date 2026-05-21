"""
Thin wrapper around the OpenAI Chat Completions API for story generation.

Mirrors the style of `OpenAIEmbeddingClient` (148): the service code uses
this client so it stays testable with a fake. No LangChain.
"""
from __future__ import annotations

from typing import Any

from openai import OpenAI

from app.core.config import settings


class OpenAIChatClient:
    def __init__(
        self,
        api_key: str | None = None,
        model: str | None = None,
        base_url: str | None = None,
        timeout: int | None = None,
    ) -> None:
        self.api_key = api_key if api_key is not None else settings.OPENAI_API_KEY
        self.model = model or settings.OPENAI_MODEL
        self.base_url = base_url or settings.OPENAI_BASE_URL
        self.timeout = timeout or settings.OPENAI_REQUEST_TIMEOUT
        if not self.api_key:
            raise ValueError(
                "OPENAI_API_KEY is empty — cannot construct OpenAIChatClient."
            )
        self._client = OpenAI(
            api_key=self.api_key,
            base_url=self.base_url or None,
            timeout=self.timeout,
        )

    def create_json_chat_completion(
        self,
        messages: list[dict],
        temperature: float | None = None,
    ) -> str:
        """Call chat.completions.create with JSON mode and return content string."""
        if not messages:
            raise ValueError("messages must not be empty")
        temp = temperature if temperature is not None else settings.STORY_LLM_TEMPERATURE
        response: Any = self._client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=temp,
            response_format={"type": "json_object"},
        )
        choices = response.choices or []
        if not choices:
            raise RuntimeError("OpenAI chat completion returned no choices")
        content = choices[0].message.content
        if not content:
            raise RuntimeError("OpenAI chat completion returned empty content")
        return content
