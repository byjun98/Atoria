"""Unit tests for RagSearchService — fakes, no DB / no OpenAI."""
from __future__ import annotations

from types import SimpleNamespace

import pytest

from app.schemas.rag_search_schema import (
    RagSearchByHeritageRequest,
    RagSearchRequest,
)
from app.services.rag.rag_search_service import RagSearchService


def _chunk(chunk_id, *, heritage="첨성대", role="legend_material", factuality="mixed",
           source_type="historical_anecdote"):
    return SimpleNamespace(
        chunk_id=chunk_id,
        source_record_id=chunk_id.rsplit("_chunk_", 1)[0],
        heritage_name=heritage,
        title=f"t-{chunk_id}",
        content_role=role,
        source_type=source_type,
        factuality_level=factuality,
        narrative_type=None,
        folklore_status=None,
        source_site="우리역사넷",
        source_urls=["https://contents.history.go.kr/x"],
        raw_text="raw",
        context_text="[h]\nbody",
        mission_hooks=["hook"],
        related_heritages=[heritage],
        related_people=[],
        related_places=["경주"],
        motifs=["별"],
        tone_tags=["신비"],
        extra_metadata={"language": "ko"},
    )


class FakeRepo:
    def __init__(self, chunks: list):
        self._chunks = chunks
        self.last_call: dict = {}

    def search_similar_chunks(self, **kw):
        self.last_call = kw
        chunks = list(self._chunks)
        # apply filters as a real repo would
        for key, attr in (
            ("heritage_names", "heritage_name"),
            ("content_roles", "content_role"),
            ("factuality_levels", "factuality_level"),
            ("source_types", "source_type"),
        ):
            vals = kw.get(key)
            if vals:
                chunks = [c for c in chunks if getattr(c, attr) in vals]
        # synthesize ascending distances
        results = [(c, 0.1 + i * 0.05) for i, c in enumerate(chunks)]
        thr = kw.get("distance_threshold")
        if thr is not None:
            results = [(c, d) for c, d in results if d <= thr]
        return results[: kw["top_k"]]


class FakeClient:
    expected_dim = 4
    model = "fake-model"

    def __init__(self):
        self.calls: list[str] = []

    def embed_text(self, text):
        self.calls.append(text)
        return [0.1, 0.2, 0.3, 0.4]


def _service(chunks):
    return RagSearchService(
        repository=FakeRepo(chunks),
        embedding_client=FakeClient(),
        default_top_k=5,
        max_top_k=10,
    )


def test_basic_search_returns_items_in_order():
    svc = _service([_chunk(f"c_{i}_chunk_000") for i in range(3)])
    data = svc.search(RagSearchRequest(query="별 첨성대"))
    assert data.result_count == 3
    assert data.embedding_model == "fake-model"
    assert data.top_k == 5
    assert [r.chunk_id for r in data.results] == ["c_0_chunk_000", "c_1_chunk_000", "c_2_chunk_000"]
    assert all(r.distance < r2.distance for r, r2 in zip(data.results, data.results[1:]))


def test_query_empty_raises_at_schema_level():
    with pytest.raises(ValueError):
        RagSearchRequest(query="   ")


def test_top_k_max_enforced_at_schema_level():
    # Schema cap is settings.RAG_SEARCH_MAX_TOP_K; test the schema validator.
    with pytest.raises(ValueError, match="넘을 수 없습니다"):
        RagSearchRequest(query="x", top_k=10_000)


def test_include_raw_text_false_omits_raw():
    svc = _service([_chunk("c_chunk_000")])
    data = svc.search(RagSearchRequest(query="x"))
    assert data.results[0].raw_text is None
    assert data.results[0].context_text is not None


def test_include_context_text_false_omits_context():
    svc = _service([_chunk("c_chunk_000")])
    data = svc.search(
        RagSearchRequest(query="x", include_context_text=False, include_raw_text=True)
    )
    assert data.results[0].context_text is None
    assert data.results[0].raw_text == "raw"


def test_filters_propagated_to_repository():
    svc = _service([
        _chunk("c1_chunk_000", role="fact_context", factuality="history"),
        _chunk("c2_chunk_000", role="legend_material", factuality="mixed"),
        _chunk("c3_chunk_000", role="symbolic_material", factuality="symbolic"),
    ])
    data = svc.search(
        RagSearchRequest(
            query="x",
            content_roles=["legend_material", "symbolic_material"],
            factuality_levels=["mixed", "symbolic"],
        )
    )
    assert {r.content_role for r in data.results} == {"legend_material", "symbolic_material"}
    assert data.filters["content_roles"] == ["legend_material", "symbolic_material"]
    assert data.filters["factuality_levels"] == ["mixed", "symbolic"]


def test_similarity_is_one_minus_distance():
    svc = _service([_chunk("c_chunk_000")])
    data = svc.search(RagSearchRequest(query="x"))
    item = data.results[0]
    assert pytest.approx(item.similarity, abs=1e-6) == 1.0 - item.distance


def test_search_by_heritage_pins_filter_and_falls_back_to_heritage_query():
    chunks = [
        _chunk("a_chunk_000", heritage="첨성대"),
        _chunk("b_chunk_000", heritage="석가탑"),
    ]
    svc = _service(chunks)
    data = svc.search_by_heritage(RagSearchByHeritageRequest(heritage_name="첨성대"))
    assert {r.heritage_name for r in data.results} == {"첨성대"}
    assert data.query == "첨성대"


def test_distance_threshold_filters_out_far_results():
    svc = _service([_chunk(f"c_{i}_chunk_000") for i in range(5)])
    # synthetic distances are 0.10, 0.15, 0.20, 0.25, 0.30 → threshold 0.18 keeps 2
    data = svc.search(RagSearchRequest(query="x", distance_threshold=0.18))
    assert data.result_count == 2
