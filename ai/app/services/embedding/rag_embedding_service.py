"""
RagEmbeddingService — orchestrates DB → OpenAI Embeddings → DB.

- Reads READY_FOR_EMBEDDING chunks from `RagChunkRepository`.
- Sends `chunk.context_text` (NOT raw_text) to the embedding client.
- On success: `update_chunk_embedding` (sets embedding, model, status=EMBEDDED).
- On failure: `mark_chunk_embedding_failed` (status=EMBEDDING_FAILED), records
  the chunk in `failures`, continues with the next batch.
"""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field

from app.clients.openai_embedding_client import OpenAIEmbeddingClient
from app.db.models.rag_chunk import RagChunk
from app.repositories.rag_chunk_repository import RagChunkRepository


@dataclass
class RagEmbeddingResult:
    total_candidates: int = 0
    embedded_count: int = 0
    failed_count: int = 0
    skipped_count: int = 0
    embedding_model: str = ""
    embedding_dim: int = 0
    failures: list[dict] = field(default_factory=list)
    embedded_chunk_ids: list[str] = field(default_factory=list)
    by_content_role: dict[str, int] = field(default_factory=dict)
    by_factuality_level: dict[str, int] = field(default_factory=dict)
    by_heritage_name: dict[str, int] = field(default_factory=dict)


class RagEmbeddingService:
    def __init__(
        self,
        repository: RagChunkRepository,
        embedding_client: OpenAIEmbeddingClient,
        batch_size: int = 50,
    ) -> None:
        if batch_size <= 0:
            raise ValueError("batch_size must be > 0")
        self.repository = repository
        self.client = embedding_client
        self.batch_size = batch_size

    # ---- public ---------------------------------------------------------

    def embed_ready_chunks(self, limit: int = 100) -> RagEmbeddingResult:
        candidates = self.repository.list_chunks_ready_for_embedding(limit=limit)
        result = RagEmbeddingResult(
            total_candidates=len(candidates),
            embedding_model=self.client.model,
            embedding_dim=self.client.expected_dim,
        )
        if not candidates:
            return result

        # Skip empty-context_text chunks up-front, keep them out of the API batch.
        valid: list[RagChunk] = []
        for chunk in candidates:
            try:
                self._validate_chunk_for_embedding(chunk)
            except ValueError as e:
                result.skipped_count += 1
                result.failures.append(
                    {
                        "chunk_id": chunk.chunk_id,
                        "heritage_name": chunk.heritage_name,
                        "error_code": "INVALID_CHUNK",
                        "error_message": str(e),
                    }
                )
                self._safe_mark_failed(chunk.chunk_id, str(e))
                continue
            valid.append(chunk)

        embedded_chunks: list[RagChunk] = []
        for batch in _chunked(valid, self.batch_size):
            ok, fails = self._embed_batch(batch)
            embedded_chunks.extend(ok)
            result.failures.extend(fails)

        result.embedded_count = len(embedded_chunks)
        result.failed_count = sum(
            1 for f in result.failures if f["error_code"] != "INVALID_CHUNK"
        ) + 0  # invalid chunks already counted under skipped_count
        result.embedded_chunk_ids = [c.chunk_id for c in embedded_chunks]
        result.by_content_role = dict(
            Counter(c.content_role for c in embedded_chunks if c.content_role)
        )
        result.by_factuality_level = dict(
            Counter((c.factuality_level or "unknown") for c in embedded_chunks)
        )
        result.by_heritage_name = dict(
            Counter(c.heritage_name for c in embedded_chunks if c.heritage_name)
        )
        return result

    # ---- helpers --------------------------------------------------------

    @staticmethod
    def _validate_chunk_for_embedding(chunk: RagChunk) -> None:
        if not chunk.context_text or not chunk.context_text.strip():
            raise ValueError("context_text is empty")

    def _safe_mark_failed(self, chunk_id: str, message: str) -> None:
        try:
            self.repository.mark_chunk_embedding_failed(chunk_id, message)
        except Exception:  # repository failure must not crash the batch loop
            pass

    def _embed_batch(
        self, chunks: list[RagChunk]
    ) -> tuple[list[RagChunk], list[dict]]:
        texts = [c.context_text for c in chunks]
        try:
            vectors = self.client.embed_texts(texts)
        except Exception as e:
            # OpenAI batch failure → every chunk in this batch is failed.
            fails = []
            for c in chunks:
                self._safe_mark_failed(c.chunk_id, str(e))
                fails.append(
                    {
                        "chunk_id": c.chunk_id,
                        "heritage_name": c.heritage_name,
                        "error_code": "EMBEDDING_API_ERROR",
                        "error_message": str(e),
                    }
                )
            return [], fails

        ok: list[RagChunk] = []
        fails: list[dict] = []
        for chunk, vec in zip(chunks, vectors):
            try:
                self.repository.update_chunk_embedding(
                    chunk_id=chunk.chunk_id,
                    embedding=vec,
                    embedding_model=self.client.model,
                )
                ok.append(chunk)
            except Exception as e:
                self._safe_mark_failed(chunk.chunk_id, str(e))
                fails.append(
                    {
                        "chunk_id": chunk.chunk_id,
                        "heritage_name": chunk.heritage_name,
                        "error_code": "DB_UPDATE_FAILED",
                        "error_message": str(e),
                    }
                )
        return ok, fails


def _chunked(items: list, size: int):
    for i in range(0, len(items), size):
        yield items[i : i + size]
