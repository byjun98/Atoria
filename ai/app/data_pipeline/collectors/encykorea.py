"""
Collector for 한국민족문화대백과 (Encyclopedia of Korean Culture).

Flow: keyword search → first result link → detail page parse.
"""
from __future__ import annotations

from typing import Iterable
from urllib.parse import quote, urljoin

from bs4 import BeautifulSoup

from app.data_pipeline.collectors.base import BaseCollector, slugify
from app.data_pipeline.sites import get_site
from app.schemas.data.legend import LegendRecord
from app.schemas.data.heritage_context import HeritageContextRecord


class EncykoreaCollector(BaseCollector):
    """Crawls encykorea.aks.ac.kr via search → detail."""

    site_key = "encykorea"

    def __init__(self) -> None:
        super().__init__()
        self.site = get_site(self.site_key)

    # -- parsing helpers ---------------------------------------------------

    def _find_first_link(self, html: str) -> str | None:
        soup = BeautifulSoup(html, "lxml")
        sel = self.site.selectors.search_result_link
        a = soup.select_one(sel)
        if not a or not a.get("href"):
            return None
        return urljoin(self.site.base_url, a["href"])

    def _parse_detail(self, html: str) -> tuple[str, str, str | None]:
        soup = BeautifulSoup(html, "lxml")
        sel = self.site.selectors
        t = soup.select_one(sel.detail_title)
        b = soup.select_one(sel.detail_body)
        c = soup.select_one(sel.detail_category)
        return (
            t.get_text(strip=True) if t else "",
            b.get_text("\n", strip=True) if b else "",
            c.get_text(strip=True) if c else None,
        )

    # -- collect -----------------------------------------------------------

    def collect(
        self, seed: str,
    ) -> Iterable[LegendRecord | HeritageContextRecord]:
        client = self.build_client()
        search_url = self.site.search_url.format(q=quote(seed))

        self.polite_sleep()
        shtml = self.fetch(client, search_url)
        if not shtml:
            self.append_failure(
                {"site": self.site_key, "stage": "search", "seed": seed, "url": search_url}
            )
            return

        self.save_raw_html(f"search-{slugify(seed)}", shtml)

        try:
            detail_url = self._find_first_link(shtml)
        except Exception as e:
            self.append_failure(
                {"site": self.site_key, "stage": "search_parse", "seed": seed, "error": str(e)}
            )
            return

        if not detail_url:
            self.append_failure(
                {"site": self.site_key, "stage": "no_result", "seed": seed}
            )
            return

        self.polite_sleep()
        dhtml = self.fetch(client, detail_url)
        if not dhtml:
            self.append_failure(
                {"site": self.site_key, "stage": "detail", "seed": seed, "url": detail_url}
            )
            return

        self.save_raw_html(slugify(seed), dhtml)

        try:
            title, body, category = self._parse_detail(dhtml)
            record = LegendRecord(
                legend_id=f"legend-gyeongju-{self.site_key}-{slugify(title or seed)}-001",
                title=title or seed,
                source_site=self.site.name,
                source_url=detail_url,
                category=category,
                summary=body[:220] + ("…" if len(body) > 220 else ""),
                original_text=body,
            )
            record.metadata.needs_review = (not body or len(body) < 120)
            yield record
        except Exception as e:
            self.append_failure(
                {"site": self.site_key, "stage": "detail_parse", "seed": seed,
                 "url": detail_url, "error": str(e)}
            )

        client.close()
