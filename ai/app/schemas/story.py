"""Request/response schemas for the Spring Boot ↔ AI integration APIs.

Mirrors `ai/API.md`:
  - POST /story/intro
  - POST /artifacts/ebook/jobs
"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


# --- /story/intro ---------------------------------------------------------


class PersonInfo(BaseModel):
    name: str
    age: int
    tendency: str
    interest: str | None = None
    interests: list[str] = Field(default_factory=list)


class StoryPlace(BaseModel):
    place_id: int
    sequence: int
    name: str
    description: str
    address: str
    category: str
    latitude: float
    longitude: float


class StoryIntroRequest(BaseModel):
    people_cnt: int
    people_information: list[PersonInfo] = Field(..., min_length=1)
    places: list[StoryPlace] = Field(..., min_length=1)


MissionType = Literal["PHOTO", "CHOICE", "QUIZ", "ACTION"]


class MissionItem(BaseModel):
    sequence: int
    title: str
    description: str
    verification_hint: str | None = None
    type: MissionType
    story: str


class StoryIntroResponse(BaseModel):
    intro: str
    missions: list[MissionItem]
    outro: str


# --- /artifacts/ebook/jobs -----------------------------------------------


class ProtagonistInfo(BaseModel):
    people_cnt: int
    people_information: list[PersonInfo]


class StoryBlock(BaseModel):
    title: str
    intro: str
    outro: str
    protagonist_info: ProtagonistInfo


class ChapterUserResult(BaseModel):
    image_url: str | None = None
    choice: str | None = None


class EbookChapter(BaseModel):
    sequence: int
    place_id: int
    place_name: str
    place_address: str
    mission_title: str
    mission_description: str
    mission_type: MissionType
    story_content: str
    user_result: ChapterUserResult


class EbookJobRequest(BaseModel):
    story_id: int
    user_id: int
    story: StoryBlock
    chapters: list[EbookChapter]


PageType = Literal["COVER", "INTRO", "CHAPTER", "OUTRO", "BACK_COVER"]
PageLayout = Literal[
    "COVER",
    "TEXT_ONLY",
    "IMAGE_TOP_TEXT_BOTTOM",
    "TEXT_WITH_QUOTE",
    "BACK_COVER",
]


class EbookMeta(BaseModel):
    title: str
    subtitle: str
    author: str
    page_count: int
    language: str = "ko"


class EbookCover(BaseModel):
    title: str
    background_color: str
    thumbnail_hint: str


class EbookPage(BaseModel):
    page_number: int
    type: PageType
    layout: PageLayout
    sequence: int | None = None
    title: str | None = None
    subtitle: str | None = None
    text: str | None = None
    image_url: str | None = None
    caption: str | None = None
    quote: str | None = None


class EbookContent(BaseModel):
    meta: EbookMeta
    cover: EbookCover
    pages: list[EbookPage]


class EbookJobData(BaseModel):
    story_id: int
    ebook_content: EbookContent


class EbookJobResponse(BaseModel):
    success: bool
    data: EbookJobData | None = None
    error: dict | None = None
    timestamp: str = Field(...)
