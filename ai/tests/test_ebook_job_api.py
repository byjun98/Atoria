"""End-to-end API tests for POST /artifacts/ebook/jobs (157)."""
from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client():
    return TestClient(app)


def _payload(*, photo=True, choice=True):
    return {
        "story_id": 301, "user_id": 1,
        "story": {
            "title": "경주의 비밀을 푸는 두 꼬마 탐험가",
            "intro": "두 아이는 경주의 별빛을 찾아 여행을 시작했습니다.",
            "outro": "모든 단서를 모은 아이들은 경주의 기억을 마음에 담았습니다.",
            "protagonist_info": {
                "people_cnt": 2,
                "people_information": [
                    {"name": "민준", "age": 5, "tendency": "모험적"},
                    {"name": "성준", "age": 6, "tendency": "모험적"},
                ],
            },
        },
        "chapters": [
            {
                "sequence": 1, "place_id": 301, "place_name": "첨성대",
                "place_address": "경북 경주시 첨성로 140-25",
                "mission_title": "별빛 관측소에서 단서 찾기",
                "mission_description": "첨성대 앞에서 별자리 사진을 찍어보세요.",
                "mission_type": "PHOTO",
                "story_content": "첨성대에 도착한 두 아이는 별자리의 비밀을 발견했습니다.",
                "user_result": {
                    "image_url": "https://example.com/mission1.jpg" if photo else None,
                    "choice": None,
                },
            },
            {
                "sequence": 2, "place_id": 302, "place_name": "불국사",
                "place_address": "경북 경주시 불국로 385",
                "mission_title": "불국사의 비밀을 선택하라",
                "mission_description": "불국사가 품고 있는 진짜 의미는 무엇일까요?",
                "mission_type": "CHOICE",
                "story_content": "불국사에 선 두 아이는 두루마리 앞에서 고민했습니다.",
                "user_result": {
                    "image_url": None,
                    "choice": "불국사는 이상 세계를 표현한 사찰이다." if choice else None,
                },
            },
        ],
    }


def test_happy_path_returns_success_envelope(client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    assert r.status_code == 200
    body = r.json()
    assert body["success"] is True
    assert body["error"] is None
    assert body["timestamp"]
    assert body["data"]["story_id"] == 301


def test_response_meta_title_matches_request_story_title(client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    body = r.json()
    assert body["data"]["ebook_content"]["meta"]["title"] == \
        "경주의 비밀을 푸는 두 꼬마 탐험가"
    assert body["data"]["ebook_content"]["cover"]["title"] == \
        "경주의 비밀을 푸는 두 꼬마 탐험가"


def test_pages_use_only_allowed_wire_enums(client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    body = r.json()
    pages = body["data"]["ebook_content"]["pages"]
    allowed_types = {"COVER", "INTRO", "CHAPTER", "OUTRO", "BACK_COVER"}
    allowed_layouts = {
        "COVER", "TEXT_ONLY", "IMAGE_TOP_TEXT_BOTTOM",
        "TEXT_WITH_QUOTE", "BACK_COVER",
    }
    for p in pages:
        assert p["type"] in allowed_types
        assert p["layout"] in allowed_layouts


def test_photo_chapter_renders_image_top_layout(client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    body = r.json()
    pages = body["data"]["ebook_content"]["pages"]
    photo_chapter = next(p for p in pages if p.get("sequence") == 1)
    assert photo_chapter["type"] == "CHAPTER"
    assert photo_chapter["layout"] == "IMAGE_TOP_TEXT_BOTTOM"
    assert photo_chapter["image_url"] == "https://example.com/mission1.jpg"
    assert photo_chapter["quote"] is None


def test_choice_chapter_falls_back_to_text_only_in_mvp(client):
    """MVP — choice 만 있고 image_url 이 없는 chapter 는 TEXT_ONLY 로 떨어지고
    quote 는 절대 노출되지 않는다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    body = r.json()
    pages = body["data"]["ebook_content"]["pages"]
    choice_chapter = next(p for p in pages if p.get("sequence") == 2)
    assert choice_chapter["type"] == "CHAPTER"
    assert choice_chapter["layout"] == "TEXT_ONLY"
    assert choice_chapter["quote"] is None
    assert choice_chapter["image_url"] is None


def test_response_does_not_leak_internal_fields(client):
    """API.md 에 없는 draft_id / page_type / body / image_prompt / used_chunk_ids
    / source_urls / warnings 등은 외부 응답에 절대 나오지 않아야 한다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    raw = r.text
    forbidden = (
        "draft_id", "page_type", '"body"', "image_prompt",
        "used_chunk_ids", "source_urls", '"warnings"',
        "mission_result_status",
    )
    for token in forbidden:
        assert token not in raw, f"internal field leaked: {token}"


def test_back_cover_appended_last(client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    assert pages[-1]["type"] == "BACK_COVER"
    assert pages[-1]["layout"] == "BACK_COVER"


def test_page_count_matches_pages_length(client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    body = r.json()
    assert body["data"]["ebook_content"]["meta"]["page_count"] == \
        len(body["data"]["ebook_content"]["pages"])


def test_validation_error_returns_422(client):
    bad = _payload()
    del bad["story"]["title"]   # required field
    r = client.post("/artifacts/ebook/jobs", json=bad)
    assert r.status_code == 422


def test_response_envelope_has_no_job_id_or_status(client):
    """API.md 명세에 job_id / status 가 없으므로 외부 응답에도 노출 금지."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    body = r.json()
    assert "job_id" not in body
    assert "status" not in body
    assert "job_id" not in body["data"]
    assert "status" not in body["data"]


# ---- 157-quality-pass: cover title / choice → keyword 회귀 가드 -------------


def test_cover_page_title_matches_request_story_title(client):
    """COVER page title 은 meta.title / cover.title 과 일치해야 한다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    body = r.json()
    pages = body["data"]["ebook_content"]["pages"]
    wire_title = body["data"]["ebook_content"]["meta"]["title"]
    cover_obj_title = body["data"]["ebook_content"]["cover"]["title"]
    cover_page = next(p for p in pages if p["type"] == "COVER")
    assert wire_title == "경주의 비밀을 푸는 두 꼬마 탐험가"
    assert cover_obj_title == wire_title
    assert cover_page["title"] == wire_title


def test_choice_does_not_leak_into_keywords_paragraph(client):
    """ACTION/CHOICE 어떤 mission_type 이든, user_result.choice 가 외부 응답의
    '함께 고른 키워드는 …입니다' 문장으로 흘러 들어가지 않아야 한다."""
    payload = _payload()
    # ACTION 으로 보내고 choice 에 긴 문장을 넣어 옛 회귀 케이스를 재현 시도
    payload["chapters"][1]["mission_type"] = "ACTION"
    payload["chapters"][1]["user_result"]["choice"] = (
        "남쪽 창이 하늘을 향해 열려 있는 것 같았다."
    )
    r = client.post("/artifacts/ebook/jobs", json=payload)
    raw = r.text
    assert "함께 고른 키워드는 남쪽 창" not in raw
    assert "함께 고른 키워드는 불국사" not in raw
    # 어거지 종결 / 조사 회귀 가드
    assert "보세요는 것입니다" not in raw
    assert "입니다.입니다" not in raw
    assert "민준와(과)" not in raw
    assert "을(를)" not in raw


def test_choice_value_never_leaks_anywhere_in_mvp(client):
    """MVP — choice 는 어떤 필드로도 wire 응답에 노출되지 않아야 한다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    raw = r.text
    body = r.json()
    pages = body["data"]["ebook_content"]["pages"]
    photo_chapter = next(p for p in pages if p.get("sequence") == 1)
    choice_chapter = next(p for p in pages if p.get("sequence") == 2)
    # PHOTO chapter — image_url 만, quote None
    assert photo_chapter["image_url"] == "https://example.com/mission1.jpg"
    assert photo_chapter["quote"] is None
    # CHOICE chapter — quote 도 image_url 도 None
    assert choice_chapter["quote"] is None
    assert choice_chapter["image_url"] is None
    # 본문(text) 어디에도 choice 문자열이 들어가서는 안 된다
    assert "불국사는 이상 세계를 표현한 사찰이다." not in raw


# ---- 158: LLM narrative path (with fakes) ------------------------------


def _fake_narrative_payload(*, story_title="경주의 비밀을 푸는 두 꼬마 탐험가"):
    import json as _json
    return _json.dumps({
        "cover_title": story_title,
        "cover_subtitle": "별빛 지도를 따라간 하루",
        "thumbnail_hint": "별빛 지도가 펼쳐진 경주의 밤하늘",
        "symbolic_object": "별빛 지도",
        "supporting_character": "지도 위의 작은 빛",
        "story_question": "잃어버린 별빛은 어디에 숨어 있을까?",
        "story_arc": "별빛 지도가 두 장소를 거치며 단서를 모아 마지막에 별빛의 의미를 완성하는 이야기",
        "continuity_notes": [
            "첨성대에서 떠오른 작은 별이 불국사로 이어지는 길이 됨",
        ],
        "intro_page": {
            "title": "이야기의 시작",
            "text": (
                "민준의 손에는 오래된 별빛 지도가 들려 있었다.\n\n"
                "지도 위에는 아직 이름 없는 두 개의 빛이 조용히 반짝이고 있었다.\n\n"
                "“잃어버린 별빛은 어디에 숨어 있을까?” 민준은 작게 속삭였다."
            ),
        },
        "chapters": [
            {
                "sequence": 1,
                "title": "첫 번째 이야기, 첨성대의 별빛",
                "text": (
                    "민준은 첨성대 앞에 서자 자연스럽게 고개를 들었다. "
                    "둥근 돌탑 위로 하늘이 펼쳐졌다.\n\n"
                    "“저 창은 왜 하늘을 향해 있는 걸까요?” 민준이 물었다. "
                    "지도 위의 작은 빛이 살짝 흔들리며 대답했다. "
                    "“옛사람들은 하늘을 보며 계절과 시간을 읽으려 했다고 전해진단다.”\n\n"
                    "민준은 눈을 동그랗게 떴다. "
                    "별빛 지도 한쪽에 작은 별 하나가 조용히 떠올랐다."
                ),
                "caption": "첨성대의 한 장면",
            },
            {
                "sequence": 2,
                "title": "두 번째 이야기, 불국사의 부처의 자리",
                "text": (
                    "앞서 첨성대에서 떠오른 작은 별이 길을 가리키듯 흔들렸다. "
                    "민준은 그 빛을 따라 불국사 앞마당에 들어서며 길게 숨을 내쉬었다.\n\n"
                    "“이곳은 어떤 마음을 담고 있는 걸까요?” 민준이 물었다. "
                    "지도 위의 작은 빛이 가만히 흔들렸다. "
                    "“오래전 사람들은 이곳에 부처의 세계를 닮은 정원을 만들고 싶어 했다고 이야기된단다.”\n\n"
                    "민준은 마음이 따뜻해졌다. "
                    "별빛 지도 위에는 작은 등 같은 빛이 새로 켜졌다."
                ),
                "caption": "불국사의 한 장면",
            },
        ],
        "outro_page": {
            "title": "여행을 마치며",
            "text": (
                "여행을 마칠 때, 별빛 지도에는 첨성대의 하늘과 "
                "불국사의 햇살이 함께 남아 있었다.\n\n"
                "잃어버린 별빛은 멀리 있지 않았다. "
                "오늘 함께 본 풍경 안에 조용히 들어 있었다."
            ),
        },
        "back_cover": {
            "title": "여행은 계속됩니다",
            "text": "별빛 지도는 오늘 만난 모든 빛의 기억을 품은 채 두 사람의 손에 남았다.",
        },
    }, ensure_ascii=False)


@pytest.fixture
def fake_llm_path(monkeypatch):
    """Force the LLM path on and inject a scripted chat client."""
    from app.core.config import settings
    from app.services.ebook import ebook_narrative_service as ns_mod

    monkeypatch.setattr(settings, "EBOOK_NARRATIVE_USE_LLM", True)
    monkeypatch.setattr(settings, "EBOOK_NARRATIVE_MAX_RETRIES", 1)

    class _ScriptedClient:
        def __init__(self, response): self._response = response
        def create_json_chat_completion(self, messages, temperature=None):
            return self._response

    # patch OpenAIChatClient construction inside narrative service
    monkeypatch.setattr(ns_mod, "OpenAIChatClient",
                        lambda: _ScriptedClient(_fake_narrative_payload()))
    yield


def test_llm_path_returns_storybook_chapters(fake_llm_path, client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    assert r.status_code == 200
    body = r.json()
    pages = body["data"]["ebook_content"]["pages"]
    chapter_pages = [p for p in pages if p["type"] == "CHAPTER"]
    assert len(chapter_pages) == 2
    # storybook title
    assert chapter_pages[0]["title"].startswith("첫 번째 이야기, ")
    assert "별빛" in chapter_pages[0]["title"]
    # narrative text 가 fake LLM 응답에서 흘러왔는지 (장면 시작 + 상징 오브젝트)
    assert "불국사 앞마당" in chapter_pages[1]["text"]
    assert "별빛 지도" in chapter_pages[1]["text"]
    # 158-v4: ch2 가 ch1 단서를 이어받는 연결 신호 (앞서 / 그 빛 / 따라 등)
    assert any(cue in chapter_pages[1]["text"]
               for cue in ("앞서", "그 빛", "이어", "따라", "가리킨"))
    # mission_title 직접 노출 금지
    for p in chapter_pages:
        for mt in [c["mission_title"] for c in _payload()["chapters"]]:
            assert mt not in p["title"]


def test_llm_path_response_has_no_mission_report_phrases(fake_llm_path, client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    raw = r.text
    for forbidden in (
        "이곳에서의 미션은", "오늘의 미션", "미션 안내",
        "사진 1장", "사진 2장",
        "기록했습니다", "선택했습니다", "완료했습니다",
        "함께 고른 키워드", "보세요는 것입니다",
        "을(를)", "와(과)", "에서에서",
    ):
        assert forbidden not in raw, f"forbidden in LLM response: {forbidden}"


def test_llm_path_preserves_image_url_and_layout(fake_llm_path, client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    # PHOTO chapter (sequence=1) → IMAGE_TOP_TEXT_BOTTOM, image_url 보존
    p1 = next(p for p in pages if p.get("sequence") == 1)
    assert p1["layout"] == "IMAGE_TOP_TEXT_BOTTOM"
    assert p1["image_url"] == "https://example.com/mission1.jpg"
    # CHOICE chapter (sequence=2) → image_url 없음 → TEXT_ONLY, quote None
    p2 = next(p for p in pages if p.get("sequence") == 2)
    assert p2["layout"] == "TEXT_ONLY"
    assert p2["image_url"] is None
    assert p2["quote"] is None


def test_llm_path_chapters_open_with_scene_not_address(fake_llm_path, client):
    """CHAPTER text 첫 문장은 인물 행동 / 장면으로 시작 — 안내문 어휘 금지."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    chapter_pages = [p for p in pages if p["type"] == "CHAPTER"]
    for ch in chapter_pages:
        first_sentence = (ch["text"] or "").split(".", 1)[0]
        # 안내문 어휘 금지
        for forbidden in (
            "위치한", "자리한", "알려져 있습니다", "유적지로", "문화재로",
            "경상북도", "경주시", "통일신라의", "신라 시대의",
        ):
            assert forbidden not in first_sentence, (
                f"chapter first sentence is exposition-styled: '{first_sentence}'"
            )
        # protagonist name 또는 가족 등장
        assert ("민준" in ch["text"]) or ("성준" in ch["text"]) or ("가족" in ch["text"])


def test_llm_path_symbolic_object_carries_through(fake_llm_path, client):
    """상징 오브젝트가 intro / 모든 chapter / (outro 또는 back_cover) 에 등장."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    intro = next(p for p in pages if p["type"] == "INTRO")["text"]
    outro = next(p for p in pages if p["type"] == "OUTRO")["text"]
    back_cover = next(p for p in pages if p["type"] == "BACK_COVER")["text"] or ""
    chapters = [p["text"] for p in pages if p["type"] == "CHAPTER"]

    sym = "별빛 지도"
    assert sym in intro
    for ch_text in chapters:
        assert sym in ch_text, f"chapter does not carry symbolic object: {ch_text[:60]}…"
    assert (sym in outro) or (sym in back_cover)


def test_llm_path_response_does_not_expose_symbolic_object_field(fake_llm_path, client):
    """내부 narrative 의 symbolic_object 는 외부 wire response 에 노출되지 않는다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    raw = r.text
    assert "symbolic_object" not in raw  # API.md 에 없는 키 누수 가드


def test_llm_path_chapter_text_contains_dialogue(fake_llm_path, client):
    """각 CHAPTER text 에 한국어 둥근따옴표 대화 “…” 가 한 쌍 이상 들어 있어야 한다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    chapter_pages = [p for p in pages if p["type"] == "CHAPTER"]
    for ch in chapter_pages:
        text = ch["text"] or ""
        assert "“" in text and "”" in text, (
            f"chapter {ch.get('sequence')} has no Korean dialogue: {text[:80]}…"
        )


def test_llm_path_chapter_text_has_at_least_three_paragraphs(fake_llm_path, client):
    """각 CHAPTER text 는 \\n\\n 기준으로 3문단 이상이어야 한다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    chapter_pages = [p for p in pages if p["type"] == "CHAPTER"]
    for ch in chapter_pages:
        paragraphs = [p for p in (ch["text"] or "").split("\n\n") if p.strip()]
        assert len(paragraphs) >= 3, (
            f"chapter {ch.get('sequence')} has only {len(paragraphs)} paragraph(s)"
        )


def test_llm_path_intro_question_recovered_in_outro(fake_llm_path, client):
    """intro 의 질문 ('잃어버린 별빛 …') 이 outro 에서 어떤 형태로든 회수되어야 한다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    intro = next(p for p in pages if p["type"] == "INTRO")["text"]
    outro = next(p for p in pages if p["type"] == "OUTRO")["text"]
    assert "잃어버린 별빛" in intro
    # outro 에 질문의 핵심 키워드 일부가 회수되어야 한다
    assert "잃어버린 별빛" in outro or "별빛" in outro


def test_llm_path_response_does_not_expose_internal_fairy_tale_fields(fake_llm_path, client):
    """내부 supporting_character / story_question / story_arc / continuity_notes 모두
    wire response 에 노출되지 않는다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    raw = r.text
    for hidden_key in (
        "supporting_character", "story_question", "symbolic_object",
        "story_arc", "continuity_notes",
    ):
        assert hidden_key not in raw, f"internal key leaked: {hidden_key}"


def test_llm_path_chapter2_carries_link_signal(fake_llm_path, client):
    """ch2 본문이 ch1 단서 / 상징 오브젝트 / 연결 어구 중 하나를 갖고 있어야 한다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    chapter_pages = [p for p in pages if p["type"] == "CHAPTER"]
    assert len(chapter_pages) >= 2
    ch2_text = chapter_pages[1]["text"] or ""
    has_symbolic = "별빛 지도" in ch2_text
    has_cue = any(cue in ch2_text for cue in
                   ("앞서", "그 빛", "이어", "따라", "가리킨", "이끌", "조금 전"))
    assert has_symbolic or has_cue, (
        f"chapter 2 has no link to chapter 1: {ch2_text[:80]}…"
    )


def test_llm_path_chapter_endings_are_not_repetitive(fake_llm_path, client):
    """모든 chapter 가 같은 마지막 6글자 어미로 끝나는 회귀 가드."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    chapter_pages = [p for p in pages if p["type"] == "CHAPTER"]
    tails: list[str] = []
    for ch in chapter_pages:
        s = (ch["text"] or "").strip()
        while s and s[-1] in ".!?。":
            s = s[:-1]
        if s:
            tails.append(s[-6:])
    # chapter 가 2개뿐인 fixture 상황에서는 같은 6글자가 두 번까지는 허용
    if len(tails) >= 3:
        from collections import Counter
        most_common, n = Counter(tails).most_common(1)[0]
        assert n < 3, f"chapter endings repeat {n}x: '{most_common}'"


def test_llm_path_meta_and_cover_title_match_request(fake_llm_path, client):
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    body = r.json()
    wire_title = body["data"]["ebook_content"]["meta"]["title"]
    assert wire_title == "경주의 비밀을 푸는 두 꼬마 탐험가"
    assert body["data"]["ebook_content"]["cover"]["title"] == wire_title
    cover_page = next(
        p for p in body["data"]["ebook_content"]["pages"] if p["type"] == "COVER"
    )
    assert cover_page["title"] == wire_title


def test_llm_path_falls_back_when_violations_exhaust_retries(monkeypatch, client):
    """LLM 이 계속 금지 어구를 뱉으면 retry 후 deterministic fallback 으로 떨어진다.
    그 결과여도 wire schema 와 storybook tone 가드는 모두 유지된다."""
    from app.core.config import settings as _settings
    from app.services.ebook import ebook_narrative_service as ns_mod

    monkeypatch.setattr(_settings, "EBOOK_NARRATIVE_USE_LLM", True)
    monkeypatch.setattr(_settings, "EBOOK_NARRATIVE_MAX_RETRIES", 1)

    bad_payload = _fake_narrative_payload().replace(
        "별빛이 처음 깜빡이는 밤", "이곳에서의 미션은 별빛을 찾는 것이었다"
    )

    class _AlwaysBadClient:
        def create_json_chat_completion(self, messages, temperature=None):
            return bad_payload

    monkeypatch.setattr(ns_mod, "OpenAIChatClient", lambda: _AlwaysBadClient())

    r = client.post("/artifacts/ebook/jobs", json=_payload())
    assert r.status_code == 200      # API 는 200 유지
    raw = r.text
    # fallback 경로 결과여도 금지 어구는 안 나와야 함
    for forbidden in ("이곳에서의 미션은", "오늘의 미션", "사진 1장",
                      "기록했습니다", "선택했습니다", "함께 고른 키워드"):
        assert forbidden not in raw, f"forbidden leaked via fallback: {forbidden}"
    # API.md 추가 필드 비노출 (fallback 결과여도 wire schema 동일)
    body = r.json()
    assert "job_id" not in body and "status" not in body


def test_chapter_title_uses_storybook_tone(client):
    """wire mission_title 이 chapter title 에 그대로 박혀 있으면 안 된다.
    sequence 기반 이야기책 톤 ('첫 번째 이야기, …') 으로 합성되어야 함."""
    payload = _payload()
    r = client.post("/artifacts/ebook/jobs", json=payload)
    pages = r.json()["data"]["ebook_content"]["pages"]
    chapter_pages = [p for p in pages if p["type"] == "CHAPTER"]
    assert len(chapter_pages) == 2

    p1 = chapter_pages[0]   # sequence=1 첨성대
    p2 = chapter_pages[1]   # sequence=2 불국사

    assert p1["title"].startswith("첫 번째 이야기, ")
    assert "첨성대" in p1["title"]
    assert "별빛" in p1["title"]

    assert p2["title"].startswith("두 번째 이야기, ")
    assert "불국사" in p2["title"]

    # request 의 mission_title 이 page.title 에 직접 노출되어선 안 된다
    request_titles = [c["mission_title"] for c in payload["chapters"]]
    for mt in request_titles:
        assert mt not in p1["title"]
        assert mt not in p2["title"]


def test_chapter_title_has_no_mission_form_words(client):
    """CHAPTER title 에 '미션 / 선택하라 / 찾기 / 포착하기' 같은 미션형 표현
    노출 금지."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    for p in pages:
        if p["type"] != "CHAPTER":
            continue
        for forbidden in ("미션", "선택하라", "찾기", "포착하기", "조사"):
            assert forbidden not in p["title"], (
                f"chapter title '{p['title']}' leaks mission-form word '{forbidden}'"
            )


def test_no_text_with_quote_layout_anywhere(client):
    """MVP — TEXT_WITH_QUOTE layout 자체를 사용하지 않는다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    pages = r.json()["data"]["ebook_content"]["pages"]
    for p in pages:
        assert p["layout"] != "TEXT_WITH_QUOTE"


def test_response_text_is_storybook_tone(client):
    """본문에 '미션 / 기록했습니다 / 완료했습니다 / 함께 고른 키워드' 같은
    결과 보고식 표현이 절대 등장하지 않는다."""
    r = client.post("/artifacts/ebook/jobs", json=_payload())
    raw = r.text
    for forbidden in (
        "미션 안내", "오늘의 미션은 다음과 같습니다",
        "이곳에서의 미션", "미션을 완료했습니다",
        "기록했습니다", "선택했습니다", "완료했습니다",
        "함께 고른 키워드", "내가 남긴 한 줄",
        "보세요는 것입니다", "사진 1장이 함께 남았습니다",
    ):
        assert forbidden not in raw, f"forbidden phrase leaked into wire: {forbidden}"
