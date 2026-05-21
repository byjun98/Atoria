"""
Synchronous /artifacts/ebook/jobs handler logic.

Two paths:
  1. LLM narrative path (default, controlled by `EBOOK_NARRATIVE_USE_LLM`).
     The narrative service drives a chat completion and returns a storybook
     draft, which the adapter rewires into the API.md wire response.
  2. Deterministic 156 path — used as fallback when the narrative path fails
     for any reason (LLM error / JSON parse / forbidden phrase / disabled).

The wire schema is identical in both paths; the API.md response (success /
data / error / timestamp) never grows new fields.
"""
from __future__ import annotations

import logging

from app.core.config import settings
from app.schemas.story import EbookJobData, EbookJobRequest
from app.services.ebook.ebook_draft_service import EbookDraftService
from app.services.ebook.ebook_job_adapter import EbookJobAdapter
from app.services.ebook.ebook_narrative_service import (
    EbookNarrativeService,
    NarrativeGenerationFailed,
)

_logger = logging.getLogger(__name__)


class EbookJobService:
    def __init__(
        self,
        draft_service: EbookDraftService | None = None,
        adapter: EbookJobAdapter | None = None,
        narrative_service: EbookNarrativeService | None = None,
    ) -> None:
        self.draft_service = draft_service or EbookDraftService()
        self.adapter = adapter or EbookJobAdapter()
        # Lazy-init: only constructed when LLM path is enabled and used.
        self._narrative_service = narrative_service

    # ---- public --------------------------------------------------------

    def create_ebook_content(self, request: EbookJobRequest) -> EbookJobData:
        if settings.EBOOK_NARRATIVE_USE_LLM:
            try:
                narrative = self._get_narrative_service().generate_narrative(request)
                return self.adapter.to_ebook_content_from_narrative(
                    story_id=request.story_id,
                    request=request,
                    narrative=narrative,
                )
            except NarrativeGenerationFailed as e:
                # NOTE: avoid reserved LogRecord attribute names ('message',
                # 'msg', 'name', 'args' etc.) in `extra` — they raise from
                # inside the handler and break the fallback.
                _logger.warning(
                    "ebook_narrative_fallback narrative_code=%s reason=%s",
                    e.code, e.message,
                    extra={"narrative_violations": e.violations},
                )
            except Exception as e:  # noqa: BLE001 — fallback covers anything else
                _logger.warning(
                    "ebook_narrative_fallback_unexpected error=%s: %s",
                    type(e).__name__, e,
                )
        # Deterministic fallback — wire schema identical.
        return self._fallback_to_deterministic(request)

    # ---- helpers -------------------------------------------------------

    def _get_narrative_service(self) -> EbookNarrativeService:
        if self._narrative_service is None:
            self._narrative_service = EbookNarrativeService()
        return self._narrative_service

    def _fallback_to_deterministic(self, request: EbookJobRequest) -> EbookJobData:
        draft_request = self.adapter.to_draft_request(request)
        draft = self.draft_service.generate_draft(draft_request)
        return self.adapter.to_ebook_content(
            story_id=request.story_id,
            request=request,
            draft=draft,
        )
