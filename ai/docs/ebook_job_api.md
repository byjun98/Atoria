# /artifacts/ebook/jobs (157)

## 목적

API.md 의 `POST /artifacts/ebook/jobs` 명세를 그대로 유지하면서, 응답을 156 의 `EbookDraftService` 가 deterministic 하게 만든 콘텐츠로 채운다. 이전 스텁(`app/api/v1/artifacts.py`) 이 자체 페이지를 조립하던 부분을 156 로 위임했다.

## API.md 와의 관계

**wire schema 0% 변경 / 0% 추가**:

- path: `POST /artifacts/ebook/jobs` (이전과 동일)
- request: `EbookJobRequest` 그대로 — `story_id / user_id / story / chapters` + `chapters[].user_result.{image_url, choice}`
- response: `EbookJobResponse` 그대로 — `success / data / error / timestamp` 봉투 + `data.ebook_content.{meta, cover, pages[]}`
- enum: `PageType ∈ COVER / INTRO / CHAPTER / OUTRO / BACK_COVER`, `PageLayout ∈ COVER / TEXT_ONLY / IMAGE_TOP_TEXT_BOTTOM / TEXT_WITH_QUOTE / BACK_COVER`
- **`job_id / status / draft_id / page_type / body / image_prompt / used_chunk_ids / source_urls / warnings / mission_result_status` 같은 내부 필드는 외부 응답에 절대 노출되지 않는다.**

API.md 가 `job_id` / `status` 없이 `ebook_content` 를 즉시 반환하는 구조이므로 157 은 **동기 처리 API** 다. 비동기 Job 상태 조회 API 가 필요하면 158 에서 별도 명세를 합의해야 한다.

## 모듈 / 책임

| 위치 | 책임 |
|---|---|
| `app/api/v1/artifacts.py` | router. `EbookJobService` 의존성 주입. ValueError → 400, 그 외 → 500 (`EBOOK_JOB_FAILED`) |
| `app/services/ebook/ebook_job_service.py` | adapter ↔ draft service orchestration |
| `app/services/ebook/ebook_job_adapter.py` | wire ↔ 156 internal 변환 (양방향) |
| `app/schemas/story.py` | wire schema 그대로 (변경 없음) |
| `app/services/ebook/ebook_draft_service.py` | 156 의 deterministic 조립 (변경 없음) |

## 내부 흐름

```
POST /artifacts/ebook/jobs (EbookJobRequest)
    ▼
EbookJobAdapter.to_draft_request
    ▼
EbookDraftService.generate_draft (156)
    ▼
EbookJobAdapter.to_ebook_content
    ▼
EbookJobResponse (success / data.ebook_content / error / timestamp)
```

## 변환 규칙

### Wire request → 156 internal request

| API.md 필드 | 156 필드 |
|---|---|
| `story.protagonist_info.people_information[0].name` | `EbookUserProfile.nickname` |
| `people_information[*].tendency` (첫 비어있지 않은 값) | `persona` |
| `people_information.length > 1` | `companion_type="family"` |
| 최연소 `age` ≤12 / ≤19 / 그 외 | `age_group="child" / "teen" / "family"-or-"adult"` |
| `story.intro` | `EbookStorySourceInput.intro` |
| `story.outro` | `EbookStorySourceInput.outro` |
| `chapters[].sequence` | places·missions 의 `sequence` |
| `chapters[].place_id` | `places[].place_id` (str) |
| `chapters[].place_name` | `places[].place_name`, `missions[].place_name` |
| `chapters[].place_address` | `places[].address` |
| (없음) | `places[].category = "문화유산"` 기본값 |
| `chapters[].mission_title` | `missions[].title` |
| `chapters[].mission_description` | `missions[].description` |
| `chapters[].mission_type` | `missions[].type` (그대로 — `PHOTO/CHOICE/QUIZ/ACTION`) |
| `chapters[].story_content` | `missions[].story` |
| `chapters[].user_result.image_url` (있을 때) | `mission_results[].photo_urls=[image_url]`, `completed=True` |
| `chapters[].user_result.choice` (있을 때) | `mission_results[].user_answer`, `selected_keywords=[choice]`, `completed=True` |
| 둘 다 없음 | `mission_results` 자체 생략 |

옵션은 wire 응답에 `table_of_contents / mission_result / sources` 페이지가 없으므로 156 옵션을 모두 끔 (`include_table_of_contents=False`, `include_mission_results=False`, `include_sources=False`). `include_image_prompts` 는 `True` 로 켜서 cover thumbnail_hint 채움.

### 156 draft → API.md response

| 156 page_type | wire `type` | wire `layout` 결정 |
|---|---|---|
| `cover` | `COVER` | `COVER` |
| `prologue` | `INTRO` | `TEXT_ONLY` |
| `place_story` | `CHAPTER` | `choice` 있음 → `TEXT_WITH_QUOTE` / `image_url` 있음 → `IMAGE_TOP_TEXT_BOTTOM` / 둘 다 없음 → `TEXT_ONLY` |
| `epilogue` | `OUTRO` | `TEXT_ONLY` |
| `table_of_contents` / `mission_result` / `sources` / `reflection` | (제외) | API.md enum 에 없으므로 외부 응답에서 필터됨 |
| (자동 부착) | `BACK_COVER` | `BACK_COVER` |

`CHAPTER` 페이지의 chapter 매칭은 156 `EbookPageDraft.place_name` ↔ `chapters[].place_name` 으로 (place_name 은 API.md request 안에서 unique 가정).

`CHAPTER` 페이지의 `image_url` / `caption` / `quote` 는 매칭된 `chapter.user_result` 에서 직접 가져옴 (156 draft 의 image_urls / mission_result_summary 가 아니라 wire 원본을 그대로 반사 — 무손실).

`meta.title` = `request.story.title`, `meta.author` = `people_information[].name` join `", "`, `meta.subtitle` = `"{author}의 경주 탐험 이야기"`, `meta.page_count` = 변환된 wire pages 길이, `meta.language = "ko"`.

`cover.title` = `request.story.title`, `cover.background_color = "#F5E6D3"` (고정), `cover.thumbnail_hint` = 156 `cover.image_prompt` 가 있으면 그걸, 없으면 첫 장소 기반 fallback 문구.

`BACK_COVER` 는 항상 마지막에 한 장 자동 추가 — `title="{author}의 모험은 계속됩니다"`, `subtitle="Atoria와 함께한 경주 여행"`, `text="방문한 장소: 첨성대 · 불국사 · 석굴암"`.

## 158 와의 차이

- 157 (이 PR): 동기. 한 호출로 `ebook_content` 즉시 반환. API.md 현재 명세 그대로
- 158 (예정): 비동기 Job 상태 조회. **별도 명세 합의 필요** — `job_id` / `status` 같은 필드는 API.md 에 추가될 때만 응답에 넣을 수 있음. 이 PR 머지 후에도 외부 wire schema 는 변하지 않음

## 동작 검증

```bash
# 라우트 등록 확인
docker compose -p atoria exec ai python -c \
  "from app.main import app; print([(r.path, sorted(r.methods)) for r in app.routes if 'artifact' in r.path or 'ebook' in r.path])"

# 실제 호출 — ai 컨테이너 안에서 urllib (호스트 포트 publish 안 되어 있어도 OK)
docker compose -p atoria exec ai python -c "
import json, urllib.request
payload = { ... }   # docs 본문의 예시 또는 PR 본문 참고
req = urllib.request.Request('http://localhost:8000/artifacts/ebook/jobs',
    data=json.dumps(payload, ensure_ascii=False).encode('utf-8'),
    headers={'Content-Type': 'application/json'}, method='POST')
print(urllib.request.urlopen(req, timeout=60).read().decode('utf-8'))
"
```

응답 검증 포인트:
- `success=true`, `data.story_id == request.story_id`
- `data.ebook_content.meta.title == request.story.title`
- `pages[*].type ∈ {COVER, INTRO, CHAPTER, OUTRO, BACK_COVER}`
- PHOTO chapter → `IMAGE_TOP_TEXT_BOTTOM` + `image_url` 노출 / `caption` 자동
- CHOICE chapter → `TEXT_WITH_QUOTE` + `quote` 노출
- `draft_id / page_type / body / image_prompt / used_chunk_ids / source_urls / warnings` 등 내부 필드 응답에 없음

## MVP 정책: "이야기책" 톤 (157-storybook-pass)

운영 검토 결과, E-book 은 **미션 수행 결과 보고서가 아니라 사용자의 탐방 경험을 자연스럽게 엮은 이야기책** 으로 확정됨. wire schema 는 그대로 유지하고 본문 / 페이지 구성 정책만 갱신:

- 미션은 **PHOTO 만 사용**. `chapters[].mission_type` 은 명세상 유지하지만 본문 생성은 PHOTO 경로 기준으로 작성.
- `chapters[].user_result.image_url` 만 chapter `image_url` / `image_urls` 로 흐른다.
- `chapters[].user_result.choice` 는 **현재 어떤 경로로도 본문에 노출되지 않는다** — `user_answer / selected_keywords / quote` 어디에도 매핑하지 않음. wire response 의 `quote` 는 항상 `null`.
- chapter `layout` ∈ `IMAGE_TOP_TEXT_BOTTOM` (사진 있음) 또는 `TEXT_ONLY` (사진 없음). **`TEXT_WITH_QUOTE` 미사용**.
- `mission_title` / `mission_description` 은 앱 진행용 데이터. **본문에 직접 노출하지 않음** (chapter `title` 에는 들어가지만 `text` 본문에는 description 어구가 들어가지 않음).
- `mission_result` 페이지 미생성 (`include_mission_results=False` 강제).

### 본문에서 절대 사용하지 않는 표현

`미션`, `미션 안내`, `오늘의 미션은 다음과 같습니다`, `이곳에서의 미션`, `미션을 완료했습니다`, `기록했습니다`, `선택했습니다`, `함께 고른 키워드`, `내가 남긴 한 줄`, `사진 N장이 함께 남았습니다`, `보세요는 것입니다`. 위 어구는 회귀 가드 테스트 (`test_response_text_is_storybook_tone`) 로 wire 응답 raw 에서 영구 차단.

### 사진 문장 규칙

`photo_urls` 가 있을 때만 자연스러운 한 줄 추가, 없으면 사진 관련 문장 자체 미출력.

```
민준은 첨성대에서 만난 한 장면을 사진으로 담았습니다. 그 사진은 이 순간의 분위기를 조용히 간직하고 있습니다.
```

`사진 1장이 함께 남았습니다` 같은 결과 보고식 표현은 금지.

### caption / cover.title 정책

- 사진이 있는 chapter caption: `"{place_name}에서 남긴 여행의 한 장면"`
- `meta.title == cover.title == pages[0].title (COVER) == request.story.title` 3종 일치 보장 (회귀 가드 테스트 `test_cover_page_title_matches_request_story_title`).

## 158: LLM 기반 이야기책 원고 생성 + deterministic fallback

운영 검토 결과 deterministic 조립만으로는 본문 품질이 충분치 않아, 같은 wire schema 를 유지한 채 **내부 처리 경로를 LLM 으로 전환**. API.md 의 path / request / response 변수명 / enum / 봉투 (`success / data / error / timestamp`) 모두 그대로 — `job_id / status` 추가 없음.

### 처리 흐름

```
POST /artifacts/ebook/jobs (EbookJobRequest)
    ▼
EbookJobService.create_ebook_content
    ├─ EBOOK_NARRATIVE_USE_LLM=True (default)
    │     ▼
    │   EbookNarrativeService.generate_narrative
    │     ├─ EbookNarrativePromptBuilder.build_messages
    │     ├─ OpenAIChatClient.create_json_chat_completion (json_object mode)
    │     ├─ JSON parse → EbookNarrativeDraft
    │     ├─ chapter sequence 매칭 검증
    │     ├─ 금지 어구 검사 (NARRATIVE_FORBIDDEN_PHRASES + CHAPTER title 추가 금지)
    │     └─ 위반 시 1회 retry (REPAIR_INSTRUCTION 첨부) → 최종 실패면 raise
    │     ▼ (성공)
    │   EbookJobAdapter.to_ebook_content_from_narrative
    │     ▼
    │   EbookJobResponse 반환
    │
    └─ NarrativeGenerationFailed / 그 외 예외
        ▼
      EbookJobAdapter.to_draft_request → EbookDraftService.generate_draft (156)
        ▼
      EbookJobAdapter.to_ebook_content (deterministic 본문)
        ▼
      EbookJobResponse 반환  ← API 는 200 유지, wire schema 동일
```

### 모듈 / 책임

| 위치 | 책임 |
|---|---|
| `app/services/ebook/ebook_narrative_prompt_builder.py` | system + user 프롬프트, 금지 어구 상수, repair 메시지 |
| `app/services/ebook/ebook_narrative_service.py` | 호출 / 파싱 / 검증 / retry / `EbookNarrativeDraft` 모델 / `NarrativeGenerationFailed` |
| `app/services/ebook/ebook_job_adapter.py` | `to_ebook_content_from_narrative` 신설 (narrative draft → wire pages) |
| `app/services/ebook/ebook_job_service.py` | LLM 우선 + deterministic fallback orchestration |

### 프롬프트 정책 (요약)

- 결과물은 **이야기책 한 권** — 미션 보고서 아님
- 전체 여행을 관통하는 상징 1개 (별빛 / 달빛 / 시간 여행 지도 등)를 intro 에서 제시 → chapter 에서 이어받기 → outro 에서 회수
- `mission_title` / `mission_description` 은 LLM 입력 payload 에는 보내지만 **본문에 직접 박지 않도록** 시스템 프롬프트와 user payload 모두에 명시 (`_internal_mission_title_for_reference_only` 라벨)
- `user_result.image_url` 만 사진 단서로 사용. `choice` 는 MVP 미사용
- 금지 어구 (시스템 프롬프트에 인용): `미션 / 미션 안내 / 오늘의 미션 / 이곳에서의 미션 / 완료했습니다 / 기록했습니다 / 선택했습니다 / 함께 고른 키워드 / 사진 1장 / 사진 N장 / 보세요는 것입니다 / 하세요는 것입니다 / 을(를) / 와(과) / 에서에서 / 입니다.입니다`
- CHAPTER title 추가 금지: `찾기 / 포착하기 / 선택하라 / 조사 / 탐험 / 미션`

### 검증 / Retry 정책

응답 후 `_collect_violations` 가 모든 텍스트 필드 (intro/chapter/outro/back_cover/cover_*/thumbnail_hint) 와 모든 chapter title 을 스캔해 위반 어구 검출. 위반이 있으면:

1. `EBOOK_NARRATIVE_MAX_RETRIES` (default 1) 회 retry — `[REPAIR_INSTRUCTION]` 블록을 user prompt 끝에 붙여 위반 사유 명시
2. 최종 실패 → `NarrativeGenerationFailed("EBOOK_LLM_FORBIDDEN_PHRASE", ..., violations)`
3. 다른 코드: `EBOOK_LLM_API_ERROR / EBOOK_LLM_INVALID_JSON / EBOOK_LLM_SCHEMA_VALIDATION_ERROR / EBOOK_LLM_CHAPTER_MISMATCH / EBOOK_LLM_EMPTY_RESPONSE`

### Fallback 정책

`EbookJobService.create_ebook_content` 가 위 예외 (또는 그 외 어떤 예외든) 를 잡으면 156 deterministic 경로로 자동 전환. **API 는 200 유지**, wire schema 동일, fallback 여부는 외부 응답에 새 필드로 노출하지 않음 (구조화 로그 `ebook_narrative_fallback` 으로만 추적).

### 환경변수

```env
EBOOK_NARRATIVE_USE_LLM=true        # false 로 두면 항상 deterministic 경로
EBOOK_NARRATIVE_TEMPERATURE=0.7
EBOOK_NARRATIVE_MAX_RETRIES=1
```

`OPENAI_API_KEY` 가 비어 있으면 `OpenAIChatClient()` 가 ValueError → 자동 fallback.

## 158-v2: scene-style chapters + symbolic-object continuity

운영 응답에서 챕터 본문이 여전히 "경상북도 경주시 인왕동에 자리한 첨성대는…" 같은 안내문 톤으로 시작하고, 전체를 관통하는 서사 장치가 약하다는 회귀가 잡혀 같은 158 작업 안에서 narrative prompt 와 검증을 다시 강화. **API.md wire schema 0 변경 / fallback 정책 동일.**

### 프롬프트 변경

- system prompt 에 신규 섹션 2개 추가
  - `[STORYBOOK_SCENE_STYLE]` — chapter 첫 문장은 인물 행동 / 감각 / 시선 / 분위기로 시작. "위치한 / 자리한 / 알려져 있습니다 / 경상북도 / 경주시" 같은 안내문 어휘 금지. 좋은/나쁜 시작 예시 인라인 인용
  - `[SYMBOLIC_OBJECT_RULE]` — 전체 책에 하나의 상징 오브젝트가 intro → 모든 chapter → outro/back_cover 까지 자연스럽게 이어진다. 후보 8종 인용 (`별빛 지도 / 작은 등불 / 기억 조각 / 시간 여행 편지 / 달빛 조각 / 오래된 나침반 / 신라의 작은 열쇠 / 연꽃등`)
- user prompt 에 `[NARRATIVE_QUALITY_REQUIREMENTS]` 추가 — 첫 문장 장면 시작 / protagonist name 등장 / symbolic_object 모든 chapter 노출 / mission_title·mission_description 본문 노출 금지
- output JSON schema 에 `symbolic_object` 필드 추가 (**내부 검증용. wire response 에는 노출 안 됨**)
- repair instruction 7항목으로 확장

### `EbookNarrativeDraft` 변경

`symbolic_object: str | None = None` 필드 추가. **wire response 에 노출되지 않음** — `to_ebook_content_from_narrative` 가 `symbolic_object` 를 외부 페이지로 직접 옮기지 않음. 회귀 가드 테스트 (`test_llm_path_response_does_not_expose_symbolic_object_field`) 가 wire raw text 에 `symbolic_object` 키가 새지 않는지 영구 검증.

### validation 추가 (모두 retry 트리거 → 최종 실패면 fallback)

| 신규 violation 코드 | 트리거 |
|---|---|
| `EBOOK_LLM_EXPOSITION_OPENING` | chapter text 첫 문장이 안내문 어휘 (`위치한 / 자리한 / 알려져 있습니다 / 경상북도 / 경주시 / 통일신라의 / 신라 시대의 …`) 포함, 또는 `{place_name}는 / {place_name}은` 패턴 |
| `EBOOK_LLM_SYMBOLIC_OBJECT_MISSING` | `symbolic_object` 필드 비어 있음 |
| `EBOOK_LLM_SYMBOLIC_OBJECT_UNDERUSED` | intro / 모든 chapter / (outro 또는 back_cover) 중 어디에 라도 `symbolic_object` 미노출 |

기존 검사 (`NARRATIVE_FORBIDDEN_PHRASES`, `CHAPTER_TITLE_FORBIDDEN_TOKENS`, `EBOOK_LLM_CHAPTER_MISMATCH` 등) 그대로 유지.

### Fallback 보장

위 신규 violation 도 모두 `NarrativeGenerationFailed` 로 분류 → `EbookJobService` 가 catch → 156 deterministic 경로로 자동 전환. **API 200 유지 / wire schema 동일 / fallback 여부 외부 노출 없음**.

## 158-v3: fairy-tale style policy (구연동화 톤 + 자유 보조 인물 + 대화 + 질문)

운영 응답이 여전히 "장소 설명을 이야기처럼 연결한 글" 톤이어서, 진짜 어린이 이야기책 형식을 강제하기 위한 가드 한 층 더 추가. **API.md wire schema 0 변경 / fallback 정책 동일.**

### 프롬프트 변경

system prompt 신규 섹션 7개:
- `[FAIRY_TALE_STYLE_RULE]` — 구연동화 어휘 ("옛날 옛적에 / 어느 날 / 그러자"), 짧고 분명한 문장, 시간 순서, 약한 판타지 한도, 따뜻한 교훈
- `[SUPPORTING_CHARACTER_CREATION_RULE]` — 보조 인물을 **고정 목록에서 강제로 고르지 않고 자유롭게 만든다**. "반드시 다음 중 하나를 선택" 같은 강제 어구 회귀 차단. 실존 역사 인물의 실제 증언처럼 단정 금지 — "전해진단다 / 이야기된단다" 완화 표현 의무
- `[STORY_CONFLICT_RULE]` — intro 에 단순한 질문 1개 → 각 chapter 에서 답을 조금씩 → outro 에서 회수
- `[REPETITION_AND_RHYTHM_RULE]` — 핵심 상징·문장 가벼운 2~3회 반복 (기계적 반복 금지)
- `[EMOTION_RULE]` — 주인공 감정 (놀람 / 궁금함 / 기쁨 / 따뜻함 등) 직접 표현, 각 chapter 최소 1회
- `[DIALOGUE_RULE]` — 한국어 둥근따옴표 “ ” 사용. 1~3문장 짧은 대화, 각 chapter 최소 1회. 사극·예언자 말투 금지
- `[CHAPTER_STRUCTURE_RULE]` — 1문단 장면 진입 / 2문단 질문·대화 / 3문단 사진·감정 / 4문단(선택) 상징 오브젝트 변화

user prompt 신규 섹션 2개:
- `[FAIRY_TALE_REQUIREMENTS]` — 구연동화 / 짧은 문장 / 시간 순서 / 보조 인물 / 대화 1회 / 상징 반복 / 감정 / 질문 회수 / 따뜻한 교훈
- `[DO_NOT_USE_FIXED_CHARACTER_LIST]` — 고정 목록 강제 차단, "별빛 요정 / 지도 속 목소리 / 노스님" 같은 같은 캐릭터만 등장 금지

output JSON schema 신규 필드 (모두 **내부 검증용 — wire response 미노출**):
- `supporting_character: str | None`
- `story_question: str | None`

repair instruction 12항목으로 확장 — 보조 인물 자유 생성 / 대화 / 3문단 / 질문 회수 / 감정 / 구연동화 어휘 / 안내문 회피 / mission_title 미노출 / 금지 어구 / JSON-only.

### `EbookNarrativeDraft` 변경

`supporting_character / story_question` 필드 추가. 회귀 가드 테스트 (`test_llm_path_response_does_not_expose_internal_fairy_tale_fields`) 가 wire raw text 에 `supporting_character / story_question / symbolic_object` 키 누수 없는지 영구 검증.

### validation 추가

| 신규 violation 코드 | 트리거 |
|---|---|
| `EBOOK_LLM_DIALOGUE_MISSING` | chapter text 에 한국어 둥근따옴표 대화 “ ” 한 쌍 이상 없음 |
| `EBOOK_LLM_CHAPTER_TOO_FLAT` | chapter text 가 `\n\n` 기준 3문단 미만 |
| `EBOOK_LLM_SUPPORTING_CHARACTER_MISSING` | `supporting_character` 필드 빈값 |
| `EBOOK_LLM_STORY_QUESTION_MISSING` | `story_question` 필드 빈값 |

기존 검사 (`NARRATIVE_FORBIDDEN_PHRASES`, `CHAPTER_TITLE_FORBIDDEN_TOKENS`, `EBOOK_LLM_EXPOSITION_OPENING`, `EBOOK_LLM_SYMBOLIC_OBJECT_*`, `EBOOK_LLM_CHAPTER_MISMATCH` 등) 그대로 유지.

### 정책 / 안전 가드 (변경 없음)

- 역사 사실을 왜곡하지 않음. story_content 범위 안에서만 설명.
- 실존 역사 인물이 실제 한 말처럼 표현 금지. 보조 인물도 "전해진단다 / 이야기된단다" 완화.
- 약한 판타지 장치만 허용. 전투 / 괴물 / 마법 대결 금지.
- API 200 유지, wire schema 동일, fallback 여부 외부 비노출.

## 158-v4: continuous-story policy (옴니버스 방지 + 인과 연결)

이전까지의 패스로도 챕터가 여전히 옴니버스 (도착→설명→오브젝트 변화) 처럼 보이는 회귀가 잡혀, 같은 158 작업 안에서 **장소 간 인과 연결** 강제 가드 한 층 더 추가. **API.md wire schema 0 변경 / fallback 정책 동일.**

### 프롬프트 변경

system prompt 신규 섹션 4개:
- `[CONTINUOUS_STORY_RULE]` — 장소별 옴니버스 금지. intro 의 목표/질문/약속 → chapter 1 첫 단서 → chapter 2+ 가 앞 단서 때문에 이어짐 → 마지막 chapter 에서 단서 통합 → outro 회수
- `[CHAPTER_CAUSAL_LINK_RULE]` — 각 chapter 는 이전 chapter 의 단서·감정·질문·오브젝트 변화·이동 이유 중 최소 하나를 이어받음. 연결 문장 4종 인용
- `[PLOT_PROGRESS_RULE]` — INTRO / CHAPTER 1~N / OUTRO / BACK_COVER 단계별 책임 명시
- `[ANTI_OMNIBUS_RULE]` — "이곳은 단순한 OOO 이 아님" / "기억 조각은 OOO 을 담았습니다" 같은 반복 패턴 인용 + 각 chapter 사건 기능 다양화 (문제 발견 / 첫 단서 해석 / 방향 전환 / 상상 확장 / 의미 완성)

user prompt 신규 섹션 2개:
- `[CONTINUITY_REQUIREMENTS]` — 옴니버스 금지, `story_arc` 한 줄 + `continuity_notes` 배열 (≥ chapters-1) 작성 강제
- `[PLACE_SEQUENCE_USAGE]` — request.chapters 순서가 이야기 이동 경로

output JSON schema 신규 필드 (모두 **내부 검증용 — wire response 미노출**):
- `story_arc: str` — 전체 책 한 줄 서사 요약
- `continuity_notes: list[str]` — chapter 간 인과 연결 메모 배열

repair instruction 15항목으로 확장 — 독립 에피소드 금지 / 이전 단서 이어받기 / chapter 마지막 힌트 / story_arc + continuity_notes 강제.

### `EbookNarrativeDraft` 변경

`story_arc / continuity_notes` 필드 추가. 회귀 가드 테스트 (`test_llm_path_response_does_not_expose_internal_fairy_tale_fields`) 가 wire raw text 에 `story_arc / continuity_notes / supporting_character / story_question / symbolic_object` 모두 키 누수 없는지 영구 검증.

### validation 추가

| 신규 violation 코드 | 트리거 |
|---|---|
| `EBOOK_LLM_CONTINUITY_MISSING` | chapters ≥ 2 인데 `continuity_notes` 비어있지 않은 항목이 chapters-1 미만 |
| `EBOOK_LLM_CHAPTER_LINK_MISSING` | chapter 2 이후 본문에 `symbolic_object` 도 없고 연결 어구 (`앞서 / 그 빛 / 그 조각 / 이어 / 따라 / 가리킨 / 이끌 / 첫 번째 …`) 도 하나 없음 |
| `EBOOK_LLM_REPETITIVE_CHAPTER_PATTERN` | chapters ≥ 3 일 때 chapter 마지막 문장의 끝 6글자가 3회 이상 동일 |

3종 모두 retry 트리거 → 최종 실패 시 156 deterministic fallback. **API 200 유지 / wire schema 동일 / fallback 여부 외부 비노출.**

기존 검사 (forbidden phrases / chapter title / exposition opening / symbolic_object / dialogue / paragraph count / supporting_character / story_question / chapter sequence mismatch) 그대로 유지.

## 의도적으로 제외 (후속 이슈)

- 158 Job 상태 조회 / 비동기 처리
- PDF / EPUB 생성 / 파일 저장 (Spring Boot or File 서버 책임)
- 이미지 생성 API 호출
- OpenAI Chat / Embedding 호출, RAG 검색 호출
- DB 스키마 변경 / Spring Boot 명세 변경
- LangChain 사용
