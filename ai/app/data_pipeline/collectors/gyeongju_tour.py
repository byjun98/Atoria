"""
Collector for 경주문화관광 설화 페이지.

Parses story paragraphs from the Gyeongju tourism portal's legend listing.
"""
from __future__ import annotations

import re
from typing import Iterable

from bs4 import BeautifulSoup

from app.data_pipeline.collectors.base import BaseCollector, slugify
from app.data_pipeline.registry import KNOWN_PEOPLE
from app.data_pipeline.sites import get_site
from app.schemas.data.legend import LegendRecord
from app.schemas.data.heritage_context import HeritageContextRecord

# Patterns to skip boilerplate paragraphs
_SKIP_RE = re.compile(
    r"Beautiful\s+Gyeongju|환영\s+합니다",
    re.IGNORECASE,
)


class GyeongjuTourCollector(BaseCollector):
    """Crawls the Gyeongju tour portal for local legends."""

    site_key = "gyeongju_tour"

    def __init__(self) -> None:
        super().__init__()
        self.site = get_site(self.site_key)

    # -- helpers -----------------------------------------------------------

    @staticmethod
    def _guess_title(body: str) -> str:
        """Heuristically derive a title from the story body."""
        hits = [(body.find(p), p) for p in KNOWN_PEOPLE if p in body]
        hits = [(i, p) for i, p in hits if i >= 0]
        if hits:
            _, person = min(hits)
            return f"{person} 설화"
        first = body.split("\n", 1)[0]
        head = re.split(r"[은는이가.!?…\"\n]", first, maxsplit=1)[0]
        return (head[:30] + ("…" if len(head) > 30 else "")).strip() or "경주 설화"

    def _parse_stories(self, html: str) -> list[tuple[str, str]]:
        soup = BeautifulSoup(html, "lxml")
        sel = self.site.selectors
        out: list[tuple[str, str]] = []
        for p in soup.select(sel.story_paragraphs):
            text = p.get_text("\n", strip=True)
            if not text or len(text) < self.site.min_paragraph_len:
                continue
            if _SKIP_RE.search(text):
                continue
            title = self._guess_title(text)
            out.append((title, text))
        return out

    # -- collect -----------------------------------------------------------

    def collect(
        self, seed: str,
    ) -> Iterable[LegendRecord | HeritageContextRecord]:
        client = self.build_client()
        url = self.site.list_url

        html = self.fetch(client, url)
        if not html:
            self.append_failure({"site": self.site_key, "stage": "list", "url": url})
            return

        self.save_raw_html("_list", html)

        try:
            stories = self._parse_stories(html)
        except Exception as e:
            self.logger.error("parse fail: %s", e)
            self.append_failure(
                {"site": self.site_key, "stage": "parse", "error": str(e)}
            )
            return

        self.logger.info("stories found=%d", len(stories))
        for seq, (title, body) in enumerate(stories, start=1):
            try:
                record = LegendRecord(
                    legend_id=f"legend-gyeongju-{self.site_key}-{slugify(title)}-{seq:03d}",
                    title=title,
                    source_site=self.site.name,
                    source_url=url,
                    era="신라",
                    category="경주 설화",
                    summary=body[:220] + ("…" if len(body) > 220 else ""),
                    original_text=body,
                )
                record.metadata.needs_review = len(body) < 300
                yield record
            except Exception as e:
                self.logger.error("record build fail title=%s err=%s", title, e)
                self.append_failure(
                    {"site": self.site_key, "stage": "record_build",
                     "title": title, "url": url, "error": str(e)}
                )

        client.close()
