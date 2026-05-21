"""
RAG ingestion service (B-mode).

Reads local heritage_context / heritage_legend_materials JSON, reuses the
144 chunking pipeline (SourceDocumentNormalizer + SemanticChunker +
ContextBuilder + MissionHookExtractor), and emits READY_FOR_EMBEDDING
chunk dicts. Does NOT call OpenAI / pgvector / DB / HTTP API.
"""
from __future__ import annotations

import json
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

from app.schemas.chunk_schema import RagChunk
from app.services.chunking import SemanticChunker, SourceDocumentNormalizer


READY_STATUS = "READY_FOR_EMBEDDING"


@dataclass
class RagIngestionResult:
    total_items: int = 0
    success_items: int = 0
    failed_items: int = 0
    total_chunks: int = 0
    chunks: list[dict] = field(default_factory=list)
    failures: list[dict] = field(default_factory=list)
    by_source_type: dict[str, int] = field(default_factory=dict)
    by_content_role: dict[str, int] = field(default_factory=dict)
    by_factuality_level: dict[str, int] = field(default_factory=dict)
    by_heritage_name: dict[str, int] = field(default_factory=dict)


class RagIngestionService:
    """Run the 144 chunking pipeline over local source items."""

    def __init__(
        self,
        normalizer: SourceDocumentNormalizer | None = None,
        chunker: SemanticChunker | None = None,
    ) -> None:
        self.normalizer = normalizer or SourceDocumentNormalizer()
        self.chunker = chunker or SemanticChunker()

    # ---- public ---------------------------------------------------------

    def ingest_items(self, items: list[dict]) -> RagIngestionResult:
        result = RagIngestionResult(total_items=len(items))
        for idx, item in enumerate(items):
            chunks, failure = self._chunk_item(item, idx)
            if failure is not None:
                result.failed_items += 1
                result.failures.append(failure)
                continue
            result.success_items += 1
            result.chunks.extend(chunks)

        result.total_chunks = len(result.chunks)
        result.by_source_type = dict(Counter(c["source_type"] for c in result.chunks))
        result.by_content_role = dict(Counter(c["content_role"] for c in result.chunks))
        result.by_factuality_level = dict(
            Counter((c.get("factuality_level") or "unknown") for c in result.chunks)
        )
        result.by_heritage_name = dict(
            Counter(c["heritage_name"] for c in result.chunks if c.get("heritage_name"))
        )
        return result

    def ingest_files(self, file_paths: list[Path]) -> RagIngestionResult:
        items: list[dict] = []
        for p in file_paths:
            items.extend(self._load_json_file(p))
        return self.ingest_items(items)

    # ---- helpers --------------------------------------------------------

    def _load_json_file(self, file_path: Path) -> list[dict]:
        if not file_path.exists():
            raise FileNotFoundError(f"Input file not found: {file_path}")
        try:
            with file_path.open(encoding="utf-8") as f:
                data = json.load(f)
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid JSON in {file_path}: {e}") from e
        if isinstance(data, list):
            return [d for d in data if isinstance(d, dict)]
        if isinstance(data, dict):
            return [data]
        raise ValueError(f"Unsupported JSON root in {file_path}: {type(data).__name__}")

    def _chunk_item(
        self, item: dict, index: int
    ) -> tuple[list[dict], dict | None]:
        record_id = item.get("legend_id") or item.get("record_id")
        heritage_name = item.get("heritage_name")
        try:
            doc = self.normalizer.normalize(item)
            chunks = self.chunker.chunk_document(doc)
            if not chunks:
                return [], {
                    "index": index,
                    "source_record_id": record_id,
                    "heritage_name": heritage_name,
                    "error_code": "EMPTY_CHUNKS",
                    "error_message": "Normalizer produced no chunkable text.",
                }
            return [self._to_ready_for_embedding_dict(c) for c in chunks], None
        except (ValueError, KeyError, TypeError) as e:
            return [], {
                "index": index,
                "source_record_id": record_id,
                "heritage_name": heritage_name,
                "error_code": type(e).__name__,
                "error_message": str(e),
            }

    @staticmethod
    def _to_ready_for_embedding_dict(chunk: RagChunk) -> dict:
        d = chunk.model_dump()
        d["embedding_status"] = READY_STATUS
        d["embedded_at"] = None
        d["embedding_model"] = None
        d["vector_id"] = None
        return d


# --- writers --------------------------------------------------------------


def _ensure_dir(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def write_jsonl(chunks: Iterable[dict], output_path: Path) -> None:
    _ensure_dir(output_path)
    with output_path.open("w", encoding="utf-8") as f:
        for chunk in chunks:
            f.write(json.dumps(chunk, ensure_ascii=False))
            f.write("\n")


def write_json(data: Any, output_path: Path) -> None:
    _ensure_dir(output_path)
    with output_path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
