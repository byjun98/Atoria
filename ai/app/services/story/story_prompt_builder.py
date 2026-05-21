"""Compose system + user messages for story generation (151).

This builder does NOT call OpenAI. 152 will pass the returned messages list
straight to its LLM client.
"""
from __future__ import annotations

import json

from app.schemas.story_schema import (
    CoursePlaceInput,
    StoryGenerationOptions,
    StoryIntroGenerationRequest,
    StoryUpdateGenerationRequest,
    StoryUserProfile,
)
from app.services.story.rag_context_formatter import RagContextFormatter
from app.services.story.story_prompt_templates import (
    STORY_INTRO_USER_PROMPT_TEMPLATE,
    STORY_OUTPUT_JSON_INSTRUCTION,
    STORY_PROMPT_VERSION,
    STORY_SYSTEM_PROMPT,
    STORY_UPDATE_USER_PROMPT_TEMPLATE,
)


class StoryPromptBuilder:
    def __init__(
        self,
        rag_context_formatter: RagContextFormatter | None = None,
        prompt_version: str = STORY_PROMPT_VERSION,
    ) -> None:
        self.formatter = rag_context_formatter or RagContextFormatter()
        self.prompt_version = prompt_version

    # ---- public --------------------------------------------------------

    def build_intro_messages(self, request: StoryIntroGenerationRequest) -> list[dict]:
        return [
            {"role": "system", "content": STORY_SYSTEM_PROMPT},
            {"role": "user", "content": self.build_intro_prompt_text(request)},
        ]

    def build_update_messages(self, request: StoryUpdateGenerationRequest) -> list[dict]:
        return [
            {"role": "system", "content": STORY_SYSTEM_PROMPT},
            {"role": "user", "content": self._build_update_prompt_text(request)},
        ]

    def build_intro_prompt_text(self, request: StoryIntroGenerationRequest) -> str:
        opts = request.options
        return STORY_INTRO_USER_PROMPT_TEMPLATE.format(
            user_profile_block=self._format_user_profile(request.user_profile),
            places_block=self._format_places(request.places),
            include_missions=str(opts.include_missions).lower(),
            mission_count_per_place=opts.mission_count_per_place,
            max_intro_chars=opts.max_intro_chars,
            max_place_story_chars=opts.max_place_story_chars,
            max_outro_chars=opts.max_outro_chars,
            output_format=opts.output_format,
            rag_context_block=self.formatter.format_contexts_by_place(request.rag_contexts),
            weather_context_block=self._format_weather_context(getattr(request, "weather_context", None)),
            output_instruction=STORY_OUTPUT_JSON_INSTRUCTION,
        )

    # ---- helpers -------------------------------------------------------

    def _build_update_prompt_text(self, request: StoryUpdateGenerationRequest) -> str:
        return STORY_UPDATE_USER_PROMPT_TEMPLATE.format(
            story_session_id=request.story_session_id,
            current_place_name=request.current_place_name,
            user_profile_block=self._format_user_profile(request.user_profile),
            previous_story_state_block=_dump_json(request.previous_story_state),
            completed_mission_result_block=_dump_json(request.completed_mission_result),
            rag_context_block=self.formatter.format_contexts_by_place(request.rag_contexts),
            weather_context_block=self._format_weather_context(getattr(request, "weather_context", None)),
            output_instruction=STORY_OUTPUT_JSON_INSTRUCTION,
        )

    @staticmethod
    def _format_weather_context(weather) -> str:
        if not weather:
            return "(날씨 정보 없음)"
        parts = []
        if weather.temperature is not None:
            parts.append(f"- 현재 기온 (TA): {weather.temperature}도")
        if weather.rainfall is not None:
            parts.append(f"- 강수량 (RN): {weather.rainfall}mm")
        if weather.humidity is not None:
            parts.append(f"- 습도 (HM): {weather.humidity}%")
        if weather.wind_speed is not None:
            parts.append(f"- 풍속 (WS): {weather.wind_speed}m/s")
        if weather.wind_direction is not None:
            parts.append(f"- 풍향 (WD): {weather.wind_direction}deg")
        if not parts:
            return "(날씨 정보 없음)"
        return "\n".join(parts)

    @staticmethod
    def _format_user_profile(profile: StoryUserProfile) -> str:
        rows = [
            ("nickname", profile.nickname),
            ("persona", profile.persona),
            ("companion_type", profile.companion_type),
            ("story_theme", profile.story_theme),
            ("story_tone", profile.story_tone),
            ("age_group", profile.age_group),
            ("age", profile.age),
            ("interest_tags", profile.interest_tags),
            ("tendency_tags", profile.tendency_tags),
            ("language", profile.language),
        ]
        return "\n".join(f"- {k}: {_format_profile_value(v)}" for k, v in rows)

    @staticmethod
    def _format_places(places: list[CoursePlaceInput]) -> str:
        ordered = sorted(places, key=lambda p: p.sequence)
        lines = []
        for p in ordered:
            lines.append(
                f"- sequence={p.sequence} | place_name={p.place_name} | "
                f"place_id={p.place_id or '(미지정)'} | "
                f"expected_activity={p.expected_activity or '(미지정)'}"
            )
        return "\n".join(lines)


def _dump_json(value) -> str:
    if not value:
        return "(없음)"
    return json.dumps(value, ensure_ascii=False, indent=2)


def _format_profile_value(value) -> str:
    if value is None:
        return "(미지정)"
    if isinstance(value, list):
        return ", ".join(str(v) for v in value if v) or "(미지정)"
    return str(value)
