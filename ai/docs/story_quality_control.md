# Story Quality Control (154)

## 목적

LLM 응답을 그대로 사용자에게 반환하지 않는다. 152 까지의 흐름은 "LLM 이 JSON 만 잘 주면 통과" 였지만, 운영 데이터를 보면

- mission 이 "문화재를 잘 관찰해보세요" 같은 일반 문장으로 떨어진다
- 설화 자료를 "실제로 그랬다" 처럼 단정한다
- places 개수가 요청과 다르게 줄어든다
- `used_chunk_ids` / `source_urls` 가 비어 채워지지 않는다

같은 회귀가 발생한다. 154 는 이 회귀를 **검증 → 자동 보정 → (옵션) 1회 재시도** 의 3단으로 잡는다.

## 모듈 / 책임

| 위치 | 책임 |
|---|---|
| `app/services/story/story_quality_models.py` | `StoryQualityIssue`, `StoryQualityReport`, `ISSUE_CODES` |
| `app/services/story/story_quality_validator.py` | 구조 / 길이 / 미션 / RAG 사용 / 사실·설화 표현 / 일반 문장 검증 (read-only) |
| `app/services/story/story_quality_repairer.py` | 안전한 메타데이터 보정만 (sequence 정렬, related_place_name backfill, used_chunk_ids/source_urls 합집합, mission_type alias 정규화) |
| `app/services/story/story_intro_service.py` | 위 둘을 LLM 호출 후 파이프라인에 끼움 |

## 검증 항목 (`StoryQualityValidator`)

| 분류 | code | severity | 비고 |
|---|---|---|---|
| 구조 | `EMPTY_TITLE` | warning | |
| | `EMPTY_INTRO` | error | |
| | `EMPTY_OUTRO` | error | |
| | `PLACE_COUNT_MISMATCH` | error | |
| | `PLACE_SEQUENCE_MISMATCH` | warning (auto_fixable) | |
| | `PLACE_NAME_MISMATCH` | warning | |
| 길이 | `INTRO_TOO_SHORT` / `INTRO_TOO_LONG` | warning | |
| | `OUTRO_TOO_SHORT` / `OUTRO_TOO_LONG` | warning | |
| | `PLACE_STORY_TOO_SHORT` / `_TOO_LONG` | warning | |
| | `MISSION_DESCRIPTION_TOO_SHORT` | warning | |
| 미션 | `MISSING_MISSION` | error | |
| | `MISSION_TITLE_EMPTY` | error | |
| | `MISSION_DESCRIPTION_EMPTY` | error | |
| | `MISSION_TYPE_INVALID` | error (auto_fixable) | |
| | `MISSION_NOT_OBSERVABLE` | warning | 관찰/사진/방향 등 키워드 부재 |
| | `MISSION_RELATED_PLACE_MISSING` | warning (auto_fixable) | |
| | `DUPLICATE_MISSION` | warning | 같은 title/instruction 반복 |
| RAG 사용 | `MISSING_RAG_USAGE` | warning (auto_fixable) | RAG 가 있었는데 `used_chunk_ids` 빈 경우 |
| | `MISSING_SOURCE_TRACE` | warning (auto_fixable) | |
| | `PLACE_USED_CHUNK_MISSING` / `PLACE_SOURCE_URLS_MISSING` | warning (auto_fixable) | 장소별 |
| 표현 | `FACT_LEGEND_CONFUSION` | warning | legend/mixed 자료에서 단정 어조 (`실제로/반드시/사실이다/확실히`) |
| | `SYMBOLIC_AS_FACT` | warning | symbolic 자료가 있는데 `상징/상상/의미로/해석할 수` 어구가 전혀 없음 |
| 일반성 | `STORY_TOO_GENERIC` | warning | 흔한 문장 또는 장소 고유 키워드 부재 |

`STORY_LLM_*` 같은 LLM-구조 오류는 152 의 `StoryResponseParser` 에서 이미 처리. 154 는 그 이후 단계만 본다.

## 자동 보정 항목 (`StoryQualityRepairer`)

LLM 본문(prose) 은 절대 다시 쓰지 않는다. 메타데이터 / 구조만:

1. `places` 를 `sequence` 오름차순 정렬
2. 빈 `mission.related_place_name` → 해당 `place.place_name` 으로 backfill
3. 빈 `place.used_chunk_ids` → 해당 `RagContextByPlace` 의 모든 chunk_id 로 backfill
4. 빈 `place.source_urls` → 해당 RAG 의 `source_urls` 합집합으로 backfill
5. 빈 `mission.related_chunk_ids` → `place.used_chunk_ids` 로 backfill
6. `draft.used_chunk_ids` / `draft.source_urls` → 모든 places 의 합집합으로 union
7. wire-style mission_type alias (`PHOTO/QUIZ/ACTION/CHOICE`) → 내부 형식 (`photo/quiz/observation`) 으로 정규화

수정한 항목은 `StoryQualityReport.fixed_count` 로 집계.

## Retry 정책

```
settings.STORY_QUALITY_ENABLE_RETRY  (default: true)
settings.STORY_QUALITY_MAX_RETRIES   (default: 2)
```

- repair 후에도 `error` 가 남으면 retry 후보
- retry 메시지는 원본 `messages` 에 `[REPAIR_INSTRUCTION]` 블록을 사용자 메시지 끝에 추가
  - 실패 사유 (issue.code + message) 를 함께 전달
  - 기존 OUTPUT_JSON_SCHEMA, 요청 장소 수, 비어있지 않은 intro/outro/mission 을 다시 강조
- 최대 2회 재시도. 재시도 후에도 실패하면 `StoryQualityValidationFailed` 예외
- 아토리아 미션 품질 검수는 사후 촬영 표현이나 접근 제한 위반을 hard error로 잡기 때문에 retry를 기본 활성화

## StoryIntroService 와의 연결

수정 전:
```
RAG → builder → chat → parser → reconcile → return
```

수정 후:
```
RAG → builder → chat → parser → reconcile
            ↓
       validator
            ↓
       repairer
            ↓
       (재검증)
            ↓
   has_errors? ── no ──→ return
            └── yes ──→ retry once?
                        ├── yes → chat → ... → return / raise
                        └── no  → raise StoryQualityValidationFailed
```

`StoryQualityValidationFailed` 는 router 에서 502 + `STORY_QUALITY_VALIDATION_FAILED` 코드 + `issue_codes` 배열로 매핑.

## Spring Boot wire response 영향

**없음.** API.md 의 `intro / missions[] / outro` 응답 형태는 그대로 유지. quality warning 은 외부에 노출하지 않고 다음 두 곳에 보존:

1. `StoryIntroDraft.warnings` — service 내부 호출자가 직접 draft 를 쓰는 경우 활용
2. 구조화 로그 (`_logger.info("story_intro_generated", ...)`) — `request_id`, `passed`, `error_count`, `warning_count`, `fixed_count`, `issue_codes`, `used_chunk_count`, `source_url_count` 만 남김. 본문 / 개인정보 / 전체 prompt 는 절대 로그하지 않음

## 154 가 하지 않는 것

- LLM 본문 자동 재작성 (단어 교체 / 문장 swap / 길이 강제 truncate)
- factual hallucination 의 사실 검증 (RAG chunk 와 의미 비교 등)
- mission 다양성 / persona 적합성 평가
- A/B 평가 데이터셋 / 품질 지표 트래킹

위 항목들은 향후 154+ 고도화에서 처리.

## 154-prompt-pass: 프롬프트 + validator 동시 강화

운영 호출 결과 "구조는 통과하지만 일반적인 응답" 회귀가 잡혔다 (예: 첨성대 미션이 "경계 찾기" 같은 결과). validator/repairer 만으로는 해결이 안 되어 같은 154 작업 안에서 프롬프트와 validator 를 동시에 강화했다.

### 프롬프트 변경 (`STORY_PROMPT_VERSION = story_intro_v2`)

system prompt 에 추가된 핵심 규칙:
- **장소 고유성 규칙(가장 중요)**: 모든 장소에 같은 구조의 일반 미션 반복 금지. story_fragment / mission 은 그 장소의 RAG_CONTEXT (motifs / mission_hooks / heritage_name) 를 반드시 인용.
- **mission_title 좋은/나쁜 예시**: "탐험·조사·찾기·관찰·둘러보기" 한 단어 제목 금지. 좋은 예시 (`첨성대 남쪽 창이 바라보는 하늘 찾기`, `월지에 비친 누각의 그림자 포착하기`, `에밀레종의 울림을 상상하는 비천상 무늬 찾기`) 인용.
- **story_fragment 3문장 구조**: 1) fact 사실, 2) legend/symbolic 분위기 (완화 표현), 3) 사용자 시점의 현장 동작.
- **intro/outro 규칙**: intro 는 사용자 프로필 + 코스 테마 + 장소명 2개 이상. outro 는 방문 장소 경험 2개 이상 회상.
- **문체 규칙**: "~해보세요" 반복 금지. 행동 동사를 구체화 (세어 본다 / 걸어 본다 / 사진으로 남긴다 / 비교해 본다 / 가족과 이야기해 본다).
- **자료 신뢰 단계 표현 분리**: fact_context = 단정 어조 / legend_material = "전해진다 / 이야기된다 / 상상해 볼 수 있다" / symbolic_material = "상징적으로 / 의미로 해석하면".

user prompt 에 추가된 섹션:
- `[MISSION_QUALITY_RULES]` — 미션의 정량 기준
- `[PLACE_SPECIFICITY_RULES]` — 장소 고유 키워드 사용 강제
- `[STYLE_RULES]` — 문체 가이드

### `RagContextFormatter` 개선

각 chunk 출력에 `content_role` 과 `usage_hint` 가 추가되어 LLM 이 자료 신뢰 단계를 헷갈리지 않게 한다:

```
- chunk_id: legend_cheomseongdae_seondeok_001_chunk_000
  heritage: 첨성대
  content_role: legend_material
  usage_hint: 전승/이야기 분위기와 미션 소재로 사용. '전해진다 / 이야기된다 / 상상해 볼 수 있다' 로 표현하고 사실로 단정 금지.
  factuality: mixed
  ...
```

### validator 강화 — 추가된 issue codes

| code | severity | 트리거 |
|---|---|---|
| `MISSION_TITLE_TOO_GENERIC` | warning | mission_title 에 "탐험·조사·찾기·관찰·둘러보기" 같은 단어가 들어 있고 장소 고유 키워드가 전혀 없음 |
| `MISSION_NOT_PLACE_SPECIFIC` | warning | mission(title+instruction) 에 장소명이 없거나, RAG 키워드가 있음에도 hit 가 2개 미만 |
| `STORY_NOT_PLACE_SPECIFIC` | warning | story_fragment 에 장소명이 없거나, RAG 키워드가 있음에도 hit 가 2개 미만 |
| `INTRO_TOO_GENERIC` | warning | intro 에 흔한 인사 문구가 있고 장소명 호명 2개 미만이며 닉네임도 없음 |
| `OUTRO_TOO_GENERIC` | warning | outro 에 흔한 마무리 문구가 있고 장소명 호명 2개 미만 |
| `RAG_CONTEXT_UNDERUSED` | warning | RAG 의 motifs / mission_hooks 가 본문 (intro/outro/story/mission) 어디에도 반영되지 않음 |

place specificity 규칙은 **RAG 가 제공된 장소에 대해서만 2-키워드 hit 을 강제** 한다 (RAG 가 없는 장소는 장소명 포함만 확인). 운영 데이터가 적은 MVP 단계에서 false positive 를 막기 위함.

### retry instruction 강화

품질 실패 후 1회 retry 시 user prompt 끝에 붙는 `[REPAIR_INSTRUCTION]` 가 단순 사유 나열에서 8개 항목 리스트로 확장됨:
1. 요청 places 수와 동일한 places 배열
2. mission_title 에 장소 고유 소재 포함, 일반 단어 금지
3. mission_instruction 에 현장 행동 1개 이상
4. story_fragment 에 장소명 + RAG motif/인물 2개 이상
5. fact/legend/symbolic 표현 단계 구분
6. intro 에 사용자 + 장소명 2개 이상
7. outro 에 장소 경험 2개 이상 회상
8. JSON 외 출력 금지

### Spring Boot wire response 영향

**없음.** `intro / missions[] / outro` 형태 그대로. 강화된 품질 정보는 여전히 `StoryIntroDraft.warnings` 와 구조화 로그 (`issue_codes`, `warning_count` 등) 에서만 보인다.

## 향후 TODO

- LLM judge 기반 mission 다양성 / persona 매칭 점수
- factuality 검증을 RAG chunk re-embedding + similarity 비교로 보강
- 평가 fixture 확장 (장소 5개 / 다양한 persona)
- 운영 메트릭: `STORY_QUALITY_*_COUNT` Prometheus gauge
