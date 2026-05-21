"""
POST /artifacts/ebook/jobs — E-book 콘텐츠 구조 생성.

API.md 의 wire schema (`EbookJobRequest` / `EbookJobResponse`) 는 그대로
유지하고, 내부에서 `EbookJobService` (= 156 EbookDraftService + adapter) 가
deterministic 하게 콘텐츠를 조립한다. 파일 (PDF/EPUB) 생성·저장은 Spring
Boot / File 서버 책임이며 이 엔드포인트는 동기 응답이다.
"""
from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException

from app.schemas.story import EbookJobRequest, EbookJobResponse
from app.services.ebook import EbookJobService

router = APIRouter(tags=["artifacts"])


def get_ebook_job_service() -> EbookJobService:
    """FastAPI dependency. Tests override this with a fake-backed service."""
    return EbookJobService()


@router.post("/artifacts/ebook/jobs", response_model=EbookJobResponse)
def create_ebook_job(
    payload: EbookJobRequest,
    service: EbookJobService = Depends(get_ebook_job_service),
) -> EbookJobResponse:
    try:
        data = service.create_ebook_content(payload)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail={"code": "EBOOK_JOB_FAILED", "message": str(e)},
        )

    return EbookJobResponse(
        success=True,
        data=data,
        error=None,
        timestamp=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    )
