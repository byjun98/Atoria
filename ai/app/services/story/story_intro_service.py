"""
Orchestrate the /story/intro flow:
  RAG search per place → 151 prompt build → OpenAI Chat Completion → parse → draft.
"""
from __future__ import annotations

from app.clients.openai_chat_client import OpenAIChatClient
from app.core.config import settings  # noqa: F401
from app.schemas.rag_search_schema import RagSearchRequest, RagSearchResultItem
from app.schemas.story_schema import (
    CoursePlaceInput,
    RagContextByPlace,
    RagContextItem,
    StoryIntroDraft,
    StoryIntroGenerationRequest,
)
from app.core.logging import get_logger
from app.services.rag import RagSearchService
from app.services.story.rag_context_formatter import RagContextFormatter
from app.services.story.story_prompt_builder import StoryPromptBuilder
from app.services.story.story_quality_models import StoryQualityReport
from app.services.story.story_quality_repairer import StoryQualityRepairer
from app.services.story.story_quality_validator import StoryQualityValidator
from app.services.story.story_response_parser import (
    StoryResponseError,
    StoryResponseParser,
)


_logger = get_logger(__name__)


class StoryQualityValidationFailed(RuntimeError):
    """Raised when quality control cannot salvage the LLM response."""

    def __init__(self, message: str, report: StoryQualityReport):
        super().__init__(message)
        self.report = report


from app.clients.weather_client import WeatherClient

class StoryIntroService:
    def __init__(
        self,
        rag_search_service: RagSearchService,
        chat_client: OpenAIChatClient,
        weather_client: WeatherClient | None = None,
        prompt_builder: StoryPromptBuilder | None = None,
        response_parser: StoryResponseParser | None = None,
        quality_validator: StoryQualityValidator | None = None,
        quality_repairer: StoryQualityRepairer | None = None,
    ) -> None:
        self.rag = rag_search_service
        self.chat = chat_client
        self.weather = weather_client or WeatherClient()
        self.prompt_builder = prompt_builder or StoryPromptBuilder(
            rag_context_formatter=RagContextFormatter()
        )
        self.parser = response_parser or StoryResponseParser()
        self.validator = quality_validator or StoryQualityValidator()
        self.repairer = quality_repairer or StoryQualityRepairer()

    # ---- public --------------------------------------------------------

    def generate_intro(self, request: StoryIntroGenerationRequest) -> StoryIntroDraft:
        warnings: list[str] = []
        rag_stats: dict = {
            "rag_search_strategy": "disabled",
            "embedding_call_count": 0,
            "vector_fallback_count": 0,
        }

        ordered_places = sorted(request.places, key=lambda p: p.sequence)
        request_for_prompt = request.model_copy(update={"places": ordered_places})

        weather_data = self.weather.fetch_current_weather()
        # Convert WeatherData to WeatherContext schema. Since they have the same fields, we can just dump/load or map manually.
        from app.schemas.story_schema import WeatherContext
        weather_ctx = WeatherContext(
            temperature=weather_data.temperature,
            rainfall=weather_data.rainfall,
            humidity=weather_data.humidity,
            wind_speed=weather_data.wind_speed,
            wind_direction=weather_data.wind_direction,
        )
        request_for_prompt = request_for_prompt.model_copy(update={"weather_context": weather_ctx})

        if request_for_prompt.options.use_rag:
            rag_contexts, rag_warnings, rag_stats = self._build_rag_contexts_for_places(
                ordered_places, request_id=request.request_id,
            )
            request_for_prompt = request_for_prompt.model_copy(
                update={"rag_contexts": rag_contexts}
            )
            warnings.extend(rag_warnings)

        messages = self.prompt_builder.build_intro_messages(request_for_prompt)
        draft, report, attempt_trace = self._generate_with_quality_control(
            request_for_prompt=request_for_prompt,
            messages=messages,
            initial_warnings=warnings,
        )
        attempt_count = len(attempt_trace)
        _logger.info(
            "story_intro_generated",
            retry_used=attempt_count > 1,
            attempt_count=attempt_count,
            passed=report.passed,
            error_count=report.error_count,
            warning_count=report.warning_count,
            issue_codes=[i.code for i in report.issues],
            used_chunk_count=len(draft.used_chunk_ids),
            source_url_count=len(draft.source_urls),
            rag_search_strategy=rag_stats["rag_search_strategy"],
            embedding_call_count=rag_stats["embedding_call_count"],
            vector_fallback_count=rag_stats["vector_fallback_count"],
            metadata_selected_chunk_count=rag_stats.get("metadata_selected_chunk_count", 0),
        )
        return draft

    # ---- quality control loop -----------------------------------------

    def _generate_with_quality_control(
        self,
        *,
        request_for_prompt: StoryIntroGenerationRequest,
        messages: list[dict],
        initial_warnings: list[str],
    ) -> tuple[StoryIntroDraft, StoryQualityReport, list[dict]]:
        attempt_messages = list(messages)
        max_retries = (
            settings.STORY_QUALITY_MAX_RETRIES
            if settings.STORY_QUALITY_ENABLE_RETRY
            else 0
        )
        last_report: StoryQualityReport | None = None
        last_draft: StoryIntroDraft | None = None
        attempt_trace: list[dict] = []

        for attempt in range(max_retries + 1):
            raw = self.chat.create_json_chat_completion(attempt_messages)
            draft = self.parser.parse_intro_response(raw)
            ordered_places = sorted(request_for_prompt.places, key=lambda p: p.sequence)
            draft = self._reconcile_with_request(draft, ordered_places, list(initial_warnings))
            draft = draft.model_copy(
                update={"prompt_version": self.prompt_builder.prompt_version}
            )

            report = self.validator.validate_intro_draft(draft, request_for_prompt)
            draft, report = self.repairer.repair_intro_draft(draft, request_for_prompt, report)

            # Re-validate after repair so the report reflects what's left.
            post_report = self.validator.validate_intro_draft(draft, request_for_prompt)
            post_report.fixed_count = report.fixed_count
            last_report, last_draft = post_report, draft

            error_issues = [i for i in post_report.issues if i.severity == "error"]
            attempt_trace.append({
                "attempt": attempt + 1,
                "passed": not post_report.has_errors(),
                "error_codes": [i.code for i in error_issues],
                "failed_fields": [i.location for i in error_issues if i.location],
                "error_count": post_report.error_count,
                "warning_count": post_report.warning_count,
                "fixed_count": post_report.fixed_count,
            })

            if not post_report.has_errors():
                return draft, post_report, attempt_trace

            if attempt >= max_retries:
                break

            attempt_messages = self._build_retry_messages(messages, post_report)

        assert last_draft is not None and last_report is not None
        _logger.warning(
            "story_intro_quality_failed",
            request_id=request_for_prompt.request_id,
            attempt_count=len(attempt_trace),
            retry_used=len(attempt_trace) > 1,
            attempt_trace=attempt_trace,
        )
        raise StoryQualityValidationFailed(
            "STORY_QUALITY_VALIDATION_FAILED: " + "; ".join(
                i.message for i in last_report.issues if i.severity == "error"
            ),
            last_report,
        )

    @staticmethod
    def _build_retry_messages(
        original: list[dict], report: StoryQualityReport
    ) -> list[dict]:
        reasons = "\n".join(
            f"- ({i.code}) {i.message}"
            for i in report.issues
            if i.severity == "error"
        ) or "- (품질 기준 미달)"
        repair_note = (
            "\n\n[REPAIR_INSTRUCTION]\n"
            "생성 결과가 아래 이유로 품질 기준을 통과하지 못했습니다:\n"
            f"{reasons}\n\n"
            "다음 규칙을 반드시 지키고 다시 작성하세요.\n"
            "1. 요청 places 수와 동일한 places 배열을 반환하세요.\n"
            "2. 각 story_fragment 는 안내문이 아니라 사용자가 해당 장소 앞에서 단서를 발견하는 2~3문장 장면이어야 하며, 첫 문장에 정확한 place_name을 넣으세요.\n"
            "3. 각 mission_title 에는 그 장소의 고유 소재(별·창·돌단·연못·달빛·누각·종소리·계단·다리·관련 인물명 등)를 포함하세요. "
            "'탐험·조사·찾기·관찰·둘러보기' 같은 일반 단어만으로 끝내지 마세요.\n"
            "4. 각 mission_instruction 의 첫 문장에도 정확한 place_name을 넣고, 퀘스트 목표, 현장 행동, 사진 클리어 조건, 이야기 보상을 모두 포함하세요.\n"
            "5. 현장 행동은 '세어 보기 / 찾아내기 / 비교하기 / 맞춰 보기 / 위치 고르기 / 순서 정하기'처럼 사용자가 직접 해결하는 동사로 쓰세요. '유심히 보세요', '나란히 보세요'만으로 끝내지 마세요.\n"
            "6. '사진에 담아보세요', '사진으로 남겨보세요', '그 모습을 사진으로 찍어보세요', '사진을 찍어보세요' 같은 사후 촬영 표현을 쓰지 마세요.\n"
            "7. 사진 조건과 verification_hint는 'A와 B가 한 화면에 보이면 클리어'처럼 판정 조건으로 쓰고, '담아보세요/포착하세요'를 쓰지 마세요.\n"
            "8. 석빙고는 내부·천장·배수로·바닥 중앙 확인을 요구하지 말고, 입구·돌지붕선·외부 안내 요소만 활용하세요.\n"
            "9. 불국사 대웅전은 내부·불단·불상 촬영을 요구하지 말고, 외부 전면·기둥·처마·현판·마당·주변 탑 배치만 활용하세요.\n"
            "10. fact_context 는 사실 근거로만 사용하고, legend_material 은 '전해진다 / 이야기된다 / 상상해 볼 수 있다' 로 표현하고, symbolic_material 은 '상징적으로 / 의미로 해석하면' 으로 표현하세요.\n"
            "11. intro 에는 사용자 닉네임 또는 성향과 방문 장소명 2개 이상을 포함하세요. '숨겨진 비밀/역사의 숨결/어떤 모험' 같은 앱 안내문은 쓰지 마세요.\n"
            "12. outro 에는 방문한 장소 경험을 최소 2개 이상 회상시키되, '동화책', '스토리 생성', '잊지 못할 추억', '새로운 모험', '다음 여정' 같은 메타/홍보 표현은 쓰지 마세요.\n"
            "13. JSON schema 외의 문장은 출력하지 마세요."
        )
        # Append the note to the last user message; do not lose system prompt.
        retried = [dict(m) for m in original]
        if retried and retried[-1].get("role") == "user":
            retried[-1]["content"] = (retried[-1].get("content") or "") + repair_note
        return retried

    # ---- RAG -----------------------------------------------------------

    def _build_rag_contexts_for_places(
        self, places: list[CoursePlaceInput], request_id: str | None = None,
    ) -> tuple[list[RagContextByPlace], list[str], dict]:
        warnings: list[str] = []
        metadata_first = settings.RAG_METADATA_FIRST_ENABLED
        # Vector fallback is intentionally disabled — heritage_name exact match only.
        vector_fallback_enabled = False
        min_results = settings.RAG_MIN_CHUNKS_PER_PLACE
        max_per_place = settings.RAG_MAX_CHUNKS_PER_PLACE
        max_total = settings.RAG_MAX_TOTAL_CHUNKS
        strategy = "metadata_first" if metadata_first else "vector_only"

        # per-place buckets keyed by place_name (kept ordered via `places`)
        facts_by_place: dict[str, list[RagContextItem]] = {p.place_name: [] for p in places}
        stories_by_place: dict[str, list[RagContextItem]] = {p.place_name: [] for p in places}
        symbolics_by_place: dict[str, list[RagContextItem]] = {p.place_name: [] for p in places}
        rag_failed: set[str] = set()

        # ---- Phase 1: metadata-first search (no embedding) -----------------
        if metadata_first:
            for place in places:
                try:
                    facts = self._metadata_fact_items(place, max_per_place)
                    stories, syms = self._metadata_story_items(place, max_per_place)
                except Exception as e:  # metadata search failed for this place
                    warnings.append(
                        f"RAG 검색 실패: {place.place_name} — {type(e).__name__}: {e}"
                    )
                    rag_failed.add(place.place_name)
                    continue
                facts_by_place[place.place_name] = facts
                stories_by_place[place.place_name] = stories
                symbolics_by_place[place.place_name] = syms

        # ---- Phase 2: identify places that still need vector fallback ------
        fallback_requests: list[RagSearchRequest] = []
        fallback_meta: list[tuple[CoursePlaceInput, str]] = []  # (place, query_type)
        if not vector_fallback_enabled:
            _logger.info(
                "rag_vector_search_skipped",
                request_id=request_id,
                reason="heritage_name_exact_match_only",
                place_names=[p.place_name for p in places],
            )
        if vector_fallback_enabled:
            for place in places:
                if place.place_name in rag_failed:
                    continue
                fact_n = len(facts_by_place[place.place_name])
                story_n = (
                    len(stories_by_place[place.place_name])
                    + len(symbolics_by_place[place.place_name])
                )
                fact_short = fact_n < min_results
                story_short = story_n < min_results
                if not fact_short and not story_short:
                    _logger.info(
                        "rag_metadata_search_used",
                        request_id=request_id,
                        place_name=place.place_name,
                        matched_chunk_count=fact_n + story_n,
                        selected_chunk_count=fact_n + story_n,
                        vector_search_skipped=True,
                    )
                    continue
                if fact_short:
                    _logger.info(
                        "rag_vector_fallback_triggered",
                        request_id=request_id,
                        place_name=place.place_name,
                        reason="fact_context_below_min",
                        metadata_chunk_count=fact_n,
                    )
                    fallback_requests.append(RagSearchRequest(
                        query=f"{place.place_name} 역사 사실 문화재 설명",
                        top_k=max_per_place,
                        heritage_names=[place.place_name],
                        content_roles=["fact_context"],
                        include_context_text=True,
                        include_raw_text=False,
                    ))
                    fallback_meta.append((place, "fact_context"))
                if story_short:
                    _logger.info(
                        "rag_vector_fallback_triggered",
                        request_id=request_id,
                        place_name=place.place_name,
                        reason="story_material_below_min",
                        metadata_chunk_count=story_n,
                    )
                    fallback_requests.append(RagSearchRequest(
                        query=f"{place.place_name} 설화 전승 상징 미션 이야기",
                        top_k=max_per_place,
                        heritage_names=[place.place_name],
                        content_roles=["legend_material", "symbolic_material"],
                        include_context_text=True,
                        include_raw_text=False,
                    ))
                    fallback_meta.append((place, "story_material"))

        # ---- Phase 3: ONE batched embedding call for all fallbacks --------
        embedding_call_count = 0
        vector_fallback_count = len(fallback_requests)
        if fallback_requests:
            batched = settings.RAG_EMBEDDING_BATCH_ENABLED
            _logger.info(
                "rag_embedding_requested",
                request_id=request_id,
                search_stage="vector_fallback",
                place_names=[p.place_name for p, _ in fallback_meta],
                query_types=[qt for _, qt in fallback_meta],
                query_text_previews=[r.query[:120] for r in fallback_requests],
                batch_size=len(fallback_requests) if batched else 1,
                input_count=len(fallback_requests),
                input_chars=sum(len(r.query) for r in fallback_requests),
                model=getattr(getattr(self.rag, "client", None), "model", None),
            )
            try:
                if batched:
                    datas = self.rag.vector_search_batch(fallback_requests)
                    embedding_call_count = 1
                else:
                    datas = [self.rag.search(r) for r in fallback_requests]
                    embedding_call_count = len(fallback_requests)
                for (place, qt), data in zip(fallback_meta, datas):
                    items = [self._to_rag_context_item(i) for i in data.results]
                    if qt == "fact_context":
                        if not facts_by_place[place.place_name]:
                            facts_by_place[place.place_name] = items
                    else:  # story_material
                        new_stories = [i for i in items if i.content_role != "symbolic_material"]
                        new_syms = [i for i in items if i.content_role == "symbolic_material"]
                        if not stories_by_place[place.place_name]:
                            stories_by_place[place.place_name] = new_stories
                        if not symbolics_by_place[place.place_name]:
                            symbolics_by_place[place.place_name] = new_syms
            except Exception as e:
                warnings.append(
                    f"RAG vector fallback 실패: {type(e).__name__}: {e}"
                )
                vector_fallback_count = 0  # nothing landed

        # ---- Phase 4: assemble contexts with global cap -------------------
        contexts: list[RagContextByPlace] = []
        metadata_selected_chunk_count = 0
        total_used = 0
        for place in places:
            # Per-place cap (already bounded by top_k, but guard the union).
            facts = facts_by_place[place.place_name][:max_per_place]
            stories = stories_by_place[place.place_name][:max_per_place]
            symbolics = symbolics_by_place[place.place_name][:max_per_place]

            # Global cap across all places. Set RAG_MAX_TOTAL_CHUNKS <= 0 to
            # disable it so later course places do not silently lose RAG data.
            place_total = len(facts) + len(stories) + len(symbolics)
            if max_total > 0:
                remaining = max(0, max_total - total_used)
                if place_total > remaining:
                    # trim symbolics → stories → facts in that order
                    trim = place_total - remaining
                    for bucket_name in ("symbolics", "stories", "facts"):
                        if trim <= 0:
                            break
                        bucket = locals()[bucket_name]
                        drop = min(trim, len(bucket))
                        if drop:
                            del bucket[len(bucket) - drop:]
                            trim -= drop
                    place_total = len(facts) + len(stories) + len(symbolics)
            total_used += place_total

            if place.place_name not in rag_failed and place_total == 0:
                warnings.append(
                    f"RAG 자료 없음: {place.place_name} — 관찰형 미션 중심으로 작성하세요."
                )
            metadata_selected_chunk_count += place_total
            contexts.append(RagContextByPlace(
                place_name=place.place_name,
                fact_contexts=facts,
                story_materials=stories,
                symbolic_materials=symbolics,
            ))

        rag_stats = {
            "rag_search_strategy": strategy,
            "embedding_call_count": embedding_call_count,
            "vector_fallback_count": vector_fallback_count,
            "metadata_selected_chunk_count": metadata_selected_chunk_count,
        }
        return contexts, warnings, rag_stats

    def _metadata_fact_items(
        self, place: CoursePlaceInput, top_k: int,
    ) -> list[RagContextItem]:
        data = self.rag.metadata_search(RagSearchRequest(
            query=place.place_name,
            top_k=top_k,
            heritage_names=[place.place_name],
            content_roles=["fact_context"],
            include_context_text=True,
            include_raw_text=False,
        ))
        return [self._to_rag_context_item(i) for i in data.results]

    def _metadata_story_items(
        self, place: CoursePlaceInput, top_k: int,
    ) -> tuple[list[RagContextItem], list[RagContextItem]]:
        data = self.rag.metadata_search(RagSearchRequest(
            query=place.place_name,
            top_k=top_k,
            heritage_names=[place.place_name],
            content_roles=["legend_material", "symbolic_material"],
            include_context_text=True,
            include_raw_text=False,
        ))
        stories: list[RagContextItem] = []
        symbolics: list[RagContextItem] = []
        for raw in data.results:
            ctx = self._to_rag_context_item(raw)
            if ctx.content_role == "symbolic_material":
                symbolics.append(ctx)
            else:
                stories.append(ctx)
        return stories, symbolics

    @staticmethod
    def _to_rag_context_item(item: RagSearchResultItem) -> RagContextItem:
        return RagContextItem(
            chunk_id=item.chunk_id,
            heritage_name=item.heritage_name,
            title=item.title,
            content_role=item.content_role,  # already one of the 3 valid roles
            source_type=item.source_type,
            factuality_level=item.factuality_level,
            source_site=item.source_site,
            source_urls=list(item.source_urls or []),
            context_text=item.context_text or "",
            mission_hooks=list(item.mission_hooks or []),
            motifs=list(item.motifs or []),
            tone_tags=list(item.tone_tags or []),
            distance=item.distance,
        )

    # ---- post-processing ----------------------------------------------

    @staticmethod
    def _reconcile_with_request(
        draft: StoryIntroDraft,
        ordered_places: list[CoursePlaceInput],
        warnings: list[str],
    ) -> StoryIntroDraft:
        # places count mismatch
        if len(draft.places) != len(ordered_places):
            warnings.append(
                f"LLM places 길이가 요청과 다릅니다: "
                f"requested={len(ordered_places)}, returned={len(draft.places)}"
            )

        # sort by sequence
        sorted_places = sorted(draft.places, key=lambda p: p.sequence)

        # backfill mission.related_place_name when LLM left it blank
        for place in sorted_places:
            if place.mission is not None and not place.mission.related_place_name:
                place.mission.related_place_name = place.place_name

        return draft.model_copy(
            update={
                "places": sorted_places,
                "warnings": list(draft.warnings) + warnings,
            }
        )
