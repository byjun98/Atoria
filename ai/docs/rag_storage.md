# RAG Storage (147)

PostgreSQL + pgvector schema for the RAG pipeline.

## Tables

| Table | Purpose |
|---|---|
| `rag_source_documents` | One row per source record (heritage_context fact OR legend material) |
| `rag_chunks` | Semantic chunks; `embedding` column nullable until 148 fills it |

`rag_chunks.source_document_id → rag_source_documents.id (ON DELETE CASCADE)`.

## Embedding lifecycle

`rag_chunks.embedding_status` ∈
- `READY_FOR_EMBEDDING` — created/updated by 145 ingestion + 147 storage. **Default.**
- `EMBEDDING_IN_PROGRESS` — 148 worker has claimed the row.
- `EMBEDDED` — `embedding`, `embedding_model`, `embedded_at` filled. Re-ingestion will NOT downgrade this status.
- `EMBEDDING_FAILED` — 148 worker recorded a failure.

## Indexes

Per-column B-tree on `heritage_name`, `source_type`, `content_role`, `factuality_level`, `embedding_status`, plus composites `(heritage_name, content_role)` and `(heritage_name, factuality_level)`.

GIN on every JSONB array (`related_*`, `motifs`, `mission_keywords`, `mission_hooks`, `extra_metadata`).

pgvector HNSW on `rag_chunks.embedding` with `vector_cosine_ops`. If the running pgvector build doesn't support HNSW, swap to `ivfflat` in the migration.

## Embedding dimension

Driven by `settings.RAG_EMBEDDING_DIM` (default 1536, matches `text-embedding-3-small`). Changing this requires a new migration that does `ALTER TABLE rag_chunks ALTER COLUMN embedding TYPE vector(N)`.

## Pipeline

```
1) python -m scripts.ingest_rag_documents     # 145 — write JSONL
2) alembic upgrade head                       # 147 — apply migration
3) python -m scripts.store_rag_chunks         # 147 — load JSONL into PG
4) (148) generate embeddings + UPDATE rag_chunks SET embedding = ...
```

## 148 hooks already on the repository

```python
repo = RagChunkRepository(session)
ready = repo.list_chunks_ready_for_embedding(limit=100)   # query
repo.update_chunk_embedding(chunk_id, vector, model_name) # apply
```

`update_chunk_embedding` sets `embedding`, `embedding_model`, `embedded_at`, and `embedding_status = "EMBEDDED"` atomically.
