"""
Build individual `EbookPageDraft` objects from request inputs.

Pure deterministic composition — no LLM, no DB, no network. Korean particles
are normalised through `_josa.append_josa` so we never get strings like
"민준와(과) 가족" or "첨성대에서에서". If body would exceed
`max_page_body_chars` it is truncated with an ellipsis (no semantic rewriting).
"""
from __future__ import annotations

from app.schemas.ebook_schema import (
    EbookCoverDraft,
    EbookDraftGenerationRequest,
    EbookMissionInput,
    EbookMissionResultInput,
    EbookPageDraft,
    EbookPlaceInput,
)
from app.services.ebook._josa import append_josa
from app.services.ebook.ebook_image_prompt_builder import EbookImagePromptBuilder


_TRUNCATE_NOTICE = "…(이하 생략)"


class EbookPageBuilder:
    def __init__(
        self,
        image_prompt_builder: EbookImagePromptBuilder | None = None,
        max_page_body_chars: int = 900,
    ) -> None:
        self.image_prompt_builder = image_prompt_builder or EbookImagePromptBuilder()
        self.max_chars = max_page_body_chars

    # ---- cover --------------------------------------------------------

    def build_cover(self, request: EbookDraftGenerationRequest) -> EbookCoverDraft:
        title = self._compose_title(request)
        subtitle = self._compose_subtitle(request)
        nick = (request.user_profile.nickname or "").strip()
        if nick:
            author_label = f"{nick} 가족의 경주 이야기"
        else:
            author_label = "경주 이야기"
        image_prompt = (
            self.image_prompt_builder.build_cover_image_prompt(request)
            if request.options.include_image_prompts
            else None
        )
        return EbookCoverDraft(
            title=title,
            subtitle=subtitle,
            author_label=author_label,
            theme=request.user_profile.story_theme,
            image_prompt=image_prompt,
        )

    # ---- pages --------------------------------------------------------

    def build_table_of_contents_page(
        self, places: list[EbookPlaceInput], page_number: int
    ) -> EbookPageDraft:
        ordered = sorted(places, key=lambda p: p.sequence)
        body = "\n".join(f"{p.sequence}. {p.place_name}" for p in ordered)
        return EbookPageDraft(
            page_number=page_number,
            page_type="table_of_contents",
            title="여행의 차례",
            body=body,
        )

    def build_prologue_page(self, intro: str, page_number: int) -> EbookPageDraft:
        body, truncated = self._truncate(intro)
        return EbookPageDraft(
            page_number=page_number,
            page_type="prologue",
            title="여행의 시작",
            body=body,
            metadata={"truncated": truncated} if truncated else {},
        )

    def build_place_story_page(
        self,
        *,
        place: EbookPlaceInput,
        mission: EbookMissionInput | None,
        mission_result: EbookMissionResultInput | None,
        page_number: int,
        include_image_prompts: bool = True,
        include_missing_result_note_in_body: bool = False,
        used_chunk_ids: list[str] | None = None,
        source_urls: list[str] | None = None,
    ) -> EbookPageDraft:
        title = mission.title if mission else f"{place.place_name} 이야기"
        nickname_hint = (
            (mission_result.metadata or {}).get("nickname") if mission_result else None
        )

        # MVP 정책: E-book 은 미션 보고서가 아니라 이야기책.
        # 본문에 "미션 / 미션 안내 / 오늘의 미션 / 기록했습니다 / 선택했습니다 /
        # 함께 고른 키워드 / 사진 N장이 함께 남았습니다" 같은 결과 보고식 표현
        # 금지. story_content 와 사진을 자연스럽게 녹인 한 줄만 사용.
        paragraphs: list[str] = []
        if mission and mission.story:
            paragraphs.append(mission.story.strip())

        result_status = self._status_of(mission_result)
        if mission_result is not None and mission_result.photo_urls:
            paragraphs.append(self._compose_photo_paragraph(place, nickname_hint))

        body, truncated = self._truncate("\n\n".join(p for p in paragraphs if p))

        image_urls = list(mission_result.photo_urls) if mission_result else []
        image_prompt = (
            self.image_prompt_builder.build_place_page_image_prompt(
                place, mission, mission_result
            )
            if include_image_prompts
            else None
        )

        meta: dict = {}
        if truncated:
            meta["truncated"] = True
        meta["mission_result_status"] = result_status

        return EbookPageDraft(
            page_number=page_number,
            page_type="place_story",
            title=title,
            subtitle=place.address,
            place_name=place.place_name,
            body=body,
            mission_title=mission.title if mission else None,
            mission_result_summary=self._brief_result_summary(mission_result),
            image_urls=image_urls,
            image_prompt=image_prompt,
            used_chunk_ids=list(used_chunk_ids or []),
            source_urls=list(source_urls or []),
            metadata=meta,
        )

    def build_mission_result_page(
        self,
        *,
        place: EbookPlaceInput,
        mission: EbookMissionInput | None,
        mission_result: EbookMissionResultInput,
        page_number: int,
    ) -> EbookPageDraft:
        place_with_eseo = append_josa(place.place_name, "에서")  # 첨성대에서
        bits: list[str] = []
        if mission_result.completed:
            bits.append(f"{place_with_eseo}의 미션을 완료했습니다.")
        else:
            bits.append(f"{place_with_eseo}의 미션은 아직 기록되지 않았습니다.")
        if mission_result.user_answer and mission_result.user_answer.strip():
            bits.append(
                f"내가 남긴 한 줄 — “{mission_result.user_answer.strip()}”"
            )
        if mission_result.selected_keywords:
            kw = ", ".join(mission_result.selected_keywords)
            bits.append(f"함께 고른 키워드 — {kw}")
        if mission_result.photo_urls:
            bits.append(f"사진 {len(mission_result.photo_urls)}장이 함께 남았습니다.")
        body, truncated = self._truncate("\n\n".join(bits))
        meta = {"truncated": True} if truncated else {}
        meta["mission_result_status"] = (
            "completed" if mission_result.completed else "incomplete"
        )
        return EbookPageDraft(
            page_number=page_number,
            page_type="mission_result",
            title=f"{place.place_name}에서 남긴 기록",
            place_name=place.place_name,
            body=body,
            mission_title=mission.title if mission else mission_result.mission_title,
            mission_result_summary=self._brief_result_summary(mission_result),
            image_urls=list(mission_result.photo_urls),
            metadata=meta,
        )

    def build_epilogue_page(
        self,
        *,
        outro: str,
        place_names: list[str],
        page_number: int,
    ) -> EbookPageDraft:
        bits = [outro.strip()]
        if place_names:
            bits.append("오늘 우리가 함께 걸은 곳: " + " · ".join(place_names))
        body, truncated = self._truncate("\n\n".join(bits))
        return EbookPageDraft(
            page_number=page_number,
            page_type="epilogue",
            title="여행을 마치며",
            body=body,
            metadata={"truncated": truncated} if truncated else {},
        )

    def build_sources_page(
        self, source_urls: list[str], page_number: int
    ) -> EbookPageDraft:
        unique = self._unique_preserving_order(source_urls)
        if not unique:
            body = "참고한 자료가 별도로 기록되지 않았습니다."
        else:
            body = "\n".join(f"[{i+1}] {u}" for i, u in enumerate(unique))
        return EbookPageDraft(
            page_number=page_number,
            page_type="sources",
            title="참고 자료",
            body=body,
            source_urls=unique,
        )

    # ---- paragraph composition (Korean-aware) -------------------------

    @staticmethod
    def _compose_photo_paragraph(
        place: EbookPlaceInput, nickname: str | None
    ) -> str:
        """사진을 본문 흐름에 자연스럽게 녹이는 한 줄.

        '사진 N장이 함께 남았습니다' / '기록했습니다' / '선택했습니다' /
        '함께 고른 키워드' / '미션' 같은 결과 보고식 표현은 절대 사용하지 않는다.
        """
        actor = (nickname or "").strip()
        if actor:
            actor_eun = append_josa(actor, "은/는")
            return (
                f"{actor_eun} {place.place_name}에서 만난 한 장면을 사진으로 담았습니다. "
                f"그 사진은 이 순간의 분위기를 조용히 간직하고 있습니다."
            )
        return (
            f"{place.place_name}에서 만난 한 장면이 사진으로 남아, "
            f"이 순간의 분위기를 조용히 간직하고 있습니다."
        )

        return " ".join(sentences).strip()

    # ---- helpers ------------------------------------------------------

    @staticmethod
    def _status_of(result: EbookMissionResultInput | None) -> str:
        if result is None:
            return "missing"
        return "completed" if result.completed else "incomplete"

    def _truncate(self, text: str) -> tuple[str, bool]:
        if not text:
            return "", False
        if len(text) <= self.max_chars:
            return text, False
        keep = self.max_chars - len(_TRUNCATE_NOTICE)
        return text[: max(0, keep)] + _TRUNCATE_NOTICE, True

    @staticmethod
    def _brief_result_summary(result: EbookMissionResultInput | None) -> str | None:
        if result is None:
            return None
        if result.user_answer and result.user_answer.strip():
            return result.user_answer.strip()
        if result.selected_keywords:
            return ", ".join(result.selected_keywords)
        if result.photo_urls:
            return f"{len(result.photo_urls)}장의 사진"
        return None

    @staticmethod
    def _unique_preserving_order(items: list[str]) -> list[str]:
        seen: list[str] = []
        for x in items or []:
            if x and x not in seen:
                seen.append(x)
        return seen

    # ---- title composition --------------------------------------------

    def _compose_title(self, request: EbookDraftGenerationRequest) -> str:
        nick = (request.user_profile.nickname or "").strip()
        first_place = sorted(request.places, key=lambda p: p.sequence)[0].place_name
        style = request.options.title_style
        nick_eui = append_josa(nick, "이/가") if nick else ""
        # NOTE: "이/가" 매핑은 표준이지만 표지 제목용으로는 '의' 소유격이 더 자연스럽다.
        nick_owner = (nick + "의") if nick else ""
        if style == "simple":
            return f"{nick_owner + ' ' if nick_owner else ''}{first_place} 여행"
        if style == "adventure":
            companion = append_josa(nick, "와/과") if nick else ""
            return (
                f"{companion + ' 함께한 ' if companion else ''}경주의 모험"
            )
        # personalized (default)
        if nick_owner:
            return f"{nick_owner} 경주 이야기 — {first_place}부터"
        return f"경주 이야기 — {first_place}부터"

    @staticmethod
    def _compose_subtitle(request: EbookDraftGenerationRequest) -> str | None:
        names = [p.place_name for p in sorted(request.places, key=lambda p: p.sequence)]
        if len(names) >= 2:
            first_eseo = append_josa(names[0], "에서")  # 첨성대에서
            return f"{first_eseo} {names[-1]}까지 이어진 신라 이야기"
        if names:
            first_eseo = append_josa(names[0], "에서")
            return f"{first_eseo} 만난 신라 이야기"
        return None
