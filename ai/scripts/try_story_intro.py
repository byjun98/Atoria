"""실제 GPT를 한 번 호출해 /story/intro 결과를 사람이 직접 확인하는 스크립트.

DB 없이 동작한다. RAG 컨텍스트는 하드코딩된 샘플을 주입한다.
환경변수 OPENAI_API_KEY (또는 .env) 만 있으면 된다.

사용:
    .\venv\Scripts\python.exe scripts\try_story_intro.py
    .\venv\Scripts\python.exe scripts\try_story_intro.py --runs 3   # 같은 입력으로 3번 (변주 확인)
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.clients.openai_chat_client import OpenAIChatClient  # noqa: E402
from app.schemas.rag_search_schema import RagSearchData  # noqa: E402
from app.schemas.story_schema import (  # noqa: E402
    CoursePlaceInput,
    RagContextByPlace,
    RagContextItem,
    StoryGenerationOptions,
    StoryIntroGenerationRequest,
    StoryUserProfile,
)
from app.services.story.story_intro_service import StoryIntroService  # noqa: E402


# ---- RAG 컨텍스트 직접 주입용 가짜 검색기 (DB·임베딩 API 안 탐) ----------------

class StubRagService:
    """metadata_search / vector_search_batch 가 호출돼도 빈 결과만 반환.
    실제 RAG 자료는 request.rag_contexts 로 직접 주입한다."""
    class _Client:
        model = "(stub)"
    client = _Client()

    def metadata_search(self, request):
        return RagSearchData(query=request.query, embedding_model="(stub)",
                             top_k=request.top_k or 5, result_count=0, results=[],
                             filters={})

    def vector_search_batch(self, requests):
        return [self.metadata_search(r) for r in requests]

    def search(self, request):
        return self.metadata_search(request)


def _rag_for_cheomseongdae() -> RagContextByPlace:
    return RagContextByPlace(
        place_name="첨성대",
        fact_contexts=[RagContextItem(
            chunk_id="cheomseongdae_fact_001",
            heritage_name="첨성대",
            title="첨성대",
            content_role="fact_context",
            source_type="heritage_context",
            factuality_level="history",
            source_site="한국민족문화대백과",
            source_urls=["https://encykorea.aks.ac.kr/Article/E0002925"],
            context_text="경상북도 경주시에 있는 신라의 천문 관측 시설. 27단의 돌로 쌓아 올렸으며 남쪽 중앙에 창이 있다.",
            motifs=["천문", "별", "27단"],
        )],
        story_materials=[RagContextItem(
            chunk_id="cheomseongdae_legend_001",
            heritage_name="첨성대",
            title="첨성대와 선덕여왕",
            content_role="legend_material",
            source_type="historical_anecdote",
            factuality_level="mixed",
            source_site="우리역사넷",
            source_urls=["https://contents.history.go.kr/mobile/nh/view.do?levelId=nh_008"],
            context_text="선덕여왕이 별을 살피며 백성의 안위를 헤아렸다고 전해진다.",
            mission_hooks=["남쪽 창과 별의 방향을 잇는 미션"],
            motifs=["별", "예언", "선덕여왕"],
            tone_tags=["신비", "지혜"],
        )],
    )


def _rag_for_bulguksa() -> RagContextByPlace:
    return RagContextByPlace(
        place_name="불국사",
        fact_contexts=[RagContextItem(
            chunk_id="bulguksa_fact_001",
            heritage_name="불국사",
            title="불국사",
            content_role="fact_context",
            source_type="heritage_context",
            factuality_level="history",
            source_site="한국민족문화대백과",
            source_urls=["https://encykorea.aks.ac.kr/Article/E0024661"],
            context_text="신라 경덕왕 때 김대성이 창건한 사찰. 청운교·백운교 계단을 지나면 자하문이 나온다.",
            motifs=["계단", "백운교", "자하문", "김대성"],
        )],
        symbolic_materials=[RagContextItem(
            chunk_id="bulguksa_symbol_001",
            heritage_name="불국사",
            title="불국토의 입구",
            content_role="symbolic_material",
            source_type="symbolic_reading",
            factuality_level="symbolic",
            source_site="문화재청 해설",
            source_urls=["https://www.cha.go.kr"],
            context_text="청운교와 백운교는 인간 세계와 부처의 세계를 잇는 다리로 해석된다.",
            mission_hooks=["계단 수와 단의 형태 비교"],
            motifs=["다리", "경계", "불국토"],
            tone_tags=["경건", "신비"],
        )],
    )


def _rag_for_seokguram() -> RagContextByPlace:
    return RagContextByPlace(
        place_name="석굴암",
        fact_contexts=[RagContextItem(
            chunk_id="seokguram_fact_001",
            heritage_name="석굴암",
            title="석굴암 본존불",
            content_role="fact_context",
            source_type="heritage_context",
            factuality_level="history",
            source_site="한국민족문화대백과",
            source_urls=["https://encykorea.aks.ac.kr/Article/E0030249"],
            context_text="토함산 정상 근처의 인공 석굴 사원. 본존불은 동남쪽을 향해 동해를 바라본다.",
            motifs=["본존불", "동해", "토함산", "석굴"],
        )],
        story_materials=[RagContextItem(
            chunk_id="seokguram_legend_001",
            heritage_name="석굴암",
            title="김대성과 두 어머니",
            content_role="legend_material",
            source_type="historical_anecdote",
            factuality_level="mixed",
            source_site="우리역사넷",
            source_urls=["https://contents.history.go.kr/mobile/nh/view.do?levelId=nh_008_2"],
            context_text="김대성이 전생과 현생의 두 어머니를 위해 석굴암과 불국사를 함께 세웠다고 전해진다.",
            mission_hooks=["동해 일출 방향과 본존불 시선의 일치 확인"],
            motifs=["효심", "두 어머니", "일출"],
            tone_tags=["따뜻함", "경건"],
        )],
    )


def _build_request() -> StoryIntroGenerationRequest:
    return StoryIntroGenerationRequest(
        request_id="try-001",
        user_profile=StoryUserProfile(
            nickname="민준",
            persona="curious",
            companion_type="family",
            story_theme="time_traveler",
            story_tone="warm",
            age_group="child",
        ),
        places=[
            CoursePlaceInput(place_id="301", place_name="첨성대", sequence=1),
            CoursePlaceInput(place_id="302", place_name="불국사", sequence=2),
            CoursePlaceInput(place_id="303", place_name="석굴암", sequence=3),
        ],
        options=StoryGenerationOptions(),
        # use_rag=True 지만 StubRagService 가 빈 결과를 주므로,
        # 아래 rag_contexts 가 그대로 살아남는다.
        rag_contexts=[
            _rag_for_cheomseongdae(),
            _rag_for_bulguksa(),
            _rag_for_seokguram(),
        ],
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=1,
                        help="같은 입력으로 N번 호출해서 변주 비교")
    parser.add_argument("--no-rag", action="store_true",
                        help="rag_contexts 비우고 호출 (자료 부족 시 동작 확인)")
    args = parser.parse_args()

    request = _build_request()
    if args.no_rag:
        request = request.model_copy(update={
            "options": StoryGenerationOptions(use_rag=False),
            "rag_contexts": [],
        })

    service = StoryIntroService(
        rag_search_service=StubRagService(),
        chat_client=OpenAIChatClient(),
    )

    for i in range(1, args.runs + 1):
        print(f"\n========== RUN {i}/{args.runs} ==========")
        draft = service.generate_intro(request)
        print(json.dumps(draft.model_dump(), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
