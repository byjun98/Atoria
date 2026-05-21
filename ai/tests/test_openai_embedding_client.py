"""Unit tests for OpenAIEmbeddingClient (no real API calls)."""
from __future__ import annotations

from types import SimpleNamespace

import pytest

from app.clients.openai_embedding_client import OpenAIEmbeddingClient


def _make_client(monkeypatch, embeddings_response, dim=4):
    """Build an OpenAIEmbeddingClient whose underlying SDK is stubbed."""

    class FakeEmbeddings:
        def create(self, *, model, input):
            self.last_model = model
            self.last_input = input
            return embeddings_response

    class FakeOpenAI:
        def __init__(self, *args, **kwargs):
            self.embeddings = FakeEmbeddings()

    monkeypatch.setattr("app.clients.openai_embedding_client.OpenAI", FakeOpenAI)
    return OpenAIEmbeddingClient(
        api_key="test-key",
        model="test-model",
        base_url="",
        expected_dim=dim,
        request_timeout=10,
    )


def _resp(vectors):
    return SimpleNamespace(data=[SimpleNamespace(embedding=v) for v in vectors])


def test_missing_api_key_raises(monkeypatch):
    monkeypatch.setattr("app.clients.openai_embedding_client.settings.OPENAI_API_KEY", "")
    with pytest.raises(ValueError):
        OpenAIEmbeddingClient(api_key="")


def test_embed_text_empty_raises(monkeypatch):
    client = _make_client(monkeypatch, _resp([[0.1, 0.2, 0.3, 0.4]]), dim=4)
    with pytest.raises(ValueError):
        client.embed_text("   ")


def test_embed_texts_empty_input_returns_empty(monkeypatch):
    client = _make_client(monkeypatch, _resp([]))
    assert client.embed_texts([]) == []


def test_embed_texts_normal_path(monkeypatch):
    vecs = [[0.1, 0.2, 0.3, 0.4], [0.5, 0.6, 0.7, 0.8]]
    client = _make_client(monkeypatch, _resp(vecs), dim=4)
    out = client.embed_texts(["a", "b"])
    assert out == vecs


def test_count_mismatch_raises(monkeypatch):
    client = _make_client(monkeypatch, _resp([[0.1, 0.2, 0.3, 0.4]]), dim=4)
    with pytest.raises(ValueError, match="returned 1 embeddings for 2"):
        client.embed_texts(["a", "b"])


def test_dim_mismatch_raises(monkeypatch):
    client = _make_client(monkeypatch, _resp([[0.1, 0.2, 0.3]]), dim=4)
    with pytest.raises(ValueError, match="dim=3"):
        client.embed_texts(["a"])


def test_embed_texts_rejects_blank_member(monkeypatch):
    client = _make_client(monkeypatch, _resp([[0.0] * 4, [0.0] * 4]))
    with pytest.raises(ValueError, match=r"input\[1\] is empty"):
        client.embed_texts(["a", "  "])
