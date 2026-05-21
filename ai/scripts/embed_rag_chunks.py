"""
Embed READY_FOR_EMBEDDING chunks using OpenAI Embeddings.

Usage:
    python -m scripts.embed_rag_chunks                       # full run
    python -m scripts.embed_rag_chunks --dry-run --limit 25  # no API call
    docker compose -p atoria exec ai python -m scripts.embed_rag_chunks \\
        --limit 100 --batch-size 50 \\
        --report-file data/processed/rag_embedding_report.json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.core.config import settings  # noqa: E402
from app.db.session import SessionLocal  # noqa: E402
from app.repositories.rag_chunk_repository import RagChunkRepository  # noqa: E402


DEFAULT_REPORT = ROOT / "data" / "processed" / "rag_embedding_report.json"


def _mask_db_url(url: str) -> str:
    return re.sub(r"://([^:/@]+):([^@]+)@", r"://\1:***@", url)


def _print_report(report: dict, report_file: Path) -> None:
    print("RAG embedding completed.\n")
    print("Database:")
    print(f"- {report['database_url']}")
    print(f"- schema: {report['schema']}")

    print("\nEmbedding:")
    print(f"- model: {report['embedding_model']}")
    print(f"- dimension: {report['embedding_dim']}")
    print(f"- total_candidates: {report['total_candidates']}")
    print(f"- embedded_count: {report['embedded_count']}")
    print(f"- failed_count: {report['failed_count']}")
    print(f"- skipped_count: {report['skipped_count']}")

    def _section(title: str, mapping: dict[str, int]) -> None:
        if not mapping:
            return
        print(f"\nBy {title}:")
        for k, v in sorted(mapping.items()):
            print(f"- {k}: {v}")

    _section("content_role", report["by_content_role"])
    _section("factuality_level", report["by_factuality_level"])

    print("\nOutput:")
    print(f"- {report_file}")


def _dry_run(limit: int, report_file: Path) -> dict:
    session = SessionLocal()
    try:
        repo = RagChunkRepository(session)
        candidates = repo.list_chunks_ready_for_embedding(limit=limit)
        chunk_ids = [c.chunk_id for c in candidates]
    finally:
        session.close()
    print(f"[dry-run] READY_FOR_EMBEDDING candidates: {len(chunk_ids)}")
    for cid in chunk_ids:
        print(f"  - {cid}")
    report = {
        "status": "DRY_RUN",
        "embedding_model": settings.OPENAI_EMBEDDING_MODEL,
        "embedding_dim": settings.RAG_EMBEDDING_DIM,
        "total_candidates": len(chunk_ids),
        "embedded_count": 0,
        "failed_count": 0,
        "skipped_count": 0,
        "database_url": _mask_db_url(settings.DATABASE_URL),
        "schema": settings.RAG_DB_SCHEMA,
        "by_content_role": {},
        "by_factuality_level": {},
        "by_heritage_name": {},
        "failures": [],
        "embedded_chunk_ids": [],
        "candidate_chunk_ids": chunk_ids,
    }
    report_file.parent.mkdir(parents=True, exist_ok=True)
    with report_file.open("w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\nWrote dry-run report → {report_file}")
    return report


def run(
    limit: int = None,
    batch_size: int = None,
    report_file: Path = DEFAULT_REPORT,
    dry_run: bool = False,
    fail_on_error: bool = False,
) -> dict:
    limit = limit if limit is not None else settings.RAG_EMBEDDING_LIMIT
    batch_size = batch_size if batch_size is not None else settings.RAG_EMBEDDING_BATCH_SIZE

    if dry_run:
        return _dry_run(limit, report_file)

    if not settings.OPENAI_API_KEY:
        raise RuntimeError(
            "OPENAI_API_KEY is not set. Use --dry-run to preview without API calls, "
            "or export OPENAI_API_KEY before running."
        )

    # Late imports keep dry-run usable without the openai package wired up.
    from app.clients.openai_embedding_client import OpenAIEmbeddingClient
    from app.services.embedding import RagEmbeddingService

    client = OpenAIEmbeddingClient()
    session = SessionLocal()
    try:
        repo = RagChunkRepository(session)
        service = RagEmbeddingService(
            repository=repo, embedding_client=client, batch_size=batch_size
        )
        result = service.embed_ready_chunks(limit=limit)
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()

    status = "COMPLETED" if result.failed_count == 0 else "COMPLETED_WITH_FAILURES"
    report = {
        "status": status,
        "embedding_model": result.embedding_model,
        "embedding_dim": result.embedding_dim,
        "total_candidates": result.total_candidates,
        "embedded_count": result.embedded_count,
        "failed_count": result.failed_count,
        "skipped_count": result.skipped_count,
        "database_url": _mask_db_url(settings.DATABASE_URL),
        "schema": settings.RAG_DB_SCHEMA,
        "by_content_role": result.by_content_role,
        "by_factuality_level": result.by_factuality_level,
        "by_heritage_name": result.by_heritage_name,
        "failures": result.failures,
        "embedded_chunk_ids": result.embedded_chunk_ids,
    }
    report_file.parent.mkdir(parents=True, exist_ok=True)
    with report_file.open("w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    _print_report(report, report_file)

    if fail_on_error and result.failed_count > 0:
        raise SystemExit(1)
    return report


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Embed READY_FOR_EMBEDDING rag_chunks (148)")
    p.add_argument("--limit", type=int, default=None)
    p.add_argument("--batch-size", type=int, default=None)
    p.add_argument("--report-file", type=Path, default=DEFAULT_REPORT)
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--fail-on-error", action="store_true")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = _parse_args(argv)
    run(
        limit=args.limit,
        batch_size=args.batch_size,
        report_file=args.report_file,
        dry_run=args.dry_run,
        fail_on_error=args.fail_on_error,
    )


if __name__ == "__main__":
    main()
