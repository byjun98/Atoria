"""
RagChunkStorageService — read READY_FOR_EMBEDDING chunks (from JSONL or list)
and persist them via RagChunkRepository. No OpenAI / embedding calls.
"""
from __future__ import annotations

import json
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

from app.repositories.rag_chunk_repository import RagChunkRepository


@dataclass
class RagStorageResult:
    total_chunks: int = 0
    inserted_documents: int = 0
    updated_documents: int = 0
    inserted_chunks: int = 0
    updated_chunks: int = 0
    failed_chunks: int = 0
    failures: list[dict] = field(default_factory=list)
    by_embedding_status: dict[str, int] = field(default_factory=dict)
    by_content_role: dict[str, int] = field(default_factory=dict)
    by_factuality_level: dict[str, int] = field(default_factory=dict)
    by_heritage_name: dict[str, int] = field(default_factory=dict)


class RagChunkStorageService:
    def __init__(self, repository: RagChunkRepository) -> None:
        self.repository = repository

    # ---- public ---------------------------------------------------------

    def store_ready_chunks_from_jsonl(self, file_path: Path) -> RagStorageResult:
        chunks = list(self._iter_jsonl(file_path))
        return self.store_ready_chunks(chunks)

    def store_ready_chunks(self, chunks: list[dict]) -> RagStorageResult:
        result = RagStorageResult(total_chunks=len(chunks))
        # Group chunks by source_record_id so we upsert each document just once.
        by_doc: dict[str, list[dict]] = {}
        for c in chunks:
            by_doc.setdefault(c["source_record_id"], []).append(c)

        for record_id, group in by_doc.items():
            head = group[0]
            try:
                doc, doc_inserted = self.repository.upsert_source_document(head)
            except (KeyError, ValueError, TypeError) as e:
                # Whole document failed — mark every chunk in the group as failed.
                for c in group:
                    result.failed_chunks += 1
                    result.failures.append(
                        {
                            "chunk_id": c.get("chunk_id"),
                            "source_record_id": record_id,
                            "error_code": type(e).__name__,
                            "error_message": f"upsert_source_document failed: {e}",
                        }
                    )
                continue

            if doc_inserted:
                result.inserted_documents += 1
            else:
                result.updated_documents += 1

            for c in group:
                try:
                    _, chunk_inserted = self.repository.upsert_chunk(doc, c)
                except (KeyError, ValueError, TypeError) as e:
                    result.failed_chunks += 1
                    result.failures.append(
                        {
                            "chunk_id": c.get("chunk_id"),
                            "source_record_id": record_id,
                            "error_code": type(e).__name__,
                            "error_message": str(e),
                        }
                    )
                    continue
                if chunk_inserted:
                    result.inserted_chunks += 1
                else:
                    result.updated_chunks += 1

        # Statistics across all input chunks (not just the persisted ones, so the
        # report describes the input slice).
        result.by_embedding_status = dict(
            Counter(c.get("embedding_status", "READY_FOR_EMBEDDING") for c in chunks)
        )
        result.by_content_role = dict(Counter(c.get("content_role", "unknown") for c in chunks))
        result.by_factuality_level = dict(
            Counter(c.get("factuality_level") or "unknown" for c in chunks)
        )
        result.by_heritage_name = dict(
            Counter(c["heritage_name"] for c in chunks if c.get("heritage_name"))
        )
        return result

    # ---- helpers --------------------------------------------------------

    @staticmethod
    def _iter_jsonl(file_path: Path) -> Iterable[dict]:
        if not file_path.exists():
            raise FileNotFoundError(f"input JSONL not found: {file_path}")
        with file_path.open(encoding="utf-8") as f:
            for line_no, raw in enumerate(f, start=1):
                line = raw.strip()
                if not line:
                    continue
                try:
                    yield json.loads(line)
                except json.JSONDecodeError as e:
                    raise ValueError(
                        f"Invalid JSON on line {line_no} of {file_path}: {e}"
                    ) from e
