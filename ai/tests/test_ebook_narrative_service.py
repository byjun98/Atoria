"""Tests for EbookNarrativeService — fakes, no real OpenAI."""
from __future__ import annotations

import json

import pytest

from app.schemas.story import (
    ChapterUserResult,
    EbookChapter,
    EbookJobRequest,
    PersonInfo,
    ProtagonistInfo,
    StoryBlock,
)
from app.services.ebook.ebook_narrative_service import (
    EbookNarrativeService,
    NarrativeGenerationFailed,
)


# ---- helpers -----------------------------------------------------------


def _wire_request():
    return EbookJobRequest(
        story_id=301, user_id=1,
        story=StoryBlock(
            title="경주의 비밀", intro="시작합니다.", outro="마칩니다.",
            protagonist_info=ProtagonistInfo(
                people_cnt=1,
                people_information=[PersonInfo(name="민준", age=5, tendency="모험적")],
            ),
        ),
        chapters=[
            EbookChapter(
                sequence=1, place_id=301, place_name="첨성대",
                place_address="경북 경주시", mission_title="별빛 찾기",
                mission_description="d", mission_type="PHOTO",
                story_content="첨성대 본문.",
                user_result=ChapterUserResult(image_url="https://x/p.jpg", choice=None),
            ),
            EbookChapter(
                sequence=2, place_id=302, place_name="동궁과월지",
                place_address="경북 경주시", mission_title="달빛 보기",
                mission_description="d", mission_type="PHOTO",
                story_content="월지 본문.",
                user_result=ChapterUserResult(image_url=None, choice=None),
            ),
        ],
    )


_GOOD_INTRO = (
    "민준의 손에는 오래된 별빛 지도가 들려 있었다.\n\n"
    "지도 위에는 아직 이름 없는 두 개의 빛이 조용히 반짝이고 있었다.\n\n"
    "“잃어버린 별빛은 어디에 숨어 있을까?” 민준은 작게 속삭였다."
)
_GOOD_CH1 = (
    "민준은 첨성대 앞에 서자 자연스럽게 고개를 들었다. "
    "둥근 돌탑 위로 하늘이 천천히 펼쳐졌다.\n\n"
    "“저 창은 왜 하늘을 향해 있는 걸까요?” 민준이 물었다. "
    "지도 위의 작은 빛이 살짝 흔들리며 대답했다. "
    "“옛사람들은 하늘을 보며 계절과 시간을 읽으려 했다고 전해진단다.”\n\n"
    "민준은 눈을 동그랗게 떴다. "
    "그 순간 별빛 지도 한쪽에 작은 별 하나가 조용히 떠올랐다."
)
_GOOD_CH2 = (
    "앞서 첨성대에서 떠오른 작은 별이 길을 가리키듯 흔들렸다. "
    "민준은 그 빛을 따라 월지의 물가에 다가서며 발걸음을 멈췄다.\n\n"
    "“이 물은 어떤 밤을 기억하고 있을까요?” 민준이 가만히 물었다. "
    "지도 위의 작은 빛이 또 한 번 흔들렸다. "
    "“오래전 사람들은 이 연못 위에서 오래 머무는 달빛을 바라봤다고 이야기된단다.”\n\n"
    "민준은 마음이 따뜻해졌다. "
    "별빛 지도 위에는 은은한 달빛 선이 천천히 번져 갔다."
)
_GOOD_OUTRO = (
    "여행을 마칠 때, 별빛 지도에는 첨성대의 하늘과 월지의 물빛이 함께 남아 있었다.\n\n"
    "잃어버린 별빛은 멀리 있는 게 아니었다. "
    "오늘 함께 본 풍경 안에 조용히 들어 있었다."
)
_GOOD_BACK = (
    "별빛 지도는 오늘 만난 모든 빛의 기억을 품은 채 두 사람의 손에 남았다."
)


def _good_payload(*, intro=_GOOD_INTRO,
                  ch1_text=_GOOD_CH1,
                  ch1_title="첫 번째 이야기, 첨성대의 별빛",
                  ch2_text=_GOOD_CH2,
                  ch2_title="두 번째 이야기, 월지에 비친 달빛",
                  outro=_GOOD_OUTRO,
                  back_cover=_GOOD_BACK,
                  symbolic_object="별빛 지도",
                  supporting_character="지도 위의 작은 빛",
                  story_question="잃어버린 별빛은 어디에 숨어 있을까?",
                  story_arc=("별빛 지도가 두 장소를 거치며 단서를 모아 "
                             "마지막에 잃어버린 별빛의 의미를 완성하는 이야기"),
                  continuity_notes=None):
    if continuity_notes is None:
        continuity_notes = [
            "첨성대에서 떠오른 작은 별이 다음 장소로 이어지는 길이 됨",
        ]
    return json.dumps({
        "cover_title": "경주의 비밀",
        "cover_subtitle": "별빛을 따라간 하루",
        "thumbnail_hint": "별빛이 비치는 첨성대의 둥근 실루엣",
        "symbolic_object": symbolic_object,
        "supporting_character": supporting_character,
        "story_question": story_question,
        "story_arc": story_arc,
        "continuity_notes": list(continuity_notes),
        "intro_page": {"title": "이야기의 시작", "text": intro},
        "chapters": [
            {"sequence": 1, "title": ch1_title, "text": ch1_text,
             "caption": "첨성대의 한 장면"},
            {"sequence": 2, "title": ch2_title, "text": ch2_text,
             "caption": "월지의 한 장면"},
        ],
        "outro_page": {"title": "여행을 마치며", "text": outro},
        "back_cover": {"title": "여행은 계속됩니다", "text": back_cover},
    }, ensure_ascii=False)


class _ScriptedClient:
    """Returns pre-made JSON strings, one per call."""
    def __init__(self, responses, *, raise_exc=None):
        self._responses = list(responses)
        self._raise = raise_exc
        self.calls: list[list[dict]] = []

    def create_json_chat_completion(self, messages, temperature=None):
        self.calls.append(list(messages))
        if self._raise:
            raise self._raise
        return self._responses.pop(0)


# ---- tests --------------------------------------------------------------


def test_normal_flow_parses_into_narrative_draft():
    svc = EbookNarrativeService(chat_client=_ScriptedClient([_good_payload()]))
    draft = svc.generate_narrative(_wire_request())
    assert draft.cover_title == "경주의 비밀"
    assert len(draft.chapters) == 2
    assert draft.chapters[0].title.startswith("첫 번째 이야기, ")
    assert draft.chapters[1].title.startswith("두 번째 이야기, ")
    assert draft.thumbnail_hint


def test_forbidden_phrase_in_intro_triggers_repair_then_pass():
    """첫 응답에 '미션' 이 들어가면 1회 repair 후 정상 응답으로 통과."""
    bad = _good_payload(intro="이곳에서의 미션은 별빛을 찾는 것이었다.")
    good = _good_payload()
    client = _ScriptedClient([bad, good])
    svc = EbookNarrativeService(chat_client=client, max_retries=1)
    draft = svc.generate_narrative(_wire_request())
    assert len(client.calls) == 2  # 원본 + repair
    repair_user = client.calls[1][-1]["content"]
    assert "[REPAIR_INSTRUCTION]" in repair_user
    assert draft.cover_title == "경주의 비밀"


def test_forbidden_phrase_after_retry_raises():
    """retry 후에도 금지 어구 → NarrativeGenerationFailed."""
    bad = _good_payload(intro="이곳에서의 미션은 별빛이었다.")
    client = _ScriptedClient([bad, bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=1)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert ei.value.code == "EBOOK_LLM_FORBIDDEN_PHRASE"
    assert any("미션" in v for v in ei.value.violations)
    assert len(client.calls) == 2  # max 1 retry


def test_chapter_title_forbidden_word_blocks():
    """chapter title 에 '찾기' / '포착하기' 같은 미션형 어휘 금지."""
    bad = _good_payload(ch1_title="첨성대의 별 찾기")
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("찾기" in v for v in ei.value.violations)


def test_invalid_json_raises_parse_error():
    client = _ScriptedClient(["not valid json"])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert ei.value.code == "EBOOK_LLM_INVALID_JSON"


def test_chapter_sequence_mismatch_raises():
    """request 에 2개 chapter 가 있는데 LLM 이 1개만 주면 실패."""
    only_one = json.dumps({
        "cover_title": "x", "intro_page": {"title": "t", "text": "i"},
        "chapters": [{"sequence": 1, "title": "첫 번째 이야기, 첨성대의 별빛",
                       "text": "x", "caption": "c"}],
        "outro_page": {"title": "t", "text": "o"},
        "back_cover": {"title": "t", "text": "b"},
    }, ensure_ascii=False)
    client = _ScriptedClient([only_one])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert ei.value.code == "EBOOK_LLM_CHAPTER_MISMATCH"


def test_openai_api_error_propagates_as_failure():
    client = _ScriptedClient([], raise_exc=RuntimeError("openai down"))
    svc = EbookNarrativeService(chat_client=client, max_retries=1)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert ei.value.code == "EBOOK_LLM_API_ERROR"


def test_code_fence_wrapper_is_stripped():
    fenced = "```json\n" + _good_payload() + "\n```"
    svc = EbookNarrativeService(chat_client=_ScriptedClient([fenced]))
    draft = svc.generate_narrative(_wire_request())
    assert draft.cover_title == "경주의 비밀"


# ---- 158-v2: scene opening + symbolic object validation -----------------


def test_chapter_opening_address_style_triggers_violation():
    """첫 문장이 '경상북도 경주시 인왕동에 자리한 첨성대는...' → EBOOK_LLM_EXPOSITION_OPENING."""
    bad = _good_payload(ch1_text=(
        "경상북도 경주시 인왕동에 자리한 첨성대는 신라의 천문 관측 시설이다. "
        "민준은 잠시 그 앞에 멈춰 섰다."
    ))
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_EXPOSITION_OPENING" in v for v in ei.value.violations)


def test_chapter_opening_place_as_subject_triggers_violation():
    """'첨성대는 …' 처럼 장소 주어 설명문으로 시작 → violation."""
    # title 안의 한국어 토큰을 후보로 쓰므로 chapter title 에 '첨성대' 가 들어가야 한다.
    bad = _good_payload(
        ch1_title="첫 번째 이야기, 첨성대의 별빛",
        ch1_text=(
            "첨성대는 신라 시대의 천문 관측 시설이다. 민준은 그 앞에 잠시 머물렀다."
        ),
    )
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_EXPOSITION_OPENING" in v for v in ei.value.violations)


def test_missing_symbolic_object_field_violates():
    bad = _good_payload(symbolic_object=None)
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_SYMBOLIC_OBJECT_MISSING" in v for v in ei.value.violations)


def test_symbolic_object_underused_in_chapter_violates():
    """symbolic_object 가 chapter 본문에 한 번도 등장하지 않으면 underused violation."""
    bad = _good_payload(
        ch2_text=(
            "민준은 월지의 물가에 다가서며 발걸음을 멈췄다. "
            "연못 위에 흔들리는 누각의 그림자가 오래된 신라의 밤을 불러왔다."
        )  # ← '별빛 지도' 어구가 빠짐
    )
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_SYMBOLIC_OBJECT_UNDERUSED" in v for v in ei.value.violations)


def test_repair_after_exposition_opening_succeeds():
    """첫 시도 안내문 오프닝 → repair 후 장면 오프닝으로 통과."""
    bad = _good_payload(ch1_text=(
        "경상북도 경주시 인왕동에 자리한 첨성대는 신라의 천문 관측 시설이다. "
        "민준은 그 앞에 멈춰 섰다."
    ))
    good = _good_payload()
    client = _ScriptedClient([bad, good])
    svc = EbookNarrativeService(chat_client=client, max_retries=1)
    draft = svc.generate_narrative(_wire_request())
    assert len(client.calls) == 2
    assert draft.symbolic_object == "별빛 지도"
    # repair user prompt 가 장면 시작 / 상징 오브젝트 규칙을 포함
    repair_user = client.calls[1][-1]["content"]
    # 158-v3 repair instruction 어구
    assert "장소 설명" in repair_user
    assert "symbolic_object" in repair_user
    assert "대화" in repair_user
    assert "3문단" in repair_user


# ---- 158-v3 fairy-tale: dialogue / paragraphs / supporting_character / question -----


def test_chapter_without_dialogue_violates():
    """챕터 본문에 한국어 대화 따옴표가 없으면 EBOOK_LLM_DIALOGUE_MISSING."""
    no_dialogue = (
        "민준은 첨성대 앞에 서자 자연스럽게 고개를 들었다. "
        "둥근 돌탑 위로 하늘이 펼쳐졌다.\n\n"
        "둘러보던 그는 눈을 동그랗게 떴다. "
        "별빛 지도 한쪽에 작은 별 하나가 조용히 떠올랐다.\n\n"
        "그 빛은 천천히 흔들렸다."
    )
    bad = _good_payload(ch1_text=no_dialogue)
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_DIALOGUE_MISSING" in v for v in ei.value.violations)


def test_chapter_with_too_few_paragraphs_violates():
    """\\n\\n 기준 3문단 미만이면 EBOOK_LLM_CHAPTER_TOO_FLAT."""
    one_para = (
        "민준은 첨성대 앞에 서자 자연스럽게 고개를 들었다. "
        "둥근 돌탑 위로 하늘이 펼쳐졌다. "
        "“저 창은 왜 하늘을 향해 있을까요?” 민준이 물었고 "
        "별빛 지도 위에 작은 별이 떠올랐다."
    )
    bad = _good_payload(ch1_text=one_para)
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_CHAPTER_TOO_FLAT" in v for v in ei.value.violations)


def test_missing_supporting_character_violates():
    bad = _good_payload(supporting_character=None)
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_SUPPORTING_CHARACTER_MISSING" in v for v in ei.value.violations)


def test_missing_story_question_violates():
    bad = _good_payload(story_question=None)
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_STORY_QUESTION_MISSING" in v for v in ei.value.violations)


def test_repair_after_fairy_tale_violations_succeeds():
    """첫 응답에 대화 / 보조 인물 / 질문 누락 → repair 후 통과."""
    no_dialogue = (
        "민준은 첨성대 앞에 서자 고개를 들었다. 둥근 돌탑 위로 하늘이 펼쳐졌다.\n\n"
        "잠시 후 별빛 지도 한쪽에 작은 별 하나가 떠올랐다.\n\n"
        "민준은 그 빛을 따라 걸었다."
    )
    bad = _good_payload(
        ch1_text=no_dialogue,
        supporting_character=None,
        story_question=None,
    )
    good = _good_payload()
    client = _ScriptedClient([bad, good])
    svc = EbookNarrativeService(chat_client=client, max_retries=1)
    draft = svc.generate_narrative(_wire_request())
    assert len(client.calls) == 2
    assert draft.supporting_character == "지도 위의 작은 빛"
    assert draft.story_question == "잃어버린 별빛은 어디에 숨어 있을까?"


# ---- 158-v4 continuous-story: continuity / link / repetitive pattern ---


def test_missing_continuity_notes_violates():
    """chapters 가 2개 이상이면 continuity_notes 가 N-1 개 이상 있어야 한다."""
    bad = _good_payload(continuity_notes=[])
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_CONTINUITY_MISSING" in v for v in ei.value.violations)


def test_chapter_two_without_link_signal_violates():
    """ch2 의 본문에 symbolic_object 도 없고 연결 어구도 없으면 violation."""
    no_link_ch2 = (
        "민준은 월지의 물가에 다가서며 발걸음을 멈췄다. "
        "연못이 조용히 일렁였다.\n\n"
        "“이 물은 어떤 밤을 기억하나요?” 민준이 물었다. "
        "엄마가 미소 지었다. "
        "“오래전 사람들은 물에 비친 달을 오래 바라봤단다.”\n\n"
        "민준은 마음이 따뜻해졌다."
    )
    bad = _good_payload(ch2_text=no_link_ch2)
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(_wire_request())
    assert any("EBOOK_LLM_CHAPTER_LINK_MISSING" in v for v in ei.value.violations)


def test_repetitive_chapter_ending_pattern_violates():
    """모든 chapter 가 같은 어미로 끝나면 violation. (3 chapter 이상 필요)"""
    # 3 chapter 짜리 wire request 가 필요. 임시 wire_request 확장.
    from app.schemas.story import (
        ChapterUserResult, EbookChapter, EbookJobRequest,
        PersonInfo, ProtagonistInfo, StoryBlock,
    )
    req3 = EbookJobRequest(
        story_id=301, user_id=1,
        story=StoryBlock(
            title="경주의 비밀", intro="시작", outro="끝",
            protagonist_info=ProtagonistInfo(
                people_cnt=1,
                people_information=[PersonInfo(name="민준", age=5, tendency="모험적")],
            ),
        ),
        chapters=[
            EbookChapter(sequence=i, place_id=300+i, place_name=name,
                         place_address="경북", mission_title="t",
                         mission_description="d", mission_type="PHOTO",
                         story_content="s",
                         user_result=ChapterUserResult(image_url=f"https://x/{i}.jpg",
                                                       choice=None))
            for i, name in [(1, "첨성대"), (2, "동궁과월지"), (3, "에밀레종")]
        ],
    )
    same_tail = "기억 조각은 새로운 빛을 담았다"
    text_template = (
        "민준은 {place} 앞에 서서 풍경을 바라보았다.\n\n"
        "“이곳은 무엇을 기억할까요?” 민준이 물었다. "
        "지도 위의 작은 빛이 흔들렸다. "
        "“오래된 사람들의 마음이 남아 있다고 전해진단다.”\n\n"
        "앞서 떠오른 별빛이 길을 이었다. " + same_tail + "."
    )
    bad = json.dumps({
        "cover_title": "x", "cover_subtitle": "x", "thumbnail_hint": "x",
        "symbolic_object": "별빛 지도",
        "supporting_character": "지도 위의 작은 빛",
        "story_question": "잃어버린 별빛은 어디 있을까?",
        "story_arc": "별빛 지도가 단서를 모으는 이야기",
        "continuity_notes": ["ch1→ch2", "ch2→ch3"],
        "intro_page": {"title": "i", "text": (
            "민준은 별빛 지도를 펼쳤다.\n\n"
            "지도 위에 두 개의 빛이 깜빡였다.\n\n"
            "“잃어버린 별빛은 어디에 있을까?” 민준은 속삭였다."
        )},
        "chapters": [
            {"sequence": 1, "title": "첫 번째 이야기, 첨성대의 별빛",
             "text": text_template.format(place="첨성대"), "caption": "c"},
            {"sequence": 2, "title": "두 번째 이야기, 월지의 달빛",
             "text": text_template.format(place="동궁과월지"), "caption": "c"},
            {"sequence": 3, "title": "세 번째 이야기, 에밀레종의 울림",
             "text": text_template.format(place="에밀레종"), "caption": "c"},
        ],
        "outro_page": {"title": "o", "text": (
            "여행을 마치고 별빛 지도가 완성되었다.\n\n"
            "잃어버린 별빛은 함께 본 풍경 안에 있었다."
        )},
        "back_cover": {"title": "b",
                        "text": "별빛 지도는 모든 빛의 기억을 품고 남았다."},
    }, ensure_ascii=False)
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed) as ei:
        svc.generate_narrative(req3)
    assert any("EBOOK_LLM_REPETITIVE_CHAPTER_PATTERN" in v for v in ei.value.violations)


def test_repair_after_continuity_violations_succeeds():
    bad = _good_payload(continuity_notes=[])
    good = _good_payload()
    client = _ScriptedClient([bad, good])
    svc = EbookNarrativeService(chat_client=client, max_retries=1)
    draft = svc.generate_narrative(_wire_request())
    assert len(client.calls) == 2
    assert draft.continuity_notes
    assert draft.story_arc


def test_max_retries_zero_means_no_repair():
    bad = _good_payload(intro="이곳에서의 미션은 별빛이었다.")
    client = _ScriptedClient([bad])
    svc = EbookNarrativeService(chat_client=client, max_retries=0)
    with pytest.raises(NarrativeGenerationFailed):
        svc.generate_narrative(_wire_request())
    assert len(client.calls) == 1
