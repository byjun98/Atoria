# E-book Draft Structure (156)

## 목적

탐방을 마친 사용자의 경험을 **렌더링 가능한 JSON 초안** 으로 변환한다. 이 PR 은 파일 (PDF/EPUB) 을 만들지 않으며, 비동기 Job API 도 만들지 않는다. 만들어진 `EbookDraft` 는 157 의 Job API 가 그대로 받아서 Spring Boot / File 서버로 전달하는 입력이 된다.

## 156 가 만드는 것 / 만들지 않는 것

| 만든다 | 만들지 않는다 |
|---|---|
| `EbookDraft` JSON 구조 (cover + pages + 메타) | PDF / EPUB 파일 |
| page builder, image_prompt builder, draft service | 파일 업로드 / 저장 |
| pure deterministic 조립 (LLM 호출 없음) | OpenAI Chat / Image API 호출 |
| 출처·chunk_id 합산 | DB 스키마 변경 |
| `image_prompt` 문자열 (hint) | 실제 이미지 생성 |
| 단위 테스트 (37개) | API endpoint (157), Job 상태 (158) |

## 단계 분담

```
156 (이 PR)            ─→  EbookDraft JSON
157 (다음)             ─→  POST /artifacts/ebook/jobs (비동기 Job 시작)
158                    ─→  GET  /artifacts/ebook/jobs/{job_id} (상태 조회)
160                    ─→  Spring Boot ↔ AI API 명세 정리
Spring Boot/File 서버  ─→  draft.pages 를 PDF/EPUB 으로 렌더링
```

## 모듈 / 책임

| 위치 | 책임 |
|---|---|
| `app/schemas/ebook_schema.py` | request / response Pydantic 모델, `PageType` Literal |
| `app/services/ebook/ebook_image_prompt_builder.py` | cover / place 페이지의 image prompt **문자열 hint** 만 생성 |
| `app/services/ebook/ebook_page_builder.py` | cover / ToC / prologue / place_story / mission_result / epilogue / sources 단위 페이지 조립 |
| `app/services/ebook/ebook_draft_service.py` | 위를 묶어 `EbookDraft` 생성. mission ↔ place ↔ result 매칭, page_number 부여, source_urls / used_chunk_ids 합산, warnings 기록 |

## Request schema 요약

```
EbookDraftGenerationRequest
├─ user_profile  (nickname / age_group / story_theme / language ...)
├─ places[]      (place_id, place_name, sequence, address, ...)
├─ story_source  (intro / missions[] / outro / source_urls / used_chunk_ids)
├─ mission_results[] (sequence 기준 매칭)
├─ options (include_cover / include_table_of_contents / include_mission_results /
│           include_sources / include_image_prompts / include_incomplete_missions /
│           max_page_body_chars / title_style / ...)
└─ metadata
```

검증:
- `places` 비어 있으면 ValidationError
- `story_source.intro / outro` 빈 문자열 거부
- `story_source.missions` 비어 있으면 ValidationError
- `EbookDraftOptions.max_page_body_chars` 200~4000

## Response schema 요약

```
EbookDraft
├─ draft_id     (uuid 12자리)
├─ title / subtitle
├─ cover (EbookCoverDraft | None)
├─ pages[] (EbookPageDraft)
├─ page_count
├─ source_urls[] (중복 제거)
├─ used_chunk_ids[] (중복 제거)
├─ warnings[]
└─ metadata (request_id / title_style / language)
```

`page_type` ∈
- `cover` — 표지 (image_prompt 포함)
- `table_of_contents` — 목차 (`1. 첨성대\n2. 동궁과월지`)
- `prologue` — `/story/intro` 의 `intro` 사용
- `place_story` — 장소별 본문. `mission.story + mission.description` + (있다면) 사용자 답변/키워드/사진을 결정론적 문장으로 조립
- `mission_result` — 사용자 답변 / 선택 키워드 / 사진 수 별도 페이지 (옵션)
- `epilogue` — `/story/intro` 의 `outro` + 방문 장소명 회상
- `sources` — `source_urls` 중복 제거 + 번호 매김
- `reflection` — 향후 확장용 (현재 미사용)

## /story/intro 결과를 어떻게 사용하는가

152 의 wire response (`intro / missions[] / outro`) 를 그대로 `EbookStorySourceInput` 에 담아 전달한다. 여기에 `places[]` 와 `mission_results[]` 를 함께 전달하면 156 이 다음 매칭을 수행:

- `mission ↔ place` : `sequence` 기준
- `mission_result ↔ place` : `sequence` 기준
- 매칭 실패는 fail 이 아니라 `warnings` 에 기록 (MISSING_MISSION_FOR_PLACE / MISSING_MISSION_RESULT / MISSION_RESULT_UNMATCHED / PLACE_COUNT_MISMATCH)
- `mission` 이 없는 장소도 `place_story` 페이지를 만들고 (`{place_name} 이야기` 제목), 빠뜨리지 않는다.

## mission_result 반영 방식

`place_story` body 끝에 결정론적으로 한 문단 추가:

```
"{place_name}에서 다음과 같이 기록했습니다 — "{user_answer}"
이날 고른 키워드는 별, 창, 돌 였습니다."
```

`PHOTO` 결과의 `photo_urls` 는 page 의 `image_urls` 로 그대로 들어간다 (실제 이미지 출력은 다운스트림 렌더러 책임).

`include_mission_results=true` 면 별도 `mission_result` 페이지가 추가됨. `completed=false` 인 결과는 기본적으로 제외; `include_incomplete_missions=true` 시 포함.

## source_urls / used_chunk_ids 처리

- `story_source.source_urls` 와 각 page 의 `source_urls` 를 모두 union 후 순서 보존 dedup
- `story_source.used_chunk_ids` + 각 page 의 `used_chunk_ids` 도 동일 처리
- `include_sources=true` 이고 union 결과가 비어 있지 않으면 `sources` 페이지를 마지막에 추가
- 비어 있으면 `SOURCE_URLS_EMPTY` warning 기록 후 페이지 생략

## image_prompt 처리

- 실제 이미지 생성 호출 없음. `EbookImagePromptBuilder` 가 만드는 건 **다운스트림 렌더러용 문자열 hint**
- 공통 안전 가드:
  - `따뜻한 동화책 삽화 스타일, 가족 친화적이고 부드러운 색감`
  - `실제 인물 묘사는 피하고 뒷모습이나 상징적인 실루엣으로 표현`
  - `실제 문화재의 형태를 과도하게 왜곡하지 않음`
  - `어린이를 대상으로 하므로 공포·폭력 표현 금지`
- 사용자 사진이 있는 경우 인물 묘사 대신 `사용자가 남긴 사진을 배치할 영역` 표현으로만 언급
- `include_image_prompts=false` 면 cover.image_prompt / page.image_prompt 가 모두 None

## 157 Job API 에서의 재사용

```python
from app.services.ebook import EbookDraftService

service = EbookDraftService()
draft = service.generate_draft(request)  # ← 동기 / pure
# 157 은 위 호출을 background task / Celery 등으로 감싸고
# job_id 와 상태를 별도로 관리. draft 는 결과 payload.
```

`EbookDraft` 는 Pydantic 모델이라 `model_dump()` / `model_dump_json()` 으로 직렬화 가능. Spring Boot / File 서버는 이 JSON 을 받아 PDF/EPUB 로 렌더링.

## 156-quality-pass: 한국어 문장 / image_prompt / source trace 보강

운영 draft 출력에서 잡힌 4가지 회귀를 같은 156 작업 안에서 후속 강화함.

### 1) 한국어 조사 helper

`app/services/ebook/_josa.py` — 받침 검출 + 조사 자동 선택. 파이썬 표준 라이브러리만 사용 (형태소 분석기 없음).

| 함수 | 비고 |
|---|---|
| `has_jongseong(text)` | 마지막 의미 있는 글자의 받침 여부 |
| `append_josa(text, pair)` / `with_josa` | `"은/는" / "이/가" / "을/를" / "와/과" / "으로/로"` 자동, `"에서" / "에"` 는 받침 무관 + idempotent |

회귀 가드:
- ❌ `민준와(과) 가족` → ✅ `민준 가족` (cover author_label) 또는 `민준과 가족이 함께 탐방한…` (image prompt)
- ❌ `첨성대에서에서` → ✅ `첨성대에서` (`append_josa("첨성대에서", "에서") == "첨성대에서"`)
- ❌ `별, 창, 돌 였습니다` → ✅ `함께 고른 키워드는 별, 창, 돌입니다.`

### 2) place_story body 편집 품질

이전: `mission.story` + `미션 안내: ...` + 라벨식 결과 라인.
지금: 책 원고 톤의 3문단 구조.

```
{mission.story}

이곳에서의 미션은 {mission.description}는 것입니다.

{nickname}은 {place_name}에서 “{user_answer}”라고 기록했습니다. 함께 고른 키워드는 별, 창, 돌입니다. 사진 1장도 이 페이지에 함께 남았습니다.
```

규칙:
- `미션 안내:` 라벨 금지 → "이곳에서의 미션은 …는 것입니다." 자연 문장
- `completed=False` 인 결과는 성공 기록처럼 쓰지 않는다 (별도 문장 또는 missing 처리로 분기)
- `selected_keywords` 가 비어 있으면 키워드 문장 자체 생략, `user_answer` 가 비어 있으면 사용자 한 줄 문장 생략, `photo_urls` 가 비어 있으면 사진 문장 생략

### 3) image_prompt — visual keyword 추출

`mission.title` 전체를 괄호 안에 박아 넣지 않고, 우선순위 기반 `extract_visual_keywords` 로 시각적 단서만 추린다.

| 우선순위 | 출처 |
|---|---|
| 1 | `mission_result.selected_keywords` (단음절 허용 — 사용자 직접 신호) |
| 2 | `mission.description` 의 한글 토큰 |
| 3 | `mission.title` 의 한글 토큰 |
| 4 | `place.place_name` (항상 맨 앞에 1회) |

불용어로 거르는 단어 (예시): `미션·이야기·문화재·유적·여행·체험·찾기·탐험·관찰·둘러보기·따라·걸으며·가족·함께·주변·기록`. 추가로 `…세요 / …해요 / …어요 / …아요` 로 끝나는 토큰은 동사 활용형으로 보고 제거 (`둘러보세요` 등).

회귀 가드:
- ❌ `핵심 요소(남쪽 창이 바라보는 하늘 찾기)` → ✅ `첨성대을(를) 중심으로 한 장면 — 첨성대, 별, 창, 돌, 남쪽, 하늘`
- 동궁과월지 미션이면 `월지 / 누각 / 그림자 / 연못` 중 최소 2개 이상 노출

### 4) page-level source trace 우선순위

| 우선순위 | 출처 |
|---|---|
| 1 | `EbookMissionInput.used_chunk_ids / source_urls` (mission 단위 trace — schema 신규 optional 필드) |
| 2 | `EbookStorySourceInput.used_chunk_ids / source_urls` (전체 fallback) |

`EbookDraftService` 가 place_story 페이지 생성 시 위 우선순위로 `page.used_chunk_ids` / `page.source_urls` 를 채운다. `sources` 페이지 (마지막 페이지) 는 그대로 전체 union 사용.

### 5) mission_result missing 정책

- `place_story` page 의 `metadata.mission_result_status` ∈ `completed / incomplete / missing` 로 항상 노출 → 다운스트림 렌더러가 표지/스타일 분기 가능
- 기본값으로는 body 에 missing 안내 문구를 넣지 않는다 (warnings 와 metadata 만)
- `EbookDraftOptions.include_missing_result_note_in_body=True` 일 때만 body 에 `이 장소의 미션 기록은 아직 남겨지지 않았습니다.` 한 문장 추가
- `MISSING_MISSION_RESULT` warning 은 그대로 유지

## 157-storybook-pass: MVP "이야기책" 톤 정책

운영 검토 결과 E-book 은 미션 수행 결과 보고서가 아니라 사용자의 탐방 경험을 자연스럽게 엮은 이야기책으로 확정. 본 패스에서 다음을 강제:

- `place_story.body` = 1문단 `mission.story` (story_content) + (사진 있을 때만) 1문단 자연스러운 사진 언급. 그 외 결과 보고식 표현 모두 금지.
- `mission.description` 은 본문에 직접 들어가지 않음 (앱 진행용 데이터일 뿐).
- 본문 회귀 차단 어구: `미션 / 미션 안내 / 오늘의 미션 / 이곳에서의 미션 / 미션을 완료했습니다 / 기록했습니다 / 선택했습니다 / 함께 고른 키워드 / 내가 남긴 한 줄 / 사진 N장이 함께 남았습니다 / 보세요는 것입니다`.
- `_compose_mission_paragraph` / `_compose_result_paragraph` 헬퍼 제거. 대체 헬퍼 `_compose_photo_paragraph` 만 유지.
- 157 wire 변환에서 `mission_result` 페이지는 항상 제외 (`include_mission_results=False` 강제).

## 향후 확장 TODO

- LLM 기반 page 본문 보강 (현재는 결정론적 조립만)
- `reflection` 페이지 — 사용자가 가장 인상 깊었던 미션 회상
- `mission_result` 페이지에 사진 캡션 자동 생성
- 다국어 번역 (`language="en"` 등)
- 표지 / 페이지별 layout hint 추가 (그리드 / 풀블리드 등)
