"""API tests for /rag/search using FastAPI dependency override."""
from __future__ import annotations

from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.api.v1.rag_router import get_rag_search_service
from app.main import app
from app.schemas.rag_search_schema import RagSearchData, RagSearchResultItem


class FakeService:
    def __init__(self):
        self.last_search = None
        self.last_by_heritage = None

    def _item(self, chunk_id):
        return RagSearchResultItem(
            chunk_id=chunk_id,
            source_record_id=chunk_id.rsplit("_chunk_", 1)[0],
            heritage_name="첨성대",
            title="t",
            content_role="legend_material",
            source_type="historical_anecdote",
            factuality_level="mixed",
            source_site="우리역사넷",
            source_urls=["https://x"],
            distance=0.21,
            similarity=0.79,
            context_text="[h]\nbody",
            mission_hooks=["hook"],
            related_heritages=["첨성대"],
            related_people=["선덕여왕"],
            related_places=["경주"],
            motifs=["별"],
        )

    def search(self, payload):
        self.last_search = payload
        return RagSearchData(
            query=payload.query,
            embedding_model="fake-model",
            top_k=payload.top_k or 5,
            result_count=1,
            results=[self._item("c_chunk_000")],
            filters={"content_roles": list(payload.content_roles or [])},
        )

    def search_by_heritage(self, payload):
        self.last_by_heritage = payload
        return RagSearchData(
            query=payload.query,
            embedding_model="fake-model",
            top_k=payload.top_k or 5,
            result_count=1,
            results=[self._item("c_chunk_000")],
            filters={"heritage_names": [payload.heritage_name]},
        )


@pytest.fixture
def fake_service():
    svc = FakeService()
    app.dependency_overrides[get_rag_search_service] = lambda: svc
    yield svc
    app.dependency_overrides.pop(get_rag_search_service, None)


@pytest.fixture
def client():
    return TestClient(app)


def test_search_happy_path(client, fake_service):
    r = client.post(
        "/rag/search",
        json={
            "query": "첨성대 선덕여왕 별 미션",
            "top_k": 5,
            "content_roles": ["fact_context", "legend_material"],
        },
    )
    assert r.status_code == 200
    body = r.json()
    assert body["success"] is True
    assert body["data"]["query"] == "첨성대 선덕여왕 별 미션"
    assert body["data"]["embedding_model"] == "fake-model"
    assert body["data"]["result_count"] == 1
    assert body["data"]["results"][0]["raw_text"] is None  # default include_raw_text=False
    assert body["data"]["results"][0]["context_text"]      # default include_context_text=True


def test_empty_query_returns_422(client, fake_service):
    r = client.post("/rag/search", json={"query": "   "})
    assert r.status_code == 422


def test_top_k_over_max_returns_422(client, fake_service):
    r = client.post("/rag/search", json={"query": "x", "top_k": 9999})
    assert r.status_code == 422


def test_invalid_content_role_returns_422(client, fake_service):
    r = client.post("/rag/search", json={"query": "x", "content_roles": ["bogus_role"]})
    assert r.status_code == 422


def test_filters_propagated_to_service(client, fake_service):
    client.post(
        "/rag/search",
        json={"query": "x", "content_roles": ["fact_context"]},
    )
    assert fake_service.last_search.content_roles == ["fact_context"]


def test_search_by_heritage(client, fake_service):
    r = client.post(
        "/rag/search/by-heritage",
        json={"heritage_name": "첨성대"},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["success"] is True
    assert fake_service.last_by_heritage.heritage_name == "첨성대"
    # query falls back to heritage_name when omitted
    assert fake_service.last_by_heritage.query == "첨성대"
    assert body["data"]["filters"]["heritage_names"] == ["첨성대"]


def test_default_include_flags_when_omitted(client, fake_service):
    client.post("/rag/search", json={"query": "x"})
    assert fake_service.last_search.include_context_text is True
    assert fake_service.last_search.include_raw_text is False
