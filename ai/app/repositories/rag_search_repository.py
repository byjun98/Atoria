"""
pgvector similarity search over rag_chunks.

Returns ``[(RagChunk, distance), ...]`` ordered by cosine distance ascending.
Filters by embedding_status='EMBEDDED' and the optional metadata filters.
"""
from __future__ import annotations

from typing import Sequence

from sqlalchemy import case, select
from sqlalchemy.orm import Session

from app.db.models.rag_chunk import RagChunk


def _heritage_name_or_alias(names: list[str]):
    """Match by `heritage_name` exact match only.

    related_heritages / related_places containment is intentionally skipped
    so that the search relies solely on the heritage_name sent by the backend.
    """
    return RagChunk.heritage_name.in_(list(names))


class RagSearchRepository:
    def __init__(self, session: Session) -> None:
        self.session = session

    def search_similar_chunks(
        self,
        query_embedding: Sequence[float],
        top_k: int = 5,
        heritage_names: list[str] | None = None,
        content_roles: list[str] | None = None,
        factuality_levels: list[str] | None = None,
        source_types: list[str] | None = None,
        distance_threshold: float | None = None,
    ) -> list[tuple[RagChunk, float]]:
        if top_k <= 0:
            return []

        distance_expr = RagChunk.embedding.cosine_distance(query_embedding).label("distance")

        stmt = (
            select(RagChunk, distance_expr)
            .where(RagChunk.embedding_status == "EMBEDDED")
            .where(RagChunk.embedding.isnot(None))
        )
        if heritage_names:
            stmt = stmt.where(_heritage_name_or_alias(list(heritage_names)))
        if content_roles:
            stmt = stmt.where(RagChunk.content_role.in_(list(content_roles)))
        if factuality_levels:
            stmt = stmt.where(RagChunk.factuality_level.in_(list(factuality_levels)))
        if source_types:
            stmt = stmt.where(RagChunk.source_type.in_(list(source_types)))
        if distance_threshold is not None:
            stmt = stmt.where(distance_expr <= distance_threshold)

        stmt = stmt.order_by(distance_expr.asc()).limit(top_k)

        rows = self.session.execute(stmt).all()
        return [(chunk, float(dist)) for chunk, dist in rows]

    def search_by_metadata(
        self,
        *,
        top_k: int,
        heritage_names: list[str] | None = None,
        content_roles: list[str] | None = None,
        factuality_levels: list[str] | None = None,
        source_types: list[str] | None = None,
    ) -> list[RagChunk]:
        """Pure metadata filter — no embedding, no vector distance.

        Used by the story-intro flow to avoid OpenAI embedding calls when the
        request already pins a place by name. Order is deterministic by id so
        repeated calls return the same chunks.
        """
        if top_k <= 0:
            return []
        stmt = (
            select(RagChunk)
            .where(RagChunk.embedding_status == "EMBEDDED")
        )
        if heritage_names:
            stmt = stmt.where(_heritage_name_or_alias(list(heritage_names)))
        if content_roles:
            stmt = stmt.where(RagChunk.content_role.in_(list(content_roles)))
        if factuality_levels:
            stmt = stmt.where(RagChunk.factuality_level.in_(list(factuality_levels)))
        if source_types:
            stmt = stmt.where(RagChunk.source_type.in_(list(source_types)))

        # Priority: symbolic/legend > fact > mission_hook > others, then by id.
        role_priority = case(
            (RagChunk.content_role == "symbolic_material", 1),
            (RagChunk.content_role == "legend_material", 1),
            (RagChunk.content_role == "story_material", 1),
            (RagChunk.content_role == "fact_context", 2),
            (RagChunk.content_role == "factual_context", 2),
            (RagChunk.content_role == "heritage_fact", 2),
            (RagChunk.content_role == "mission_hook", 3),
            else_=4,
        )
        stmt = stmt.order_by(role_priority.asc(), RagChunk.id.asc()).limit(top_k)
        return list(self.session.execute(stmt).scalars())
