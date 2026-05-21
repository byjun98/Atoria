"""
Collector for 한국구비문학대계 (Korean Oral Literature Survey).

Flow: keyword search → filter Gyeongju-related links → detail pages.
"""
from __future__ import annotations

from typing import Iterable
from urllib.parse import quote, urljoin

from bs4 import BeautifulSoup

from app.data_pipeline.collectors.base import BaseCollector, slugify
from app.data_pipeline.sites import get_site
from app.schemas.data.legend import LegendRecord
from app.schemas.data.heritage_context import HeritageContextRecord

MAX_PER_SEED = 3


class KdpCollector(BaseCollector):
    """Crawls gubi.aks.ac.kr (한국구비문학대계)."""

    site_key = "kdp"

    def __init__(self) -> None:
        super().__init__()
        self.site = get_site(self.site_key)

    # -- parsing helpers ---------------------------------------------------

    def _find_gyeongju_links(self, html: str) -> list[str]:
        soup = BeautifulSoup(html, "lxml")
        sel = self.site.selectors.search_result_link
        out: list[str] = []
        for a in soup.select(sel):
            href = a.get("href")
            if not href:
                continue
            parent = a.find_parent()
            parent_text = parent.get_text(" ", strip=True) if parent else ""
            if "경주" in parent_text or "경상" in parent_text:
                out.append(urljoin(self.site.base_url, href))

        # Fallback: if no Gyeongju-specific links, take first few
        if not out:
            for a in soup.select(sel)[:3]:
                href = a.get("href")
                if href:
                    out.append(urljoin(self.site.base_url, href))

        # Deduplicate
        seen: set[str] = set()
        uniq: list[str] = []
        for u in out:
            if u not in seen:
                seen.add(u)
                uniq.append(u)
        return uniq

    def _parse_detail(self, html: str) -> tuple[str, str]:
        soup = BeautifulSoup(html, "lxml")
        sel = self.site.selectors
        t = soup.select_one(sel.detail_title)
        b = soup.select_one(sel.detail_body)
        return (
            t.get_text(strip=True) if t else "",
            b.get_text("\n", strip=True) if b else "",
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
            links = self._find_gyeongju_links(shtml)
        except Exception as e:
            self.append_failure(
                {"site": self.site_key, "stage": "search_parse", "seed": seed, "error": str(e)}
            )
            return

        if not links:
            self.append_failure(
                {"site": self.site_key, "stage": "no_result", "seed": seed}
            )
            return

        seq = 0
        for url in links[:MAX_PER_SEED]:
            seq += 1
            self.polite_sleep()
            dhtml = self.fetch(client, url)
            if not dhtml:
                self.append_failure(
                    {"site": self.site_key, "stage": "detail", "seed": seed, "url": url}
                )
                continue

            self.save_raw_html(f"{slugify(seed)}-{seq}", dhtml)

            try:
                title, body = self._parse_detail(dhtml)
                record = LegendRecord(
                    legend_id=f"legend-gyeongju-{self.site_key}-{slugify(title or seed)}-{seq:03d}",
                    title=title or seed,
                    source_site=self.site.name,
                    source_url=url,
                    category="구비문학",
                    summary=body[:220] + ("…" if len(body) > 220 else ""),
                    original_text=body,
                    factuality_level="folktale",
                )
                record.metadata.needs_review = (not body or len(body) < 120)
                yield record
            except Exception as e:
                self.append_failure(
                    {"site": self.site_key, "stage": "detail_parse", "seed": seed,
                     "url": url, "error": str(e)}
                )

        client.close()
