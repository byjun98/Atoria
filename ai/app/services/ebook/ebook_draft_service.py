"""
Compose a full `EbookDraft` from a request: cover → ToC → prologue →
place pages (and optional mission_result pages) → epilogue → sources.

Pure deterministic. 157 will wrap this in a Job API.
"""
from __future__ import annotations

import uuid

from app.schemas.ebook_schema import (
    EbookCoverDraft,
    EbookDraft,
    EbookDraftGenerationRequest,
    EbookMissionInput,
    EbookMissionResultInput,
    EbookPageDraft,
    EbookPlaceInput,
)
from app.services.ebook.ebook_image_prompt_builder import EbookImagePromptBuilder
from app.services.ebook.ebook_page_builder import EbookPageBuilder


class EbookDraftService:
    def __init__(
        self,
        page_builder: EbookPageBuilder | None = None,
        image_prompt_builder: EbookImagePromptBuilder | None = None,
    ) -> None:
        self.image_prompt_builder = image_prompt_builder or EbookImagePromptBuilder()
        self.page_builder = page_builder or EbookPageBuilder(
            image_prompt_builder=self.image_prompt_builder
        )

    # ---- public --------------------------------------------------------

    def generate_draft(self, request: EbookDraftGenerationRequest) -> EbookDraft:
        warnings: list[str] = []
        # honour the option's max_page_body_chars at runtime
        self.page_builder.max_chars = request.options.max_page_body_chars

        ordered_places = sorted(request.places, key=lambda p: p.sequence)
        missions_by_seq = self._match_missions_by_sequence(
            request.story_source.missions
        )
        results_by_seq = self._match_results_by_sequence(request.mission_results)

        if len(missions_by_seq) != len(ordered_places):
            warnings.append(
                f"PLACE_COUNT_MISMATCH: places={len(ordered_places)}, "
                f"missions={len(missions_by_seq)}"
            )

        unmatched_results = [
            r for r in request.mission_results
            if r.sequence not in {p.sequence for p in ordered_places}
        ]
        for r in unmatched_results:
            warnings.append(
                f"MISSION_RESULT_UNMATCHED: sequence={r.sequence} place_name={r.place_name}"
            )

        pages: list[EbookPageDraft] = []
        page_no = 1
        cover: EbookCoverDraft | None = None

        # 1) cover
        if request.options.include_cover:
            cover = self.page_builder.build_cover(request)
            pages.append(EbookPageDraft(
                page_number=page_no,
                page_type="cover",
                title=cover.title,
                subtitle=cover.subtitle,
                image_prompt=cover.image_prompt,
            ))
            page_no += 1

        # 2) table of contents
        if request.options.include_table_of_contents:
            pages.append(self.page_builder.build_table_of_contents_page(
                ordered_places, page_no
            ))
            page_no += 1

        # 3) prologue
        pages.append(self.page_builder.build_prologue_page(
            request.story_source.intro, page_no
        ))
        page_no += 1

        # 4) place pages (+ optional mission_result pages)
        for place in ordered_places:
            mission = missions_by_seq.get(place.sequence)
            result = results_by_seq.get(place.sequence)
            if mission is None:
                warnings.append(
                    f"MISSING_MISSION_FOR_PLACE: sequence={place.sequence} "
                    f"place_name={place.place_name}"
                )

            # Per-page source trace fallback:
            #   1) mission.used_chunk_ids / mission.source_urls (가장 구체적)
            #   2) story_source.used_chunk_ids / source_urls (전체 fallback)
            page_chunk_ids: list[str] = []
            page_source_urls: list[str] = []
            if mission is not None:
                page_chunk_ids = list(mission.used_chunk_ids or [])
                page_source_urls = list(mission.source_urls or [])
            if not page_chunk_ids:
                page_chunk_ids = list(request.story_source.used_chunk_ids or [])
            if not page_source_urls:
                page_source_urls = list(request.story_source.source_urls or [])

            page = self.page_builder.build_place_story_page(
                place=place,
                mission=mission,
                mission_result=result,
                page_number=page_no,
                include_image_prompts=request.options.include_image_prompts,
                include_missing_result_note_in_body=(
                    request.options.include_missing_result_note_in_body
                ),
                used_chunk_ids=page_chunk_ids,
                source_urls=page_source_urls,
            )
            pages.append(page)
            if page.metadata.get("truncated"):
                warnings.append(
                    f"PAGE_BODY_TRUNCATED: page_number={page_no} place={place.place_name}"
                )
            page_no += 1

            if request.options.include_mission_results:
                if result is None:
                    warnings.append(
                        f"MISSING_MISSION_RESULT: sequence={place.sequence} "
                        f"place_name={place.place_name}"
                    )
                    continue
                if not result.completed and not request.options.include_incomplete_missions:
                    continue
                pages.append(self.page_builder.build_mission_result_page(
                    place=place,
                    mission=mission,
                    mission_result=result,
                    page_number=page_no,
                ))
                page_no += 1

        # 5) epilogue
        pages.append(self.page_builder.build_epilogue_page(
            outro=request.story_source.outro,
            place_names=[p.place_name for p in ordered_places],
            page_number=page_no,
        ))
        page_no += 1

        # 6) sources
        all_source_urls = self._collect_sources(request, pages)
        if request.options.include_sources:
            if all_source_urls:
                pages.append(self.page_builder.build_sources_page(
                    all_source_urls, page_no
                ))
                page_no += 1
            else:
                warnings.append("SOURCE_URLS_EMPTY: 출처 URL 이 비어 있어 sources page 생략")

        # finalise: page_number 재부여 (안전용)
        for i, p in enumerate(pages, start=1):
            p.page_number = i

        used_chunk_ids = self._collect_chunk_ids(request, pages)

        return EbookDraft(
            draft_id=f"ebook-draft-{uuid.uuid4().hex[:12]}",
            title=cover.title if cover else self._fallback_title(request),
            subtitle=cover.subtitle if cover else None,
            cover=cover,
            pages=pages,
            page_count=len(pages),
            source_urls=all_source_urls,
            used_chunk_ids=used_chunk_ids,
            warnings=warnings,
            metadata={
                "request_id": request.request_id,
                "title_style": request.options.title_style,
                "language": request.user_profile.language,
            },
        )

    # ---- helpers -------------------------------------------------------

    @staticmethod
    def _match_missions_by_sequence(
        missions: list[EbookMissionInput],
    ) -> dict[int, EbookMissionInput]:
        return {m.sequence: m for m in missions}

    @staticmethod
    def _match_results_by_sequence(
        results: list[EbookMissionResultInput],
    ) -> dict[int, EbookMissionResultInput]:
        return {r.sequence: r for r in results}

    @staticmethod
    def _collect_sources(
        request: EbookDraftGenerationRequest, pages: list[EbookPageDraft]
    ) -> list[str]:
        seen: list[str] = []
        for u in request.story_source.source_urls or []:
            if u and u not in seen:
                seen.append(u)
        for page in pages:
            for u in page.source_urls or []:
                if u and u not in seen:
                    seen.append(u)
        return seen

    @staticmethod
    def _collect_chunk_ids(
        request: EbookDraftGenerationRequest, pages: list[EbookPageDraft]
    ) -> list[str]:
        seen: list[str] = []
        for c in request.story_source.used_chunk_ids or []:
            if c and c not in seen:
                seen.append(c)
        for page in pages:
            for c in page.used_chunk_ids or []:
                if c and c not in seen:
                    seen.append(c)
        return seen

    @staticmethod
    def _fallback_title(request: EbookDraftGenerationRequest) -> str:
        nick = (request.user_profile.nickname or "").strip()
        return f"{nick + '의 ' if nick else ''}경주 이야기"
