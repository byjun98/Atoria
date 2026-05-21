"""
Internal RAG search smoke test (no FastAPI server needed).

    python -m scripts.test_rag_search --query "첨성대 선덕여왕 별 미션" --top-k 5
    docker compose -p atoria exec ai python -m scripts.test_rag_search \\
        --query "첨성대 선덕여왕 별 미션" --top-k 5 --content-roles fact_context legend_material
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.clients.openai_embedding_client import OpenAIEmbeddingClient  # noqa: E402
from app.core.config import settings  # noqa: E402
from app.db.session import SessionLocal  # noqa: E402
from app.repositories.rag_search_repository import RagSearchRepository  # noqa: E402
from app.schemas.rag_search_schema import RagSearchRequest  # noqa: E402
from app.services.rag import RagSearchService  # noqa: E402


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Smoke test RAG search (149)")
    p.add_argument("--query", required=True)
    p.add_argument("--top-k", type=int, default=settings.RAG_SEARCH_DEFAULT_TOP_K)
    p.add_argument("--heritage-names", nargs="*", default=[])
    p.add_argument("--content-roles", nargs="*", default=[])
    p.add_argument("--factuality-levels", nargs="*", default=[])
    p.add_argument("--source-types", nargs="*", default=[])
    p.add_argument("--distance-threshold", type=float, default=None)
    p.add_argument("--include-raw-text", action="store_true")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = _parse_args(argv)

    if not settings.OPENAI_API_KEY:
        raise RuntimeError("OPENAI_API_KEY is not set.")

    request = RagSearchRequest(
        query=args.query,
        top_k=args.top_k,
        heritage_names=args.heritage_names,
        content_roles=args.content_roles,
        factuality_levels=args.factuality_levels,
        source_types=args.source_types,
        include_context_text=True,
        include_raw_text=args.include_raw_text,
        distance_threshold=args.distance_threshold,
    )

    session = SessionLocal()
    try:
        repo = RagSearchRepository(session)
        client = OpenAIEmbeddingClient()
        service = RagSearchService(repository=repo, embedding_client=client)
        data = service.search(request)
    finally:
        session.close()

    print(f"Query: {data.query}")
    print(f"Top K: {data.top_k}  | model: {data.embedding_model}  | results: {data.result_count}")
    if data.filters:
        print(f"Filters: {data.filters}")
    print()
    for i, item in enumerate(data.results, 1):
        print(f"{i}. {item.chunk_id}")
        print(f"   heritage: {item.heritage_name}")
        print(f"   role: {item.content_role}  | factuality: {item.factuality_level}")
        print(f"   distance: {item.distance:.3f}  | similarity: {item.similarity:.3f}")
        if item.title:
            print(f"   title: {item.title}")
        if item.source_urls:
            print(f"   source: {item.source_urls[0]}")
        print()


if __name__ == "__main__":
    main()
