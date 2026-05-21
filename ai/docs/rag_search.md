# RAG Search (149)

## 목적

148 까지 임베딩이 채워진 `rag_chunks.embedding` 을 대상으로, 사용자 query 또는 장소/문화재 기반 검색을 수행한다. 결과는 cosine distance 오름차순. 이 검색 결과는 151/152 의 `/story/intro` 내부에서 LLM 프롬프트의 `RAG_CONTEXT` 로 들어간다.

## 검색 대상 / 필드

- 테이블: `rag_chunks`
- 조건: `embedding_status = 'EMBEDDED'` AND `embedding IS NOT NULL`
- 검색 대상 컬럼: `embedding` (vector, 1536 dim)
- 응답에 포함: `chunk_id`, `heritage_name`, `title`, `content_role`, `source_type`, `factuality_level`, `narrative_type`, `folklore_status`, `source_site`, `source_urls`, `distance`, `similarity`, `mission_hooks`, `related_*`, `motifs`, `tone_tags`, optional `context_text` / `raw_text`, `metadata`

## Query embedding

148 에서 청크 임베딩에 사용한 모델과 **반드시 같은 모델** 로 query 를 임베딩해야 한다.
- model: `settings.OPENAI_EMBEDDING_MODEL` = `text-embedding-3-small`
- dim: `settings.RAG_EMBEDDING_DIM` = `1536`

`OpenAIEmbeddingClient.embed_text(query)` 한 번 호출로 끝. 빈 query 는 schema validator 에서 reject.

## pgvector cosine distance

```sql
embedding <=> :query_vec   -- cosine distance, 작을수록 유사
```

`RagSearchRepository` 는 SQLAlchemy `RagChunk.embedding.cosine_distance(query_embedding)` 를 사용해 `ORDER BY distance ASC LIMIT top_k` 를 실행한다. 147 의 HNSW 인덱스 (`vector_cosine_ops`) 가 자동 활용됨.

`similarity = 1 - distance` 는 참고값. 실제 품질 판단은 반환된 chunk 내용으로 한다.

## 지원 필터

| 필드 | 효과 |
|---|---|
| `heritage_names` | `heritage_name IN (...)` |
| `content_roles` | `fact_context` / `legend_material` / `symbolic_material` 만 |
| `factuality_levels` | `history` / `legend` / `mixed` / `symbolic` / `literary` / `unknown` |
| `source_types` | `heritage_context` / `historical_anecdote` / `religious_narrative` / ... |
| `distance_threshold` | `distance <= threshold` 인 결과만 (기본 None — 데이터 적은 MVP 단계에서는 끄는 것을 권장) |
| `top_k` | 1..`RAG_SEARCH_MAX_TOP_K` (default 5, max 20) |
| `include_context_text` | 기본 true (LLM 프롬프트에 사용) |
| `include_raw_text` | 기본 false |

## API

### POST /rag/search

```json
{
  "query": "첨성대 선덕여왕 별 미션",
  "top_k": 5,
  "content_roles": ["fact_context", "legend_material"],
  "include_context_text": true,
  "include_raw_text": false
}
```

응답은 `success / data / error / timestamp` wrapper. `data.results[*]` 에 distance 오름차순 정렬된 chunk 들.

### POST /rag/search/by-heritage

```json
{ "heritage_name": "첨성대", "top_k": 5 }
```

`query` 가 비어 있으면 `heritage_name` 자체가 query 로 fallback. 내부적으로 `/rag/search` 와 같은 service 경로를 타며 `heritage_names = [heritage_name]` 필터가 강제됨.

## 내부 script (서버 띄우지 않고 검색)

```bash
docker compose -p atoria exec ai python -m scripts.test_rag_search \
  --query "첨성대 선덕여왕 별 미션" --top-k 5 \
  --content-roles fact_context legend_material
```

## 사전 조건 확인

```sql
SELECT embedding_status, COUNT(*) FROM rag_chunks GROUP BY embedding_status;
-- expected: EMBEDDED | 25
SELECT COUNT(*) FROM rag_chunks WHERE embedding IS NOT NULL;
-- expected: 25
```

## 151 / 152 (story generation) 와의 연결

`/story/intro` 핸들러 안에서 `RagSearchService` 를 직접 호출 (HTTP 우회):

```python
fact = service.search(RagSearchRequest(
    query=f"{place.name} 사실", top_k=3,
    content_roles=["fact_context"],
)).results
story = service.search(RagSearchRequest(
    query=f"{place.name} 설화 미션", top_k=5,
    content_roles=["legend_material", "symbolic_material"],
)).results
```

검색된 `result.context_text` 들을 LLM 프롬프트의 `RAG_CONTEXT` 섹션으로 넣고, 응답에는 `result.source_urls` 를 출처로 남기면 됨. **이번 PR 에서는 LLM 호출 / `/story/intro` 수정은 하지 않습니다.**
