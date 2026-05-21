# RAG Embedding (148)

## 목적

147 에서 DB 에 저장된 `embedding_status = READY_FOR_EMBEDDING` 청크를, OpenAI Embeddings API 로 벡터화하여 `rag_chunks.embedding` 에 저장한다. 저장이 끝나면 `embedding_status = EMBEDDED`.

## 왜 raw_text 가 아니라 context_text 를 임베딩하나

`context_text` = `[문화재 | 제목 | 콘텐츠 역할 | 자료 유형 | 사실성 | 시대 | 인물 | 관련 장소 | 모티프 | 분위기 | 출처 | 검토 필요]\n<raw_text>`.

검색용 메타데이터 헤더가 본문과 함께 임베딩되면, 사용자 질의에 "신라 / 첨성대 / 별 / 선덕여왕" 같은 단어가 들어왔을 때 본문에는 없어도 헤더에 노출되어 매칭이 잘 된다. 그래서 144 에서 만든 `context_text` 를 그대로 임베딩 입력으로 쓴다.

## Model / dimension

- `OPENAI_EMBEDDING_MODEL` = `text-embedding-3-small` (기본)
- `RAG_EMBEDDING_DIM` = `1536`
- 두 값이 일치해야 함. `RAG_EMBEDDING_DIM` 을 바꾸면 147 마이그레이션의 `vector(1536)` 컬럼 타입도 같이 바꿔야 함 (`ALTER TABLE rag_chunks ALTER COLUMN embedding TYPE vector(N)`).

## 실행 전 조건

- DB / AI 컨테이너 기동 중
- 147 마이그레이션 적용 완료
- 145 → 147 흐름으로 `READY_FOR_EMBEDDING` 청크가 DB 에 적재되어 있음
- `OPENAI_API_KEY` 가 ai 컨테이너 환경변수로 주입됨 (`docker-compose.yml` → `env_file: .env`)

```bash
docker compose -p atoria exec ai python -c "import os; print(bool(os.getenv('OPENAI_API_KEY')), os.getenv('OPENAI_EMBEDDING_MODEL'))"
docker compose -p atoria exec db psql -U postgres -d atoria_ai -c \
  "SELECT embedding_status, COUNT(*) FROM rag_chunks GROUP BY embedding_status;"
```

## Dry-run

API 호출 없이 대상 청크 수와 chunk_id 만 확인.

```bash
docker compose -p atoria exec ai python -m scripts.embed_rag_chunks --dry-run --limit 25
```

`OPENAI_API_KEY` 미설정 환경에서도 실행 가능. report 파일은 `data/processed/rag_embedding_report.json` 에 `status: "DRY_RUN"` 으로 저장.

## 실제 실행

```bash
docker compose -p atoria exec ai python -m scripts.embed_rag_chunks \
  --limit 100 \
  --batch-size 50 \
  --report-file data/processed/rag_embedding_report.json
```

기본값: `--limit 100`, `--batch-size 50` (settings).

## 처리 흐름

1. `RagChunkRepository.list_chunks_ready_for_embedding(limit)` 로 후보 조회
2. 각 청크 `context_text` 비어있는지 검증 — 비어있으면 `skipped_count++`, `mark_chunk_embedding_failed`
3. `batch_size` 단위로 `OpenAIEmbeddingClient.embed_texts(...)` 호출
4. **batch 전체 실패** (rate limit, network 등) → batch 내 모든 청크를 `EMBEDDING_FAILED` 로 마킹, 다음 batch 계속 진행
5. 성공 청크 → `repository.update_chunk_embedding(chunk_id, vector, model)` 호출
   - `embedding`, `embedding_model`, `embedded_at`, `embedding_status="EMBEDDED"` 4개 컬럼을 한 번에 채움
6. report JSON 저장

## 결과 확인

```sql
-- 상태 분포
SELECT embedding_status, COUNT(*) FROM rag_chunks GROUP BY embedding_status;

-- vector 가 채워진 행 수
SELECT COUNT(*) FROM rag_chunks WHERE embedding IS NOT NULL;

-- 실패 사유
SELECT chunk_id, extra_metadata->>'embedding_error'
  FROM rag_chunks WHERE embedding_status = 'EMBEDDING_FAILED';
```

## 실패 처리

- batch 단위 API 실패 → batch 내 모든 청크 `EMBEDDING_FAILED`. 사유는 `extra_metadata.embedding_error` 에 1000자까지 저장.
- DB update 실패 → 해당 청크만 `EMBEDDING_FAILED`.
- `context_text` 빈 청크 → `INVALID_CHUNK` 로 분류, `skipped_count` 에 집계.
- 단일 chunk 실패가 전체 배치를 중단시키지 않음. 시스템 오류 (`OPENAI_API_KEY` 누락 등) 는 명시적으로 raise.

## 149 (RAG 검색) 와의 연결

149 는 사용자 질의 텍스트를 같은 모델 (`OPENAI_EMBEDDING_MODEL`) 로 임베딩한 뒤, `rag_chunks.embedding` 과 cosine distance 비교로 top-k 청크를 가져온다. HNSW 인덱스 (`vector_cosine_ops`) 가 147 에서 이미 생성되어 있으므로 별도 인덱싱 작업은 불필요.

```sql
-- 149 검색 예시
SELECT chunk_id, heritage_name, content_role,
       embedding <=> :query_vector AS distance
  FROM rag_chunks
 WHERE embedding_status = 'EMBEDDED'
 ORDER BY distance ASC
 LIMIT 5;
```
