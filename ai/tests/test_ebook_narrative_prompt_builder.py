"""Tests for EbookNarrativePromptBuilder."""
from __future__ import annotations

from app.schemas.story import (
    ChapterUserResult,
    EbookChapter,
    EbookJobRequest,
    PersonInfo,
    ProtagonistInfo,
    StoryBlock,
)
from app.services.ebook.ebook_narrative_prompt_builder import (
    EBOOK_NARRATIVE_OUTPUT_INSTRUCTION,
    EBOOK_NARRATIVE_SYSTEM_PROMPT,
    EbookNarrativePromptBuilder,
    NARRATIVE_FORBIDDEN_PHRASES,
)


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
                place_address="경북 경주시", mission_title="별빛 관측소에서 단서 찾기",
                mission_description="첨성대 앞에서 별자리 사진을 찍어보세요.",
                mission_type="PHOTO", story_content="첨성대에 도착했습니다.",
                user_result=ChapterUserResult(image_url="https://x/p.jpg", choice=None),
            ),
        ],
    )


def test_build_messages_returns_system_and_user():
    msgs = EbookNarrativePromptBuilder().build_messages(_wire_request())
    assert len(msgs) == 2
    assert msgs[0]["role"] == "system"
    assert msgs[1]["role"] == "user"


def test_system_prompt_explains_storybook_policy():
    sys_text = EBOOK_NARRATIVE_SYSTEM_PROMPT
    assert "이야기책" in sys_text
    assert "미션 수행 결과 보고서가 아니라" in sys_text
    # 전체 서사 장치 규칙
    assert "전체 여행을 관통하는 상징" in sys_text
    # 자료 사용 규칙 — mission_title / mission_description 노출 금지
    assert "mission_title" in sys_text
    assert "mission_description" in sys_text
    assert "본문에 절대 직접 노출하지 않는다" in sys_text


def test_system_prompt_lists_forbidden_phrases():
    sys_text = EBOOK_NARRATIVE_SYSTEM_PROMPT
    # 핵심 금지 어휘 몇 가지가 시스템 프롬프트에 인용되어야 한다
    for must_show in ("미션", "완료했습니다", "기록했습니다", "함께 고른 키워드",
                       "사진 1장", "보세요는 것입니다", "에서에서"):
        assert must_show in sys_text


def test_system_prompt_lists_chapter_title_forbidden_tokens():
    sys_text = EBOOK_NARRATIVE_SYSTEM_PROMPT
    for tok in ("찾기", "포착하기", "선택하라", "조사", "탐험"):
        assert tok in sys_text


def test_user_prompt_carries_request_payload_and_json_only_rule():
    msgs = EbookNarrativePromptBuilder().build_messages(_wire_request())
    user = msgs[1]["content"]
    # request payload 의 핵심 값
    assert "경주의 비밀" in user
    assert "첨성대" in user
    assert "민준" in user
    assert "story_content" in user
    # mission_title 은 reference-only 라벨로 전달되며, 본문에 박지 말라는 안내가 포함됨
    assert "_internal_mission_title_for_reference_only" in user
    assert "본문에 그대로 박지 말고" in user
    # output schema 안내
    assert "OUTPUT_JSON_SCHEMA" in user
    assert "cover_title" in user and "intro_page" in user and "back_cover" in user


def test_output_instruction_warns_against_mission_title_passthrough():
    out = EBOOK_NARRATIVE_OUTPUT_INSTRUCTION
    assert "mission_title 을 그대로 박지 않는다" in out
    assert "JSON" in out


def test_repair_messages_append_violation_block():
    builder = EbookNarrativePromptBuilder()
    repaired = builder.build_repair_messages(
        _wire_request(),
        violations=["intro_page.text contains '미션'", "chapters[1].title contains '찾기'"],
    )
    assert len(repaired) == 2
    user = repaired[1]["content"]
    assert "[REPAIR_INSTRUCTION]" in user
    assert "미션" in user and "찾기" in user
    assert "JSON" in user
    # 158-v2 강화 — 안내문 어휘 회피 / symbolic_object
    assert "위치한" in user and "자리한" in user
    assert "symbolic_object" in user
    # 158-v3 fairy-tale 강화 — 대화 / 3문단 / 보조 인물 / 질문
    assert "대화" in user
    assert "3문단" in user
    assert "보조 인물" in user
    assert "질문" in user


# ---- 158-v2: scene-style + symbolic object ------------------------------


def test_system_prompt_has_storybook_scene_style_section():
    sys_text = EBOOK_NARRATIVE_SYSTEM_PROMPT
    assert "[STORYBOOK_SCENE_STYLE]" in sys_text
    # bad opening 예시 (안내문 톤) 인용
    assert "경상북도 경주시 인왕동에 자리한 첨성대는" in sys_text
    # good opening 예시 (인물 행동) 인용
    assert "민준은 첨성대 앞에 서자" in sys_text
    # 첫 문장 규칙 명시
    assert "인물 행동 / 감각 / 시선 / 분위기 중 하나로 시작" in sys_text


def test_system_prompt_has_symbolic_object_rule_section():
    sys_text = EBOOK_NARRATIVE_SYSTEM_PROMPT
    assert "[SYMBOLIC_OBJECT_RULE]" in sys_text
    # 후보 목록 일부 인용 검증
    for cand in ("별빛 지도", "작은 등불", "기억 조각", "오래된 나침반", "연꽃등"):
        assert cand in sys_text


def test_user_prompt_has_narrative_quality_requirements():
    msgs = EbookNarrativePromptBuilder().build_messages(_wire_request())
    user = msgs[1]["content"]
    assert "[NARRATIVE_QUALITY_REQUIREMENTS]" in user
    assert "각 chapter 의 첫 문장은 장소 설명이 아니라 장면으로 시작" in user
    assert "symbolic_object" in user


def test_output_schema_includes_symbolic_object_field():
    from app.services.ebook.ebook_narrative_prompt_builder import (
        EBOOK_NARRATIVE_OUTPUT_INSTRUCTION,
    )
    assert "symbolic_object" in EBOOK_NARRATIVE_OUTPUT_INSTRUCTION


# ---- 158-v3 fairy-tale: prompt sections ---------------------------------


def test_system_prompt_has_fairy_tale_sections():
    sys_text = EBOOK_NARRATIVE_SYSTEM_PROMPT
    for must_show in (
        "[FAIRY_TALE_STYLE_RULE]",
        "[SUPPORTING_CHARACTER_CREATION_RULE]",
        "[STORY_CONFLICT_RULE]",
        "[REPETITION_AND_RHYTHM_RULE]",
        "[EMOTION_RULE]",
        "[DIALOGUE_RULE]",
        "[CHAPTER_STRUCTURE_RULE]",
    ):
        assert must_show in sys_text, f"missing section: {must_show}"
    # 구연동화 어휘 인용
    for s in ("옛날 옛적에", "어느 날", "그러자"):
        assert s in sys_text


def test_system_prompt_does_not_force_fixed_character_list():
    """'반드시 다음 중 하나' 같은 강제 고정 목록 문구는 시스템 프롬프트에 없어야 한다."""
    sys_text = EBOOK_NARRATIVE_SYSTEM_PROMPT
    # 강제 선택 어구 회귀 가드
    assert "반드시 다음 중 하나를 선택" not in sys_text
    assert "반드시 다음 중에서 하나" not in sys_text
    # 자유 생성 명시
    assert "자유롭게 만든다" in sys_text


def test_user_prompt_has_fairy_tale_requirements_sections():
    msgs = EbookNarrativePromptBuilder().build_messages(_wire_request())
    user = msgs[1]["content"]
    assert "[FAIRY_TALE_REQUIREMENTS]" in user
    assert "[DO_NOT_USE_FIXED_CHARACTER_LIST]" in user
    # 핵심 fairy-tale 요구사항 어구
    for s in ("구연동화", "대화", "감정", "질문", "교훈"):
        assert s in user


def test_output_schema_includes_supporting_character_and_story_question():
    from app.services.ebook.ebook_narrative_prompt_builder import (
        EBOOK_NARRATIVE_OUTPUT_INSTRUCTION,
    )
    assert "supporting_character" in EBOOK_NARRATIVE_OUTPUT_INSTRUCTION
    assert "story_question" in EBOOK_NARRATIVE_OUTPUT_INSTRUCTION


# ---- 158-v4 continuous-story: prompt sections ---------------------------


def test_system_prompt_has_continuous_story_sections():
    sys_text = EBOOK_NARRATIVE_SYSTEM_PROMPT
    for must_show in (
        "[CONTINUOUS_STORY_RULE]",
        "[CHAPTER_CAUSAL_LINK_RULE]",
        "[PLOT_PROGRESS_RULE]",
        "[ANTI_OMNIBUS_RULE]",
    ):
        assert must_show in sys_text, f"missing section: {must_show}"


def test_system_prompt_lists_anti_omnibus_phrases_to_avoid():
    sys_text = EBOOK_NARRATIVE_SYSTEM_PROMPT
    # 옴니버스 회피 — 인용된 금지 패턴 일부 검증
    for phrase in (
        "이곳은 단순한",
        "기억 조각은",
        "마음에 새겼습니다",
    ):
        assert phrase in sys_text, f"anti-omnibus prompt missing example: {phrase}"


def test_user_prompt_has_continuity_requirements_and_place_sequence():
    msgs = EbookNarrativePromptBuilder().build_messages(_wire_request())
    user = msgs[1]["content"]
    assert "[CONTINUITY_REQUIREMENTS]" in user
    assert "[PLACE_SEQUENCE_USAGE]" in user
    # 핵심 어구
    for s in ("연속된 이야기", "story_arc", "continuity_notes"):
        assert s in user


def test_output_schema_includes_story_arc_and_continuity_notes():
    from app.services.ebook.ebook_narrative_prompt_builder import (
        EBOOK_NARRATIVE_OUTPUT_INSTRUCTION,
    )
    assert "story_arc" in EBOOK_NARRATIVE_OUTPUT_INSTRUCTION
    assert "continuity_notes" in EBOOK_NARRATIVE_OUTPUT_INSTRUCTION


def test_repair_messages_mention_continuity_rules():
    builder = EbookNarrativePromptBuilder()
    repaired = builder.build_repair_messages(
        _wire_request(),
        violations=["EBOOK_LLM_CONTINUITY_MISSING: continuity_notes empty"],
    )
    user = repaired[1]["content"]
    assert "독립 에피소드" in user
    assert "이전 chapter" in user
    assert "continuity_notes" in user


def test_forbidden_phrase_constants_cover_known_regressions():
    """과거 회귀 케이스가 모두 금지 목록에 있어야 한다."""
    must_block = (
        "미션", "오늘의 미션", "완료했습니다", "기록했습니다", "선택했습니다",
        "함께 고른 키워드", "사진 1장", "보세요는 것입니다",
        "을(를)", "와(과)", "에서에서", "입니다.입니다",
    )
    for phrase in must_block:
        assert phrase in NARRATIVE_FORBIDDEN_PHRASES, f"missing: {phrase}"
