"""
RagSearchService — query → embedding → pgvector search → DTO.

Designed to be reusable from both the FastAPI router and from internal
story-generation code (151/152) without going through HTTP.
"""
from __future__ import annotations

from app.clients.openai_embedding_client import OpenAIEmbeddingClient
from app.core.config import settings
from app.db.models.rag_chunk import RagChunk
from app.repositories.rag_search_repository import RagSearchRepository
from app.schemas.rag_search_schema import (
    RagSearchByHeritageRequest,
    RagSearchData,
    RagSearchRequest,
    RagSearchResultItem,
)


class RagSearchService:
    def __init__(
        self,
        repository: RagSearchRepository,
        embedding_client: OpenAIEmbeddingClient,
        default_top_k: int | None = None,
        max_top_k: int | None = None,
    ) -> None:
        self.repository = repository
        self.client = embedding_client
        self.default_top_k = default_top_k or settings.RAG_SEARCH_DEFAULT_TOP_K
        self.max_top_k = max_top_k or settings.RAG_SEARCH_MAX_TOP_K

    # ---- public --------------------------------------------------------

    def search(self, request: RagSearchRequest) -> RagSearchData:
        top_k = self._normalize_top_k(request.top_k)
        query_vector = self.client.embed_text(request.query)

        rows = self.repository.search_similar_chunks(
            query_embedding=query_vector,
            top_k=top_k,
            heritage_names=request.heritage_names or None,
            content_roles=request.content_roles or None,
            factuality_levels=request.factuality_levels or None,
            source_types=request.source_types or None,
            distance_threshold=request.distance_threshold,
        )

        results = [
            self._to_result_item(
                chunk,
                distance,
                include_context_text=request.include_context_text,
                include_raw_text=request.include_raw_text,
            )
            for chunk, distance in rows
        ]

        return RagSearchData(
            query=request.query,
            embedding_model=self.client.model,
            top_k=top_k,
            result_count=len(results),
            results=results,
            filters=self._dump_filters(request),
        )

    def metadata_search(self, request: RagSearchRequest) -> RagSearchData:
        """Run a metadata-only search (no embedding API call).

        Same response shape as :meth:`search`; ``embedding_model`` is set to
        ``"(metadata)"`` and every result has ``distance=0.0``.
        """
        top_k = self._normalize_top_k(request.top_k)
        chunks = self.repository.search_by_metadata(
            top_k=top_k,
            heritage_names=request.heritage_names or None,
            content_roles=request.content_roles or None,
            factuality_levels=request.factuality_levels or None,
            source_types=request.source_types or None,
        )
        results = [
            self._to_result_item(
                chunk,
                0.0,
                include_context_text=request.include_context_text,
                include_raw_text=request.include_raw_text,
            )
            for chunk in chunks
        ]
        return RagSearchData(
            query=request.query,
            embedding_model="(metadata)",
            top_k=top_k,
            result_count=len(results),
            results=results,
            filters=self._dump_filters(request),
        )

    def vector_search_batch(
        self, requests: list[RagSearchRequest]
    ) -> list[RagSearchData]:
        """Embed all queries in ONE API call, then run per-request DB queries.

        Order of returned RagSearchData matches the input ``requests`` list.
        Empty input returns an empty list and triggers no embedding call.
        """
        if not requests:
            return []
        texts = [r.query for r in requests]
        vectors = self.client.embed_texts(texts)
        out: list[RagSearchData] = []
        for req, vec in zip(requests, vectors):
            top_k = self._normalize_top_k(req.top_k)
            rows = self.repository.search_similar_chunks(
                query_embedding=vec,
                top_k=top_k,
                heritage_names=req.heritage_names or None,
                content_roles=req.content_roles or None,
                factuality_levels=req.factuality_levels or None,
                source_types=req.source_types or None,
                distance_threshold=req.distance_threshold,
            )
            results = [
                self._to_result_item(
                    chunk,
                    distance,
                    include_context_text=req.include_context_text,
                    include_raw_text=req.include_raw_text,
                )
                for chunk, distance in rows
            ]
            out.append(
                RagSearchData(
                    query=req.query,
                    embedding_model=self.client.model,
                    top_k=top_k,
                    result_count=len(results),
                    results=results,
                    filters=self._dump_filters(req),
                )
            )
        return out

    def search_by_heritage(self, request: RagSearchByHeritageRequest) -> RagSearchData:
        # Reuse the main search path with heritage_name pinned as the only filter.
        proxy = RagSearchRequest(
            query=request.query or request.heritage_name,
            top_k=request.top_k,
            heritage_names=[request.heritage_name],
            content_roles=request.content_roles,
            factuality_levels=request.factuality_levels,
            include_context_text=request.include_context_text,
            include_raw_text=request.include_raw_text,
            distance_threshold=request.distance_threshold,
        )
        return self.search(proxy)

    # ---- helpers -------------------------------------------------------

    def _normalize_top_k(self, top_k: int | None) -> int:
        if top_k is None:
            return self.default_top_k
        if top_k < 1:
            raise ValueError("top_k는 1 이상이어야 합니다.")
        if top_k > self.max_top_k:
            raise ValueError(f"top_k는 {self.max_top_k} 를 넘을 수 없습니다.")
        return top_k

    @staticmethod
    def _to_result_item(
        chunk: RagChunk,
        distance: float,
        *,
        include_context_text: bool,
        include_raw_text: bool,
    ) -> RagSearchResultItem:
        # cosine distance → similarity (참고값). pgvector cosine distance 범위는
        # 0..2 가 가능하지만 정상 임베딩에서는 0..1 부근에 분포하므로 1-d 로 둔다.
        similarity = 1.0 - distance
        return RagSearchResultItem(
            chunk_id=chunk.chunk_id,
            source_record_id=chunk.source_record_id,
            heritage_name=chunk.heritage_name,
            title=chunk.title,
            content_role=chunk.content_role,
            source_type=chunk.source_type,
            factuality_level=chunk.factuality_level,
            narrative_type=chunk.narrative_type,
            folklore_status=chunk.folklore_status,
            source_site=chunk.source_site,
            source_urls=list(chunk.source_urls or []),
            distance=distance,
            similarity=similarity,
            raw_text=chunk.raw_text if include_raw_text else None,
            context_text=chunk.context_text if include_context_text else None,
            mission_hooks=list(chunk.mission_hooks or []),
            related_heritages=list(chunk.related_heritages or []),
            related_people=list(chunk.related_people or []),
            related_places=list(chunk.related_places or []),
            motifs=list(chunk.motifs or []),
            tone_tags=list(chunk.tone_tags or []),
            metadata=dict(chunk.extra_metadata or {}),
        )

    @staticmethod
    def _dump_filters(request: RagSearchRequest) -> dict:
        out: dict = {}
        if request.heritage_names:
            out["heritage_names"] = list(request.heritage_names)
        if request.content_roles:
            out["content_roles"] = list(request.content_roles)
        if request.factuality_levels:
            out["factuality_levels"] = list(request.factuality_levels)
        if request.source_types:
            out["source_types"] = list(request.source_types)
        if request.distance_threshold is not None:
            out["distance_threshold"] = request.distance_threshold
        return out
