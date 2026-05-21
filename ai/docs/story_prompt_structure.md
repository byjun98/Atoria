# Story Prompt Structure (151)

## 목적

152 (`/story/intro`) 와 153 (`/story/update`) 에서 OpenAI Chat Completion 을 호출할 때 사용할 **입력/출력 계약 + 프롬프트 조립 구조**를 정의한다. 이 단계에서 LLM 을 호출하지 않으며, RAG 검색 / DB / FastAPI router 도 손대지 않는다.

## 이 단계가 만드는 것

| Layer | 위치 | 책임 |
|---|---|---|
| Pydantic 스키마 | `app/schemas/story_schema.py` | 요청/응답 계약 (`StoryIntroGenerationRequest`, `StoryIntroDraft`, RAG context DTO) |
| RAG context formatter | `app/services/story/rag_context_formatter.py` | 149 검색 결과를 LLM 프롬프트용 문자열로 그룹화 (장소별 / 역할별) |
| Prompt templates | `app/services/story/story_prompt_templates.py` | system / user 프롬프트 + JSON 스키마 instruction (versioned) |
| Prompt builder | `app/services/story/story_prompt_builder.py` | 위 셋을 합쳐 `[system, user]` messages list 로 반환 |

## 149 RAG 검색 결과 → 151 프롬프트 입력

149 의 `RagSearchResultItem` 을 152 가 호출 직전에 `RagContextItem` 으로 변환해 `RagContextByPlace` 에 넣는다. 분류는 `content_role` 기준:

| content_role | 들어가는 곳 | 사용 규칙 |
|---|---|---|
| `fact_context` | `RagContextByPlace.fact_contexts` | 역사적 사실 근거. 단정 어조 가능. |
| `legend_material` | `RagContextByPlace.story_materials` | 설화/전승. "전해진다 / 이야기된다" 로 완화. |
| `symbolic_material` | `RagContextByPlace.symbolic_materials` | 상징적 해석. "상징적으로 해석할 수 있다" 로만 표현. 실제 설화로 단정 금지. |

## StoryIntroGenerationRequest 구조 요약

- `user_profile`: 닉네임, persona, companion_type, story_theme/tone, age_group, language
- `places: list[CoursePlaceInput]` — 코스 장소 (sequence ≥ 1, place_name 필수)
- `options`: `mission_count_per_place` 1~3, `max_*_chars` 200~2000, `use_rag`, `output_format="json"`
- `rag_contexts: list[RagContextByPlace]` — 장소별 RAG 묶음
- `metadata: dict`

## StoryIntroDraft 구조 요약

LLM 이 반환해야 하는 JSON 의 Pydantic 매핑.

```
StoryIntroDraft
├─ title, intro, outro
├─ places: list[PlaceStoryDraft]
│   ├─ sequence, place_id, place_name
│   ├─ story_fragment
│   ├─ mission: MissionDraft (mission_type ∈ observation|photo|quiz|imagination|route)
│   ├─ used_chunk_ids[]
│   └─ source_urls[]
├─ used_chunk_ids[]
├─ source_urls[]
├─ prompt_version  (default "story_intro_v1")
└─ warnings[]
```

## RAG_CONTEXT 블록 구성 방식

`RagContextFormatter.format_contexts_by_place(...)` 출력 예시:

```
[장소: 첨성대]

<사실 근거 fact_context>
- chunk_id: heritage-cheomseongdae-encykorea-001_chunk_000
  heritage: 첨성대
  factuality: history
  source: 한국민족문화대백과
  urls:
    - https://encykorea.aks.ac.kr/...
  content:
    [문화재: 첨성대 | ...]
    경상북도 경주시에 있는 신라의 천문 관측 시설.

<스토리 소재 legend_material>
- chunk_id: legend_cheomseongdae_seondeok_001_chunk_000
  factuality: mixed
  mission_hooks:
    - 하늘과 별을 상징하는 요소를 찾는 미션으로 활용 가능
  ...

<상징 소재 symbolic_material>
없음
```

장소별로 budget (`max_context_chars_per_place`, default 2500) 이 적용되어 초과분은 `…(생략)` 로 잘림. 의미 왜곡을 피하기 위해 컨텐츠 자체를 재요약하지 않고 단순 truncate.

## prompt 버전 관리

- 상수: `STORY_PROMPT_VERSION = "story_intro_v1"`
- `StoryPromptBuilder(prompt_version=...)` 로 override 가능. 152 응답에 그대로 echo 되어 LLM 출력 / 프롬프트 변경 추적이 가능.

## 152 에서의 재사용

```python
service = RagSearchService(...)
fact = service.search(RagSearchRequest(query=q, content_roles=["fact_context"]))
story = service.search(RagSearchRequest(query=q, content_roles=["legend_material","symbolic_material"]))

req = StoryIntroGenerationRequest(
    user_profile=...,
    places=[...],
    rag_contexts=[
        RagContextByPlace(
            place_name="첨성대",
            fact_contexts=[RagContextItem(...) for r in fact.results],
            story_materials=[RagContextItem(...) for r in story.results if r.content_role == "legend_material"],
            symbolic_materials=[RagContextItem(...) for r in story.results if r.content_role == "symbolic_material"],
        ),
    ],
)

messages = StoryPromptBuilder().build_intro_messages(req)
raw = openai_chat_client.chat(messages, response_format={"type": "json_object"})
draft = StoryIntroDraft.model_validate_json(raw)
```

## 153 에서의 확장

`StoryUpdateGenerationRequest` 와 `StoryPromptBuilder.build_update_messages` 를 그대로 사용. 차이는 `current_place_name`, `previous_story_state`, `completed_mission_result` 가 사용자 프롬프트에 추가된다는 점뿐. system 프롬프트와 출력 JSON 스키마는 동일.

## 왜 LLM 호출을 이 단계에서 하지 않나

- 프롬프트 버전 / 출력 JSON 계약을 먼저 고정해야 152 에서 "LLM 응답을 어떻게 검증할지" 를 명확히 짤 수 있음.
- prompt builder 와 LLM client 를 분리해 두면 152 가 `messages` 만 넘기면 되고, 평가 / dry-run / fixture 캡처가 쉬워짐.
- 151 단계에서는 schema/builder 단위 테스트만으로 회귀 방어가 가능 (실 OpenAI 키 / DB 불필요).
