"""Tests for StoryIntroService — fakes only, no DB / OpenAI."""
from __future__ import annotations

import json

import pytest

from app.schemas.rag_search_schema import RagSearchData, RagSearchResultItem
from app.schemas.story_schema import (
    CoursePlaceInput,
    StoryGenerationOptions,
    StoryIntroGenerationRequest,
    StoryUserProfile,
)
from app.services.story.story_intro_service import StoryIntroService


# --- fakes --------------------------------------------------------------


def _result_item(*, chunk_id, content_role, heritage="첨성대",
                 factuality="history", urls=None, hooks=None):
    return RagSearchResultItem(
        chunk_id=chunk_id,
        source_record_id=chunk_id.rsplit("_chunk_", 1)[0],
        heritage_name=heritage,
        title=f"t-{chunk_id}",
        content_role=content_role,
        source_type="x",
        factuality_level=factuality,
        source_site="우리역사넷",
        source_urls=urls or [],
        distance=0.1,
        similarity=0.9,
        context_text=f"[h]\n{chunk_id} body",
        mission_hooks=hooks or [],
    )


class FakeRagSearchService:
    def __init__(self, by_query_role: dict | None = None,
                 raise_for_query: str | None = None,
                 metadata_empty: bool = False):
        self._map = by_query_role or {}
        self._raise_for = raise_for_query
        self._metadata_empty = metadata_empty
        self.calls: list[dict] = []
        self.metadata_calls: list[dict] = []
        self.batch_calls: list[list[dict]] = []

    def _lookup(self, request):
        if self._raise_for and self._raise_for in (request.heritage_names or []):
            raise RuntimeError("RAG down")
        if self._raise_for and self._raise_for in request.query:
            raise RuntimeError("RAG down")
        key = (tuple(sorted(request.content_roles)), bool(request.heritage_names))
        results = self._map.get(key, [])
        return RagSearchData(
            query=request.query,
            embedding_model="fake-model",
            top_k=request.top_k or 5,
            result_count=len(results),
            results=results,
            filters={},
        )

    def search(self, request):
        self.calls.append({
            "query": request.query,
            "content_roles": list(request.content_roles),
            "heritage_names": list(request.heritage_names),
        })
        return self._lookup(request)

    def metadata_search(self, request):
        self.metadata_calls.append({
            "query": request.query,
            "content_roles": list(request.content_roles),
            "heritage_names": list(request.heritage_names),
        })
        if self._raise_for and (
            self._raise_for in (request.heritage_names or [])
            or self._raise_for in request.query
        ):
            raise RuntimeError("RAG down")
        if self._metadata_empty:
            return RagSearchData(
                query=request.query, embedding_model="(metadata)",
                top_k=request.top_k or 5, result_count=0, results=[], filters={},
            )
        # Reuse the same lookup table as `search`.
        key = (tuple(sorted(request.content_roles)), bool(request.heritage_names))
        results = self._map.get(key, [])
        return RagSearchData(
            query=request.query, embedding_model="(metadata)",
            top_k=request.top_k or 5, result_count=len(results),
            results=results, filters={},
        )

    def vector_search_batch(self, requests):
        self.batch_calls.append([
            {"query": r.query, "content_roles": list(r.content_roles),
             "heritage_names": list(r.heritage_names)}
            for r in requests
        ])
        return [self._lookup(r) for r in requests]


class FakeChatClient:
    def __init__(self, response: str | None = None, raise_exc: Exception | None = None):
        self.response = response
        self.raise_exc = raise_exc
        self.last_messages: list[dict] | None = None

    def create_json_chat_completion(self, messages, temperature=None):
        self.last_messages = messages
        if self.raise_exc:
            raise self.raise_exc
        return self.response


# --- helpers ------------------------------------------------------------


def _request(places=None):
    return StoryIntroGenerationRequest(
        user_profile=StoryUserProfile(persona="curious", language="ko"),
        places=places or [
            CoursePlaceInput(place_id="1", place_name="첨성대", sequence=1),
            CoursePlaceInput(place_id="2", place_name="동궁과월지", sequence=2),
        ],
        options=StoryGenerationOptions(use_rag=True),
    )


def _draft_json(place_count=2):
    places = []
    for i in range(1, place_count + 1):
        name = "첨성대" if i == 1 else "동궁과월지"
        places.append({
            "sequence": i,
            "place_id": str(i),
            "place_name": name,
            "story_fragment": (
                f"{name} 앞에서 작은 단서를 찾는 장면 {i}입니다. "
                f"돌의 방향과 주변 모양이 다음 길을 알려 주는 것 같아요."
            ),
            "mission": {
                "mission_title": f"{name} 단서 찾기",
                "mission_instruction": (
                    f"{name}에서 돌의 모양과 방향을 찾아요. 두 단서가 한 화면에 "
                    "보이면 클리어이고, 사진은 다음 길의 단서가 됩니다."
                ),
                "mission_type": "observation",
                "verification_hint": f"{name}의 돌 모양과 방향이 한 화면에 보이면 클리어입니다.",
                "related_place_name": "" if i == 1 else "동궁과월지",  # blank → backfill
                "related_chunk_ids": [],
                "mission_keywords": ["돌", "방향"],
            },
            "used_chunk_ids": [],
            "source_urls": [],
        })
    return json.dumps({
        "title": "오늘의 경주",
        "intro": "이야기가 시작됩니다.",
        "places": places,
        "outro": "여정을 마칩니다.",
        "used_chunk_ids": [],
        "source_urls": [],
        "warnings": [],
    }, ensure_ascii=False)


# --- tests --------------------------------------------------------------


def test_full_path_returns_draft_with_places():
    rag_results = {
        (("fact_context",), True): [
            _result_item(chunk_id="f_001_chunk_000", content_role="fact_context")
        ],
        (("legend_material", "symbolic_material"), True): [
            _result_item(chunk_id="l_001_chunk_000", content_role="legend_material",
                         factuality="mixed"),
            _result_item(chunk_id="s_001_chunk_000", content_role="symbolic_material",
                         factuality="symbolic"),
        ],
    }
    rag = FakeRagSearchService(by_query_role=rag_results)
    chat = FakeChatClient(response=_draft_json())
    service = StoryIntroService(rag_search_service=rag, chat_client=chat)

    draft = service.generate_intro(_request())
    assert draft.title and draft.intro and draft.outro
    assert [p.sequence for p in draft.places] == [1, 2]
    # blank related_place_name on place 1 was backfilled
    assert draft.places[0].mission.related_place_name == "첨성대"


def test_rag_failure_does_not_abort_and_emits_warning():
    rag = FakeRagSearchService(raise_for_query="첨성대")
    chat = FakeChatClient(response=_draft_json())
    service = StoryIntroService(rag_search_service=rag, chat_client=chat)

    draft = service.generate_intro(_request())
    assert any("RAG 검색 실패" in w for w in draft.warnings)


def test_rag_called_per_place_with_correct_filters():
    """Metadata-first: each place fires metadata_search for fact + story roles.
    Empty results then trigger ONE batched vector fallback for all places."""
    rag = FakeRagSearchService()
    chat = FakeChatClient(response=_draft_json())
    StoryIntroService(rag_search_service=rag, chat_client=chat).generate_intro(_request())
    fact_md = [c for c in rag.metadata_calls if c["content_roles"] == ["fact_context"]]
    story_md = [
        c for c in rag.metadata_calls
        if c["content_roles"] == ["legend_material", "symbolic_material"]
    ]
    # 2 places × 2 role families = 4 metadata calls, 0 plain `search` calls.
    assert len(fact_md) == 2
    assert len(story_md) == 2
    assert rag.calls == []
    # Empty metadata → exactly ONE batched vector fallback request.
    assert len(rag.batch_calls) == 1
    # 2 places × 2 fallback queries (fact + story) = 4 entries in the batch.
    assert len(rag.batch_calls[0]) == 4


def test_metadata_first_skips_embedding_when_results_sufficient():
    """When metadata search returns enough chunks, no vector fallback runs."""
    rag_results = {
        (("fact_context",), True): [
            _result_item(chunk_id="f_001_chunk_000", content_role="fact_context")
        ],
        (("legend_material", "symbolic_material"), True): [
            _result_item(chunk_id="l_001_chunk_000", content_role="legend_material",
                         factuality="mixed"),
        ],
    }
    rag = FakeRagSearchService(by_query_role=rag_results)
    chat = FakeChatClient(response=_draft_json())
    StoryIntroService(rag_search_service=rag, chat_client=chat).generate_intro(_request())
    # Metadata satisfied every place → no batched vector call at all.
    assert rag.batch_calls == []
    assert rag.calls == []
    # 2 places × 2 role families.
    assert len(rag.metadata_calls) == 4


def test_emits_per_place_log_events(caplog):
    """metadata 충분한 place는 rag_metadata_search_used,
    부족한 place는 rag_vector_fallback_triggered 가 emit 되어야 한다."""
    import logging
    rag_results = {
        (("fact_context",), True): [
            _result_item(chunk_id="f_001_chunk_000", content_role="fact_context")
        ],
        (("legend_material", "symbolic_material"), True): [
            _result_item(chunk_id="l_001_chunk_000", content_role="legend_material",
                         factuality="mixed"),
        ],
    }
    rag = FakeRagSearchService(by_query_role=rag_results)
    chat = FakeChatClient(response=_draft_json())
    with caplog.at_level(logging.INFO):
        StoryIntroService(rag_search_service=rag, chat_client=chat).generate_intro(_request())
    msgs = " ".join(r.getMessage() for r in caplog.records)
    assert "rag_metadata_search_used" in msgs
    assert "story_intro_generated" in msgs
    assert "vector_search_skipped" in msgs


def test_vector_fallback_batches_into_single_call():
    """When metadata is empty, all per-place fallback queries collapse into
    a single batched embedding call."""
    rag = FakeRagSearchService(metadata_empty=True)
    chat = FakeChatClient(response=_draft_json())
    StoryIntroService(rag_search_service=rag, chat_client=chat).generate_intro(_request())
    assert len(rag.batch_calls) == 1
    # one batch covering all (place × query_type) combinations
    assert len(rag.batch_calls[0]) >= 2


def test_chat_failure_propagates():
    rag = FakeRagSearchService()
    chat = FakeChatClient(raise_exc=RuntimeError("openai down"))
    service = StoryIntroService(rag_search_service=rag, chat_client=chat)
    with pytest.raises(RuntimeError, match="openai down"):
        service.generate_intro(_request())


def test_places_count_mismatch_now_raises_quality_failure():
    """Since 154, place-count mismatch is a hard quality error.
    Even after retry it surfaces as StoryQualityValidationFailed."""
    from app.services.story.story_intro_service import StoryQualityValidationFailed

    rag = FakeRagSearchService()
    chat = FakeChatClient(response=_draft_json(place_count=1))  # request asks 2
    service = StoryIntroService(rag_search_service=rag, chat_client=chat)
    with pytest.raises(StoryQualityValidationFailed) as ei:
        service.generate_intro(_request())
    assert any(i.code == "PLACE_COUNT_MISMATCH" for i in ei.value.report.issues)


def test_use_rag_false_skips_rag_calls():
    rag = FakeRagSearchService()
    chat = FakeChatClient(response=_draft_json())
    service = StoryIntroService(rag_search_service=rag, chat_client=chat)
    req = _request()
    req = req.model_copy(update={"options": StoryGenerationOptions(use_rag=False)})
    service.generate_intro(req)
    assert rag.calls == []


def test_unsorted_places_ordered_before_prompt_build():
    chat = FakeChatClient(response=_draft_json())
    rag = FakeRagSearchService()
    service = StoryIntroService(rag_search_service=rag, chat_client=chat)
    req = _request(places=[
        CoursePlaceInput(place_name="동궁과월지", sequence=2, place_id="2"),
        CoursePlaceInput(place_name="첨성대", sequence=1, place_id="1"),
    ])
    service.generate_intro(req)
    user_msg = chat.last_messages[1]["content"]
    assert user_msg.index("첨성대") < user_msg.index("동궁과월지")
