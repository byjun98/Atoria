"""
Internal RAG ingestion script (B-mode).

Reads heritage_context + heritage_legend_materials from local files, runs the
144 chunking pipeline, and writes READY_FOR_EMBEDDING chunks + report +
failures into ai/data/processed/. No HTTP / DB / OpenAI calls.

Run:
    python -m scripts.ingest_rag_documents
or with overrides:
    python -m scripts.ingest_rag_documents \
        --legend-file ai/data/seed/heritage_legend_materials.json \
        --context-path ai/data/enriched/heritage_context \
        --output-dir ai/data/processed
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.services.ingestion import (  # noqa: E402
    RagIngestionResult,
    RagIngestionService,
    write_json,
    write_jsonl,
)


DEFAULT_LEGEND_FILE = ROOT / "data" / "seed" / "heritage_legend_materials.json"
DEFAULT_OUTPUT_DIR = ROOT / "data" / "processed"

# Candidate locations for heritage_context inputs (first match wins per slot).
DEFAULT_CONTEXT_CANDIDATES = [
    ROOT / "data" / "enriched" / "heritage_context",
    ROOT / "data" / "processed" / "heritage_contexts.json",
    ROOT / "data" / "processed" / "heritage_context.json",
    ROOT / "data" / "processed" / "heritage_context",
    ROOT / "data" / "raw" / "heritage_contexts.json",
    ROOT / "data" / "raw" / "heritage_context",
    ROOT / "data" / "seed" / "heritage_contexts.json",
]


def _resolve_input_files(
    legend_file: Path,
    context_path: Path | None,
) -> tuple[list[Path], list[str]]:
    """Return (input file list, warnings)."""
    files: list[Path] = []
    seen: set[Path] = set()
    warnings: list[str] = []

    def _add(p: Path) -> None:
        rp = p.resolve()
        if rp in seen:
            return
        seen.add(rp)
        files.append(p)

    if not legend_file.exists():
        raise FileNotFoundError(f"legend file not found: {legend_file}")
    _add(legend_file)

    candidates: list[Path]
    if context_path is not None:
        candidates = [context_path]
    else:
        candidates = DEFAULT_CONTEXT_CANDIDATES

    found_any_context = False
    for cand in candidates:
        if not cand.exists():
            continue
        if cand.is_dir():
            json_files = sorted(cand.glob("*.json"))
            if not json_files:
                continue
            for jf in json_files:
                _add(jf)
            found_any_context = True
        elif cand.is_file():
            _add(cand)
            found_any_context = True
        # When --context-path is explicit, stop after the first hit.
        if context_path is not None:
            break
        # Without an explicit override, keep using the first candidate that yielded files.
        if found_any_context:
            break

    if not found_any_context:
        warnings.append(
            "heritage_context file/dir not found — proceeding with legend data only."
        )

    return files, warnings


def _print_report(
    result: RagIngestionResult,
    input_files: list[Path],
    output_dir: Path,
    warnings: list[str],
    json_output: bool,
) -> None:
    print("RAG ingestion completed.\n")

    print("Input files:")
    for f in input_files:
        print(f"- {f}")
    if warnings:
        print()
        for w in warnings:
            print(f"WARNING: {w}")

    print("\nItems:")
    print(f"- total_items: {result.total_items}")
    print(f"- success_items: {result.success_items}")
    print(f"- failed_items: {result.failed_items}")

    print("\nChunks:")
    print(f"- total_chunks: {result.total_chunks}")

    def _print_section(title: str, mapping: dict[str, int]) -> None:
        if not mapping:
            return
        print(f"\nBy {title}:")
        for k, v in sorted(mapping.items()):
            print(f"- {k}: {v}")

    _print_section("source_type", result.by_source_type)
    _print_section("content_role", result.by_content_role)
    _print_section("factuality_level", result.by_factuality_level)

    print("\nOutput:")
    print(f"- {output_dir / 'rag_chunks_ready.jsonl'}")
    if json_output:
        print(f"- {output_dir / 'rag_chunks_ready.json'}")
    print(f"- {output_dir / 'rag_ingestion_report.json'}")
    print(f"- {output_dir / 'rag_ingestion_failures.json'}")


def run(
    legend_file: Path = DEFAULT_LEGEND_FILE,
    context_path: Path | None = None,
    output_dir: Path = DEFAULT_OUTPUT_DIR,
    json_output: bool = True,
    fail_on_error: bool = False,
) -> RagIngestionResult:
    input_files, warnings = _resolve_input_files(legend_file, context_path)

    service = RagIngestionService()
    result = service.ingest_files(input_files)

    chunks_jsonl = output_dir / "rag_chunks_ready.jsonl"
    chunks_json = output_dir / "rag_chunks_ready.json"
    report_json = output_dir / "rag_ingestion_report.json"
    failures_json = output_dir / "rag_ingestion_failures.json"

    write_jsonl(result.chunks, chunks_jsonl)
    if json_output:
        write_json(result.chunks, chunks_json)

    report = {
        "status": "COMPLETED" if result.failed_items == 0 else "COMPLETED_WITH_FAILURES",
        "total_items": result.total_items,
        "success_items": result.success_items,
        "failed_items": result.failed_items,
        "total_chunks": result.total_chunks,
        "input_files": [str(p) for p in input_files],
        "output_files": {
            "chunks_jsonl": str(chunks_jsonl),
            "chunks_json": str(chunks_json) if json_output else None,
            "report": str(report_json),
            "failures": str(failures_json),
        },
        "by_source_type": result.by_source_type,
        "by_content_role": result.by_content_role,
        "by_factuality_level": result.by_factuality_level,
        "by_heritage_name": result.by_heritage_name,
    }
    write_json(report, report_json)
    write_json(result.failures, failures_json)

    _print_report(result, input_files, output_dir, warnings, json_output)

    if fail_on_error and result.failed_items > 0:
        raise SystemExit(1)
    return result


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Internal RAG ingestion (B-mode)")
    p.add_argument("--legend-file", type=Path, default=DEFAULT_LEGEND_FILE)
    p.add_argument(
        "--context-path",
        type=Path,
        default=None,
        help="File or directory containing heritage_context JSON.",
    )
    p.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    p.add_argument(
        "--json-output",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Also write rag_chunks_ready.json (pretty array).",
    )
    p.add_argument("--fail-on-error", action="store_true")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = _parse_args(argv)
    run(
        legend_file=args.legend_file,
        context_path=args.context_path,
        output_dir=args.output_dir,
        json_output=args.json_output,
        fail_on_error=args.fail_on_error,
    )


if __name__ == "__main__":
    main()
