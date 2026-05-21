"""
Adapt the existing Spring Boot ↔ AI /story/intro wire schema (API.md) to/from
the 151 internal schemas.

Wire (kept unchanged for Spring Boot compatibility):
    request : StoryIntroRequest  (people_cnt, people_information, places[])
    response: StoryIntroResponse (intro, missions[], outro)

Internal:
    request : StoryIntroGenerationRequest  (151)
    output  : StoryIntroDraft               (151)
"""
from __future__ import annotations

import re

from app.schemas.story import (
    MissionItem,
    MissionType,
    PersonInfo,
    StoryIntroRequest,
    StoryIntroResponse,
    StoryPlace,
)
from app.schemas.story_schema import (
    CoursePlaceInput,
    StoryGenerationOptions,
    StoryIntroDraft,
    StoryIntroGenerationRequest,
    StoryUserProfile,
)


# 151 mission_type → API.md MissionType (PHOTO/CHOICE/QUIZ/ACTION).
_MISSION_TYPE_MAP: dict[str, MissionType] = {
    "photo": "PHOTO",
    "quiz": "QUIZ",
    "observation": "ACTION",
    "imagination": "ACTION",
    "route": "ACTION",
}

_SSAFY_PLACE_SUFFIX_RE = re.compile(r"_ssafy$", re.IGNORECASE)

_INTEREST_TAGS = {
    "역사 깊은 유적",
    "왕릉·고분 탐방",
    "불교 문화유산",
    "전설·설화 관심",
}

_TENDENCY_TAGS = {
    "모험",
    "신중",
    "호기심",
    "감성",
    "창의",
    "협동",
    "소극",
}

_TENDENCY_ALIASES = {
    "모험적": "모험",
    "활발": "모험",
    "brave": "모험",
    "adventure": "모험",
    "adventurous": "모험",
    "신중함": "신중",
    "관찰형": "신중",
    "calm": "신중",
    "careful": "신중",
    "curious": "호기심",
    "호기심형": "호기심",
    "감성형": "감성",
    "따뜻함": "감성",
    "creative": "창의",
    "창의형": "창의",
    "playful": "창의",
    "family": "협동",
    "협동형": "협동",
    "소극적": "소극",
    "조심스러운": "소극",
}


def _split_tags(value: str | list[str] | None) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        parts = value
    else:
        parts = re.split(r"[,/|;]+|\s{2,}", value)
    return [p.strip() for p in parts if p and p.strip()]


def _normalize_tendency_tags(values: list[str]) -> list[str]:
    out: list[str] = []
    for raw in values:
        tag = _TENDENCY_ALIASES.get(raw, raw)
        if tag in _TENDENCY_TAGS and tag not in out:
            out.append(tag)
    return out


def _normalize_interest_tags(values: list[str]) -> list[str]:
    out: list[str] = []
    for tag in values:
        if tag in _INTEREST_TAGS and tag not in out:
            out.append(tag)
    return out


def _persona_from_people(people: list[PersonInfo]) -> str | None:
    """Pick a persona-like hint from the first person's tendency, if any."""
    for p in people:
        if p.tendency:
            return p.tendency
    return None


def _tendency_tags_from_people(people: list[PersonInfo]) -> list[str]:
    values: list[str] = []
    for p in people:
        values.extend(_split_tags(p.tendency))
    return _normalize_tendency_tags(values)


def _interest_tags_from_people(people: list[PersonInfo]) -> list[str]:
    values: list[str] = []
    for p in people:
        values.extend(_split_tags(getattr(p, "interest", None)))
        values.extend(_split_tags(getattr(p, "interests", None)))
    return _normalize_interest_tags(values)


def _age_group_from_people(people: list[PersonInfo]) -> str | None:
    if not people:
        return None
    youngest = min(p.age for p in people)
    if youngest <= 12:
        return "child"
    if youngest <= 19:
        return "teen"
    if len(people) > 1:
        return "family"
    return "adult"


def _companion_type_from_people(people: list[PersonInfo]) -> str | None:
    if not people:
        return None
    return "family" if len(people) > 1 else "solo"


def _expected_activity_from_place(p: StoryPlace) -> str:
    """Compose a short activity hint from address/category for the prompt."""
    bits = []
    if p.category:
        bits.append(p.category)
    if p.description:
        bits.append(p.description)
    return " · ".join(bits) if bits else None  # type: ignore[return-value]


def _canonical_place_name(name: str) -> str:
    """Normalize demo-place names to the heritage name used by RAG chunks."""
    cleaned = (name or "").strip()
    canonical = _SSAFY_PLACE_SUFFIX_RE.sub("", cleaned).strip()
    return canonical or cleaned


def to_internal_request(req: StoryIntroRequest) -> StoryIntroGenerationRequest:
    """Spring Boot request → 151 StoryIntroGenerationRequest."""
    nickname = req.people_information[0].name if req.people_information else None
    youngest = min((p.age for p in req.people_information), default=None)
    tendency_tags = _tendency_tags_from_people(req.people_information)
    interest_tags = _interest_tags_from_people(req.people_information)
    profile = StoryUserProfile(
        nickname=nickname,
        persona=_persona_from_people(req.people_information),
        companion_type=_companion_type_from_people(req.people_information),
        story_theme=", ".join(interest_tags) if interest_tags else None,
        story_tone=", ".join(tendency_tags) if tendency_tags else None,
        age_group=_age_group_from_people(req.people_information),
        age=youngest,
        interest_tags=interest_tags,
        tendency_tags=tendency_tags,
        language="ko",
    )
    places = [
        CoursePlaceInput(
            place_id=str(p.place_id),
            place_name=(canonical_name := _canonical_place_name(p.name)),
            sequence=p.sequence,
            expected_activity=_expected_activity_from_place(p),
            metadata={
                "original_place_name": p.name,
                "rag_heritage_name": canonical_name,
                "address": p.address,
                "category": p.category,
                "latitude": p.latitude,
                "longitude": p.longitude,
            },
        )
        for p in req.places
    ]
    return StoryIntroGenerationRequest(
        user_profile=profile,
        places=places,
        options=StoryGenerationOptions(),
        rag_contexts=[],
        metadata={
            "people_cnt": req.people_cnt,
            "people_information": [p.model_dump() for p in req.people_information],
            "interest_tags": interest_tags,
            "tendency_tags": tendency_tags,
        },
    )


def to_wire_response(draft: StoryIntroDraft) -> StoryIntroResponse:
    """151 StoryIntroDraft → Spring Boot StoryIntroResponse (intro/missions/outro)."""
    missions: list[MissionItem] = []
    for place in sorted(draft.places, key=lambda p: p.sequence):
        m = place.mission
        if m is None:
            # Fall back: still emit a placeholder so the wire shape is stable.
            missions.append(
                MissionItem(
                    sequence=place.sequence,
                    title=f"{place.place_name} 둘러보기",
                    description=place.story_fragment[:120] or place.place_name,
                    verification_hint=None,
                    type="ACTION",
                    story=place.story_fragment,
                )
            )
            continue
        missions.append(
            MissionItem(
                sequence=place.sequence,
                title=m.mission_title,
                description=m.mission_instruction,
                verification_hint=m.verification_hint,
                type=_MISSION_TYPE_MAP.get(m.mission_type, "ACTION"),
                story=place.story_fragment,
            )
        )
    return StoryIntroResponse(intro=draft.intro, missions=missions, outro=draft.outro)
