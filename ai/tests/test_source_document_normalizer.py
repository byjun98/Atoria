"""Tests for SourceDocumentNormalizer."""
from __future__ import annotations

import pytest

from app.services.chunking.source_document_normalizer import SourceDocumentNormalizer


HERITAGE_CONTEXT_SAMPLE = {
    "source_type": "heritage_context",
    "source_site": "한국민족문화대백과",
    "source_url": "https://encykorea.aks.ac.kr/Article/E0002916",
    "region": "경상북도 경주",
    "era": "신라",
    "related_heritages": ["동궁과월지"],
    "related_people": ["문무왕", "혜공왕"],
    "related_places": ["경주", "안압지", "동궁", "월지"],
    "motifs": ["왕권", "동물", "천문"],
    "tone_tags": [],
    "story_hooks": [],
    "factuality_level": "history",
    "metadata": {"crawl_source": "web", "needs_review": False},
    "record_id": "heritage-donggung-wolji-encykorea-001",
    "heritage_name": "동궁과월지",
    "title": "동궁과월지 - 한국민족문화대백과",
    "definition": "경상북도 경주시에 있는 남북국시대 통일신라의 별궁이 자리했던 궁궐터.",
    "summary": (
        "경주 동궁과 월지\n慶州 東宮과 月池\n慶\n하례하다 [경]\n부수 心 총획 14\n"
        "州\n나라 이름 [주]\n부수 巛 총획 6\n"
        "예술·체육\n유적\n남북국\n국가문화유산\n"
        "경상북도 경주시에 있는 남북국시대 통일신라의 별궁이 자리했던 궁궐터.\n"
        "명칭\n경주 동궁과 월지"
    ),
    "key_facts": {},
    "narrative_excerpts": [],
}

LEGEND_SAMPLE_MIXED = {
    "legend_id": "legend_donggung_wolji_gyeongsun_001",
    "title": "달빛 비치는 연못, 동궁과 월지의 마지막 잔치",
    "heritage_name": "동궁과월지",
    "related_heritages": ["동궁과월지"],
    "related_fact_record_ids": ["heritage-donggung-wolji-encykorea-001"],
    "source_type": "historical_anecdote",
    "narrative_type": "historical_episode",
    "factuality_level": "mixed",
    "primary_source_site": "우리역사넷",
    "source_sites": ["우리역사넷"],
    "source_urls": ["https://contents.history.go.kr/mobile/eh/view.do?levelId=eh_r0090_0010"],
    "source_confidence": "high",
    "needs_review": False,
    "source_note": "재구성 데이터",
    "folklore_status": "associated_historical_scene",
    "region": "경상북도 경주",
    "era": "신라 문무왕~신라 말",
    "related_people": ["문무왕", "경순왕", "왕건"],
    "related_places": ["경주", "동궁", "월지"],
    "motifs": ["연못", "달빛"],
    "tone_tags": ["고요", "쓸쓸함"],
    "raw_text": "우리역사넷 자료에 따르면 월지는 674년 문무왕 때 만들어진 신라 왕궁의 연못으로 알려져 있다. 신라 말 경순왕은 이곳에서 왕건을 맞아 잔치를 베풀었다고 이야기된다.",
    "story_summary": "월지의 잔치 장면.",
    "story_hooks": ["연못을 바라보는 장면"],
    "mission_keywords": ["연못", "달빛"],
    "is_reconstructed": True,
    "metadata": {"language": "ko"},
}

LEGEND_SAMPLE_SYMBOLIC = {
    **LEGEND_SAMPLE_MIXED,
    "legend_id": "legend_dabotap_symbol_001",
    "factuality_level": "symbolic",
    "narrative_type": "symbolic_story",
    "folklore_status": "symbolic_only",
}


@pytest.fixture
def normalizer() -> SourceDocumentNormalizer:
    return SourceDocumentNormalizer()


def test_heritage_context_basic_mapping(normalizer):
    doc = normalizer.normalize(HERITAGE_CONTEXT_SAMPLE)
    assert doc.source_record_id == "heritage-donggung-wolji-encykorea-001"
    assert doc.source_type == "heritage_context"
    assert doc.content_role == "fact_context"
    assert doc.factuality_level == "history"
    assert doc.heritage_name == "동궁과월지"
    assert doc.source_urls == ["https://encykorea.aks.ac.kr/Article/E0002916"]
    assert doc.region == "경상북도 경주"
    assert "문무왕" in doc.related_people


def test_heritage_context_summary_noise_is_filtered(normalizer):
    doc = normalizer.normalize(HERITAGE_CONTEXT_SAMPLE)
    # Definition must remain.
    assert "별궁이 자리했던 궁궐터" in doc.text
    # Hanja gloss like '하례하다 [경]' and radical lines like '부수 心 총획 14' must be gone.
    assert "하례하다" not in doc.text
    assert "부수 心" not in doc.text
    assert "총획" not in doc.text
    # Single hanja chars and bare labels should be gone.
    assert "\n慶\n" not in doc.text
    assert "국가문화유산" not in doc.text


def test_heritage_context_routing_dispatches_correctly(normalizer):
    # No 'legend_id', has 'record_id' → goes to heritage_context path.
    doc = normalizer.normalize(HERITAGE_CONTEXT_SAMPLE)
    assert doc.content_role == "fact_context"


def test_legend_basic_mapping(normalizer):
    doc = normalizer.normalize(LEGEND_SAMPLE_MIXED)
    assert doc.source_record_id == "legend_donggung_wolji_gyeongsun_001"
    assert doc.source_type == "historical_anecdote"
    assert doc.content_role == "legend_material"
    assert doc.factuality_level == "mixed"
    assert doc.text.startswith("우리역사넷 자료에 따르면")
    assert doc.source_site == "우리역사넷"
    assert doc.source_urls and doc.source_urls[0].startswith("https://contents.history.go.kr")
    assert doc.mission_keywords == ["연못", "달빛"]


def test_legend_symbolic_routes_to_symbolic_material(normalizer):
    doc = normalizer.normalize(LEGEND_SAMPLE_SYMBOLIC)
    assert doc.content_role == "symbolic_material"
    assert doc.factuality_level == "symbolic"


def test_unknown_item_raises(normalizer):
    with pytest.raises(ValueError):
        normalizer.normalize({"foo": "bar"})


def test_normalize_many(normalizer):
    out = normalizer.normalize_many([HERITAGE_CONTEXT_SAMPLE, LEGEND_SAMPLE_MIXED])
    assert len(out) == 2
    assert {d.content_role for d in out} == {"fact_context", "legend_material"}
