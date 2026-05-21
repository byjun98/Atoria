"""
POST /story/intro — Spring Boot ↔ AI 연동 엔드포인트.

API.md 의 wire schema (StoryIntroRequest / StoryIntroResponse) 는 그대로 유지하고,
내부에서 RAG 검색 + 151 프롬프트 + OpenAI Chat Completion 으로 응답을 생성한다.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.clients.openai_chat_client import OpenAIChatClient
from app.clients.openai_embedding_client import OpenAIEmbeddingClient
from app.clients.weather_client import WeatherClient
from app.db.session import get_db
from app.repositories.rag_search_repository import RagSearchRepository
from app.schemas.story import StoryIntroRequest, StoryIntroResponse
from app.services.rag import RagSearchService
from app.services.story.story_intro_service import (
    StoryIntroService,
    StoryQualityValidationFailed,
)
from app.services.story.story_response_parser import StoryResponseError
from app.services.story.story_schema_adapter import to_internal_request, to_wire_response

router = APIRouter(tags=["story"])


def get_story_intro_service(db: Session = Depends(get_db)) -> StoryIntroService:
    """FastAPI dependency. Tests override this with a fake-backed service."""
    rag_repo = RagSearchRepository(db)
    embedding_client = OpenAIEmbeddingClient()
    rag_service = RagSearchService(repository=rag_repo, embedding_client=embedding_client)
    chat_client = OpenAIChatClient()
    weather_client = WeatherClient()
    return StoryIntroService(
        rag_search_service=rag_service,
        chat_client=chat_client,
        weather_client=weather_client,
    )


@router.post("/story/intro", response_model=StoryIntroResponse)
def create_story_intro(
    payload: StoryIntroRequest,
    service: StoryIntroService = Depends(get_story_intro_service),
) -> StoryIntroResponse:
    internal_req = to_internal_request(payload)
    try:
        draft = service.generate_intro(internal_req)
    except StoryResponseError as e:
        # LLM 응답 자체의 형식 문제는 502 로 분류 (upstream contract 문제).
        raise HTTPException(status_code=502, detail={"code": e.code, "message": e.message})
    except StoryQualityValidationFailed as e:
        # 품질 검증 실패 (구조/길이/필수 필드) — repair / retry 후에도 살릴 수 없는 경우.
        raise HTTPException(
            status_code=502,
            detail={
                "code": "STORY_QUALITY_VALIDATION_FAILED",
                "message": str(e),
                "issue_codes": [i.code for i in e.report.issues if i.severity == "error"],
            },
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail={"code": "STORY_GENERATION_FAILED", "message": str(e)},
        )
    return to_wire_response(draft)
