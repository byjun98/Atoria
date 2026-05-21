"""
Store rag_chunks_ready.jsonl into PostgreSQL via RagChunkStorageService.

No HTTP / OpenAI calls. Run after `scripts/ingest_rag_documents.py` and after
`alembic upgrade head` has applied the 147 migration.

    python -m scripts.store_rag_chunks
    python -m scripts.store_rag_chunks --input-file ai/data/processed/rag_chunks_ready.jsonl
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
from app.services.storage import RagChunkStorageService, RagStorageResult  # noqa: E402


DEFAULT_INPUT = ROOT / "data" / "processed" / "rag_chunks_ready.jsonl"
DEFAULT_REPORT = ROOT / "data" / "processed" / "rag_db_storage_report.json"


def _mask_db_url(url: str) -> str:
    return re.sub(r"://([^:/@]+):([^@]+)@", r"://\1:***@", url)


def _print_report(
    result: RagStorageResult,
    input_file: Path,
    report_file: Path,
) -> None:
    print("RAG chunk DB storage completed.\n")
    print("Input:")
    print(f"- {input_file}")
    print("\nDatabase:")
    print(f"- {_mask_db_url(settings.DATABASE_URL)}")
    print(f"- schema: {settings.RAG_DB_SCHEMA}")

    print("\nDocuments:")
    print(f"- inserted_documents: {result.inserted_documents}")
    print(f"- updated_documents: {result.updated_documents}")

    print("\nChunks:")
    print(f"- total_chunks: {result.total_chunks}")
    print(f"- inserted_chunks: {result.inserted_chunks}")
    print(f"- updated_chunks: {result.updated_chunks}")
    print(f"- failed_chunks: {result.failed_chunks}")

    def _section(title: str, mapping: dict[str, int]) -> None:
        if not mapping:
            return
        print(f"\nBy {title}:")
        for k, v in sorted(mapping.items()):
            print(f"- {k}: {v}")

    _section("embedding_status", result.by_embedding_status)
    _section("content_role", result.by_content_role)
    _section("factuality_level", result.by_factuality_level)

    print("\nOutput:")
    print(f"- {report_file}")


def run(
    input_file: Path = DEFAULT_INPUT,
    report_file: Path = DEFAULT_REPORT,
    fail_on_error: bool = False,
) -> RagStorageResult:
    if not input_file.exists():
        raise FileNotFoundError(f"input JSONL not found: {input_file}")

    session = SessionLocal()
    try:
        repo = RagChunkRepository(session)
        service = RagChunkStorageService(repo)
        result = service.store_ready_chunks_from_jsonl(input_file)
        if result.failed_chunks == 0:
            session.commit()
        else:
            # Persist what succeeded; failures are reported but do not block.
            session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()

    report_file.parent.mkdir(parents=True, exist_ok=True)
    report = {
        "status": "COMPLETED" if result.failed_chunks == 0 else "COMPLETED_WITH_FAILURES",
        "input_file": str(input_file),
        "database_url": _mask_db_url(settings.DATABASE_URL),
        "schema": settings.RAG_DB_SCHEMA,
        "total_chunks": result.total_chunks,
        "inserted_documents": result.inserted_documents,
        "updated_documents": result.updated_documents,
        "inserted_chunks": result.inserted_chunks,
        "updated_chunks": result.updated_chunks,
        "failed_chunks": result.failed_chunks,
        "by_embedding_status": result.by_embedding_status,
        "by_content_role": result.by_content_role,
        "by_factuality_level": result.by_factuality_level,
        "by_heritage_name": result.by_heritage_name,
        "failures": result.failures,
    }
    with report_file.open("w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    _print_report(result, input_file, report_file)

    if fail_on_error and result.failed_chunks > 0:
        raise SystemExit(1)
    return result


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Store RAG chunks into PostgreSQL (147)")
    p.add_argument("--input-file", type=Path, default=DEFAULT_INPUT)
    p.add_argument("--report-file", type=Path, default=DEFAULT_REPORT)
    p.add_argument("--fail-on-error", action="store_true")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = _parse_args(argv)
    run(input_file=args.input_file, report_file=args.report_file, fail_on_error=args.fail_on_error)


if __name__ == "__main__":
    main()
