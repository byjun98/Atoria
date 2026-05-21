"""
Thin wrapper around the OpenAI Embeddings API.

Service code talks to this client, not to the OpenAI SDK directly, so the
embedding service stays testable with a fake. No chat-completion calls;
embeddings only.
"""
from __future__ import annotations

from typing import Any

from openai import OpenAI

from app.core.config import settings
from app.core.logging import get_logger

_logger = get_logger(__name__)


class OpenAIEmbeddingClient:
    def __init__(
        self,
        api_key: str | None = None,
        model: str | None = None,
        base_url: str | None = None,
        expected_dim: int | None = None,
        request_timeout: int | None = None,
    ) -> None:
        self.api_key = api_key if api_key is not None else settings.OPENAI_API_KEY
        self.model = model or settings.OPENAI_EMBEDDING_MODEL
        self.base_url = base_url or settings.OPENAI_BASE_URL
        self.expected_dim = (
            expected_dim if expected_dim is not None else settings.RAG_EMBEDDING_DIM
        )
        self.timeout = request_timeout or settings.OPENAI_REQUEST_TIMEOUT
        if not self.api_key:
            raise ValueError(
                "OPENAI_API_KEY is empty — cannot construct OpenAIEmbeddingClient."
            )
        self._client = OpenAI(
            api_key=self.api_key,
            base_url=self.base_url or None,
            timeout=self.timeout,
        )

    # ---- public ---------------------------------------------------------

    def embed_text(self, text: str) -> list[float]:
        if not text or not text.strip():
            raise ValueError("embed_text: input text must not be empty")
        out = self.embed_texts([text])
        return out[0]

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        for i, t in enumerate(texts):
            if not t or not t.strip():
                raise ValueError(f"embed_texts: input[{i}] is empty")

        _logger.info(
            "openai_embeddings_api_call",
            model=self.model,
            batch_size=len(texts),
            query_text=texts[0][:120] if texts else "",
        )
        response: Any = self._client.embeddings.create(
            model=self.model,
            input=texts,
        )
        embeddings = [item.embedding for item in response.data]

        if len(embeddings) != len(texts):
            raise ValueError(
                f"OpenAI returned {len(embeddings)} embeddings for {len(texts)} inputs"
            )
        for i, e in enumerate(embeddings):
            if len(e) != self.expected_dim:
                raise ValueError(
                    f"OpenAI returned dim={len(e)} for input[{i}], "
                    f"expected {self.expected_dim} (model={self.model})"
                )
        return embeddings
