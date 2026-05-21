"""
Site configurations for each data source.

Each site entry contains URL templates, CSS selectors, and scraping parameters.
To add a new site, add a ``SiteConfig`` entry to ``SITES`` and write a matching
collector in ``app.data_pipeline.collectors``.
"""
from __future__ import annotations

from pydantic import BaseModel, Field


class SiteSelectors(BaseModel):
    """CSS selectors used to extract content from a site."""

    search_result_link: str = ""
    detail_title: str = ""
    detail_body: str = ""
    detail_category: str = ""
    content_root: str = ""
    story_paragraphs: str = ""
    page_heading: str = ""
    region_tag: str = ""


class SiteConfig(BaseModel):
    """Configuration for a single crawl-target site."""

    key: str
    name: str
    base_url: str
    search_url: str = ""
    list_url: str = ""
    selectors: SiteSelectors = Field(default_factory=SiteSelectors)
    min_paragraph_len: int = 200


# ---------------------------------------------------------------------------
# Site registry — add new sites here
# ---------------------------------------------------------------------------

SITES: dict[str, SiteConfig] = {
    "encykorea": SiteConfig(
        key="encykorea",
        name="한국민족문화대백과",
        base_url="https://encykorea.aks.ac.kr",
        search_url="https://encykorea.aks.ac.kr/Search?keyword={q}",
        selectors=SiteSelectors(
            search_result_link="a.result-title, .search_result a, .result_list a",
            detail_title="h1, .title, .article-title",
            detail_body="#articleBody, .article-body, .content, .view_article",
            detail_category=".category, .meta .cat, .breadcrumb",
        ),
    ),
    "kdp": SiteConfig(
        key="kdp",
        name="한국구비문학대계",
        base_url="https://gubi.aks.ac.kr",
        search_url="https://gubi.aks.ac.kr/web/TotalSearch.aspx?keyword={q}",
        selectors=SiteSelectors(
            search_result_link=".result a, a.title, .list_title a",
            detail_title="h1, .story-title, .tit",
            detail_body=".story-content, .view_body, .cont",
            region_tag=".region, .meta .region",
        ),
    ),
    "gyeongju_tour": SiteConfig(
        key="gyeongju_tour",
        name="경주문화관광",
        base_url="https://www.gyeongju.go.kr",
        list_url="https://www.gyeongju.go.kr/tour/page.do?mnu_uid=2359&",
        selectors=SiteSelectors(
            content_root="#content",
            story_paragraphs="#content p",
            page_heading="#content h3",
        ),
        min_paragraph_len=200,
    ),
}


def get_site(key: str) -> SiteConfig:
    """Look up a site configuration by key, raising ``KeyError`` if unknown."""
    if key not in SITES:
        raise KeyError(
            f"Unknown site '{key}'. Available: {', '.join(SITES)}"
        )
    return SITES[key]
