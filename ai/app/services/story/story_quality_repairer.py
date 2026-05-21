"""
Apply safe structural fixes to a `StoryIntroDraft`.

We never rewrite LLM prose here — only metadata-level fixes (ordering,
backfill from RAG, source url unioning, mission_type normalisation).
Content-level problems are surfaced as warnings and can trigger a retry.
"""
from __future__ import annotations

from app.schemas.story_schema import (
    PlaceStoryDraft,
    RagContextByPlace,
    RagContextItem,
    StoryIntroDraft,
    StoryIntroGenerationRequest,
)
from app.services.story.story_quality_models import StoryQualityReport


# Loose mission_type aliases the LLM might emit (e.g. uppercase, wire form).
_MISSION_TYPE_ALIAS: dict[str, str] = {
    "PHOTO": "photo", "Photo": "photo",
    "QUIZ": "quiz", "Quiz": "quiz",
    "ACTION": "observation", "Action": "observation",
    "CHOICE": "quiz", "Choice": "quiz",
    "OBSERVATION": "observation",
    "IMAGINATION": "imagination",
    "ROUTE": "route",
}


class StoryQualityRepairer:
    def repair_intro_draft(
        self,
        draft: StoryIntroDraft,
        request: StoryIntroGenerationRequest,
        report: StoryQualityReport,
    ) -> tuple[StoryIntroDraft, StoryQualityReport]:
        # 1) sort places by sequence (cheap, always safe)
        sorted_places = sorted(draft.places, key=lambda p: p.sequence)

        # 2) per-place repairs that need the matching RAG bucket
        rag_lookup = {c.place_name: c for c in request.rag_contexts}
        for place in sorted_places:
            self._repair_place(
                place=place,
                rag_for_place=rag_lookup.get(place.place_name),
                report=report,
            )

        # 3) draft-level used_chunk_ids / source_urls union from places
        draft_used = self._merge_unique(draft.used_chunk_ids, *(p.used_chunk_ids for p in sorted_places))
        draft_urls = self._merge_unique(draft.source_urls, *(p.source_urls for p in sorted_places))
        if list(draft_used) != list(draft.used_chunk_ids):
            report.fixed_count += 1
        if list(draft_urls) != list(draft.source_urls):
            report.fixed_count += 1

        repaired = draft.model_copy(
            update={
                "places": sorted_places,
                "used_chunk_ids": draft_used,
                "source_urls": draft_urls,
                "warnings": list(draft.warnings) + report.to_warning_messages(),
            }
        )
        return repaired, report

    # ---- helpers --------------------------------------------------------

    def _repair_place(
        self,
        *,
        place: PlaceStoryDraft,
        rag_for_place: RagContextByPlace | None,
        report: StoryQualityReport,
    ) -> None:
        m = place.mission

        # mission_type normalisation
        if m is not None and m.mission_type not in {
            "observation", "photo", "quiz", "imagination", "route",
        }:
            normalised = _MISSION_TYPE_ALIAS.get(m.mission_type)
            if normalised:
                m.mission_type = normalised  # type: ignore[assignment]
                report.fixed_count += 1

        # related_place_name backfill
        if m is not None and not m.related_place_name and place.place_name:
            m.related_place_name = place.place_name
            report.fixed_count += 1

        # verification_hint backfill. This is metadata for the app-side photo
        # checker, so we can safely add a neutral clear condition without
        # rewriting the user's visible story prose.
        if m is not None and not m.verification_hint:
            keywords = [k for k in (m.mission_keywords or []) if k][:2]
            target = "와 ".join(keywords) if keywords else "정한 단서"
            if place.place_name:
                m.verification_hint = (
                    f"{place.place_name}에서 {target} 조건이 한 화면에 보이면 클리어입니다."
                )
            else:
                m.verification_hint = f"{target} 조건이 한 화면에 보이면 클리어입니다."
            report.fixed_count += 1

        if rag_for_place is None:
            return
        rag_chunk_ids = self._collect_rag_chunk_ids(rag_for_place)
        rag_urls = self._collect_rag_urls(rag_for_place)

        # place.used_chunk_ids backfill
        if not place.used_chunk_ids and rag_chunk_ids:
            place.used_chunk_ids = list(rag_chunk_ids)
            report.fixed_count += 1
        # place.source_urls backfill
        if not place.source_urls and rag_urls:
            place.source_urls = list(rag_urls)
            report.fixed_count += 1
        # mission.related_chunk_ids backfill
        if m is not None and not m.related_chunk_ids and place.used_chunk_ids:
            m.related_chunk_ids = list(place.used_chunk_ids)
            report.fixed_count += 1

    @staticmethod
    def _collect_rag_chunk_ids(rag: RagContextByPlace) -> list[str]:
        out: list[str] = []
        for bucket in (rag.fact_contexts, rag.story_materials, rag.symbolic_materials):
            for it in bucket:
                if it.chunk_id and it.chunk_id not in out:
                    out.append(it.chunk_id)
        return out

    @staticmethod
    def _collect_rag_urls(rag: RagContextByPlace) -> list[str]:
        out: list[str] = []
        for bucket in (rag.fact_contexts, rag.story_materials, rag.symbolic_materials):
            for it in bucket:
                for u in it.source_urls or []:
                    if u and u not in out:
                        out.append(u)
        return out

    @staticmethod
    def _merge_unique(*lists) -> list:
        seen: list = []
        for lst in lists:
            for x in lst or []:
                if x and x not in seen:
                    seen.append(x)
        return seen
