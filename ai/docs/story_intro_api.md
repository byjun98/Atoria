# /story/intro (152)

## 목적

Spring Boot 가 코스 확정 후 한 번 호출해 받아오는 **오프닝 스토리 + 장소별 미션 + 아웃트로** 를 실제 LLM 으로 생성한다. wire schema (request / response) 는 [API.md](../API.md) 의 기존 명세 그대로 유지.

## 처리 흐름

```
Spring Boot
   └─ POST /story/intro  (StoryIntroRequest, API.md schema)
        ▼
   StorySchemaAdapter.to_internal_request
        ▼
   StoryIntroService.generate_intro
        ├─ for each place:
        │     RagSearchService.search(content_roles=["fact_context"])
        │     RagSearchService.search(content_roles=["legend_material","symbolic_material"])
        │   → RagContextByPlace 조립
        ├─ StoryPromptBuilder.build_intro_messages(req)
        ├─ OpenAIChatClient.create_json_chat_completion(messages)
        │     (response_format=json_object)
        └─ StoryResponseParser.parse_intro_response → StoryIntroDraft
        ▼
   StorySchemaAdapter.to_wire_response
        ▼
   StoryIntroResponse (API.md schema)
```

## 모듈 / 책임

| 위치 | 책임 |
|---|---|
| `app/api/v1/story.py` | `POST /story/intro` 라우터 + DI |
| `app/services/story/story_schema_adapter.py` | wire ↔ 151 schema 변환 |
| `app/services/story/story_intro_service.py` | RAG 검색 + 프롬프트 + LLM 호출 orchestration |
| `app/services/story/story_response_parser.py` | LLM JSON → `StoryIntroDraft` 검증 |
| `app/clients/openai_chat_client.py` | OpenAI Chat Completions 호출 (JSON mode) |

## RAG 검색 정책

장소마다 두 번 호출:

1. `content_roles=["fact_context"]`, `top_k=STORY_FACT_TOP_K(2)`, `heritage_names=[place_name]`
2. `content_roles=["legend_material","symbolic_material"]`, `top_k=STORY_MATERIAL_TOP_K(3)`, `heritage_names=[place_name]`

`STORY_RAG_ENABLE_FALLBACK=true` (default) 면 1차 결과 0건일 때 `heritage_names` 없이 2차 호출. 동궁과월지 / 안압지처럼 별칭으로 저장된 chunk 를 놓치지 않기 위함.

`RagSearchResultItem` → `RagContextItem` 변환 시 `chunk_id`, `factuality_level`, `source_urls`, `mission_hooks` 등 모두 보존. `content_role == "symbolic_material"` 인 결과는 `RagContextByPlace.symbolic_materials` 로, 그 외는 `story_materials` 로 분배.

## OpenAI 호출

- 모델: `settings.OPENAI_MODEL` (`gpt-4o`)
- temperature: `settings.STORY_LLM_TEMPERATURE` (`0.7`)
- `response_format={"type": "json_object"}` — LLM 이 코드펜스 없는 순수 JSON 만 반환하도록 강제
- API base URL 은 SSAFY GMS proxy (`OPENAI_BASE_URL`) 그대로 사용

## 응답 검증

`StoryResponseParser.parse_intro_response` 에서 다음 단계로 검증:

1. 빈 응답 → `STORY_LLM_EMPTY_RESPONSE`
2. JSON 파싱 실패 → `STORY_LLM_INVALID_JSON`
3. `StoryIntroDraft.model_validate` 실패 → `STORY_LLM_SCHEMA_VALIDATION_ERROR`

추가 사후 보정 (과하지 않게):
- `places` 가 sequence 순으로 정렬됨
- LLM 이 `places` 개수를 다르게 줬으면 `warnings` 에 1줄 기록
- `mission.related_place_name` 이 빈 문자열이면 해당 place 의 이름으로 backfill

## 오류 코드 → HTTP 매핑

| 오류 | HTTP | detail.code |
|---|---|---|
| `StoryResponseError(STORY_LLM_*)` | 502 | LLM 응답 형식 문제 (upstream contract 위반) |
| `ValueError` (request 검증 등) | 400 | 메시지 그대로 |
| 그 외 (`OpenAI 실패` 포함) | 500 | `STORY_GENERATION_FAILED` |
| Pydantic wire-schema 위반 | 422 | FastAPI 기본 |

## Spring Boot 에 반환되는 응답

[API.md](../API.md) 그대로:

```json
{
  "intro": "...",
  "missions": [
    { "sequence": 1, "title": "...", "description": "...", "type": "PHOTO|CHOICE|QUIZ|ACTION", "story": "..." }
  ],
  "outro": "..."
}
```

내부 `MissionDraft.mission_type` (`observation/photo/quiz/imagination/route`) → wire `MissionType` 매핑:

| 내부 | wire |
|---|---|
| `photo` | `PHOTO` |
| `quiz` | `QUIZ` |
| `observation` / `imagination` / `route` | `ACTION` |

`used_chunk_ids`, `source_urls`, `warnings` 는 API.md 응답 형태 유지를 위해 wire 응답에 노출하지 않음. 후속 이슈에서 필요해지면 응답 계약을 확장할 때 같이 추가.

## 사전 조건

- DB / AI 컨테이너 기동
- `rag_chunks` 에 `EMBEDDED` 데이터 존재 (148 까지 통과)
- `OPENAI_API_KEY` 환경변수 주입
- 마이그레이션 적용 완료

## Docker 환경 검증

```bash
# 상태 확인
docker compose -p atoria exec db psql -U postgres -d atoria_ai -c \
  "SELECT embedding_status, COUNT(*) FROM rag_chunks GROUP BY embedding_status;"

# 실제 호출
docker compose -p atoria exec ai python -c "
import json, urllib.request
payload = {
  'people_cnt': 2,
  'people_information': [
    {'name': '민준', 'age': 5, 'tendency': 'curious'},
    {'name': '성준', 'age': 6, 'tendency': 'curious'}
  ],
  'places': [
    {'place_id': 301, 'sequence': 1, 'name': '첨성대',
     'description': '신라 천문 관측소', 'address': '경북 경주시',
     'category': '역사', 'latitude': 35.83, 'longitude': 129.21},
    {'place_id': 302, 'sequence': 2, 'name': '동궁과월지',
     'description': '신라 별궁', 'address': '경북 경주시',
     'category': '역사', 'latitude': 35.84, 'longitude': 129.23}
  ]
}
req = urllib.request.Request('http://localhost:8000/story/intro',
    data=json.dumps(payload).encode('utf-8'),
    headers={'Content-Type':'application/json'}, method='POST')
print(urllib.request.urlopen(req, timeout=120).read().decode('utf-8'))
"
```

## 153 (`/story/update`) 와의 차이

- 152: 코스 시작 직전, 한 번 호출, intro + 장소별 미션 + outro 를 한꺼번에 생성
- 153: 한 장소 미션 완료 후 호출, 직전 스토리 흐름 + 미션 결과를 받아 다음 한 장면만 생성
- 둘 다 같은 `StoryPromptBuilder` 와 `OpenAIChatClient` 를 재사용. 153 은 `build_update_messages` 만 갈아끼면 됨.

## 의도적으로 제외 (후속 이슈)

- `/story/update` 구현 (153)
- 154 품질 제어 고도화 (factual hallucination 검증, 길이 제약 강제 등)
- E-book 생성 로직 변경 (`/artifacts/ebook/jobs`)
- LangChain 사용
