"""
POST /rag/search           — query → top-k similar chunks
POST /rag/search/by-heritage — same, scoped to a single heritage_name

Mounted at root (no prefix), matching /story/intro and /artifacts/ebook/jobs.
The 151/152 story-generation code calls `RagSearchService` directly, not via
HTTP — this router is a thin wrapper for development / debugging / smoke tests.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.clients.openai_embedding_client import OpenAIEmbeddingClient
from app.db.session import get_db
from app.repositories.rag_search_repository import RagSearchRepository
from app.schemas.rag_search_schema import (
    RagSearchByHeritageRequest,
    RagSearchError,
    RagSearchRequest,
    RagSearchResponse,
)
from app.services.rag import RagSearchService

router = APIRouter(tags=["rag"])


def get_rag_search_service(db: Session = Depends(get_db)) -> RagSearchService:
    """FastAPI dependency. Tests override this with a fake-backed service."""
    repository = RagSearchRepository(db)
    embedding_client = OpenAIEmbeddingClient()
    return RagSearchService(repository=repository, embedding_client=embedding_client)


@router.post("/rag/search", response_model=RagSearchResponse)
def rag_search(
    payload: RagSearchRequest,
    service: RagSearchService = Depends(get_rag_search_service),
) -> RagSearchResponse:
    try:
        data = service.search(payload)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return RagSearchResponse(success=True, data=data, error=None)


@router.post("/rag/search/by-heritage", response_model=RagSearchResponse)
def rag_search_by_heritage(
    payload: RagSearchByHeritageRequest,
    service: RagSearchService = Depends(get_rag_search_service),
) -> RagSearchResponse:
    try:
        data = service.search_by_heritage(payload)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return RagSearchResponse(success=True, data=data, error=None)
