"""
Base collector providing common HTTP, I/O, and failure-logging helpers.

All site-specific collectors subclass ``BaseCollector`` and implement the
``collect()`` method.
"""
from __future__ import annotations

import hashlib
import json
import logging
import re
import time
import random
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Iterable

import httpx

from app.core.config import settings
from app.schemas.data.legend import LegendRecord
from app.schemas.data.heritage_context import HeritageContextRecord

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

PROJECT_ROOT = Path(__file__).resolve().parents[3]  # ai/
DATA_DIR = PROJECT_ROOT / "data"
RAW_DIR = DATA_DIR / "raw"
RAW_HTML_DIR = DATA_DIR / "raw_html"
ENRICHED_DIR = DATA_DIR / "enriched"
LEGENDS_DIR = ENRICHED_DIR / "legends"
HERITAGE_CTX_DIR = ENRICHED_DIR / "heritage_context"
FAILURES_PATH = DATA_DIR / "failures.jsonl"

_SLUG_RE = re.compile(r"[^0-9A-Za-z가-힣]+")


def slugify(text: str, max_len: int = 40) -> str:
    """Create a filesystem-safe slug from *text*."""
    s = _SLUG_RE.sub("-", (text or "").strip()).strip("-").lower()
    return (s[:max_len] or "untitled")


def content_hash(text: str) -> str:
    """SHA-256 hex digest of *text*."""
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


# ---------------------------------------------------------------------------
# BaseCollector ABC
# ---------------------------------------------------------------------------

class BaseCollector(ABC):
    """
    Abstract base for all data collectors.

    Subclasses must implement ``collect(seed)`` which yields validated
    ``LegendRecord`` or ``HeritageContextRecord`` instances.
    """

    site_key: str = "base"

    def __init__(self) -> None:
        self.logger = logging.getLogger(f"collector.{self.site_key}")
        if not self.logger.handlers:
            handler = logging.StreamHandler()
            handler.setFormatter(
                logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s")
            )
            self.logger.addHandler(handler)
            self.logger.setLevel(logging.INFO)

    # -- HTTP ---------------------------------------------------------------

    def build_client(self) -> httpx.Client:
        """Build an ``httpx.Client`` with retry-friendly transport."""
        transport = httpx.HTTPTransport(retries=settings.RETRY_ATTEMPTS)
        return httpx.Client(
            transport=transport,
            timeout=httpx.Timeout(15.0),
            headers={
                "User-Agent": settings.CRAWL_USER_AGENT,
                "Accept-Language": "ko-KR,ko;q=0.9,en;q=0.5",
            },
            follow_redirects=True,
        )

    def polite_sleep(self) -> None:
        """Sleep between requests to respect the target server."""
        base = settings.CRAWL_POLITE_DELAY_SEC
        time.sleep(random.uniform(base, base * 1.8))

    def fetch(self, client: httpx.Client, url: str) -> str | None:
        """GET *url* and return the response text, or ``None`` on failure."""
        try:
            resp = client.get(url)
            resp.raise_for_status()
            resp.encoding = resp.encoding or "utf-8"
            return resp.text
        except httpx.HTTPError as exc:
            self.logger.warning("fetch fail url=%s err=%s", url, exc)
            return None

    # -- I/O helpers --------------------------------------------------------

    def save_raw_html(self, slug: str, html: str) -> Path:
        """Persist raw HTML under ``data/raw_html/<site_key>/``."""
        out_dir = RAW_HTML_DIR / self.site_key
        out_dir.mkdir(parents=True, exist_ok=True)
        path = out_dir / f"{slug}.html"
        path.write_text(html, encoding="utf-8")
        return path

    def save_record_json(self, record: LegendRecord | HeritageContextRecord) -> Path:
        """Write an enriched record to its appropriate directory."""
        data = record.model_dump()
        if record.source_type == "legend":
            target_dir = LEGENDS_DIR
            filename = f"{data['legend_id']}.json"
        else:
            target_dir = HERITAGE_CTX_DIR
            filename = f"{data['record_id']}.json"
        target_dir.mkdir(parents=True, exist_ok=True)
        path = target_dir / filename
        path.write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return path

    def append_failure(self, record: dict) -> None:
        """Append a failure record to ``data/failures.jsonl``."""
        FAILURES_PATH.parent.mkdir(parents=True, exist_ok=True)
        with FAILURES_PATH.open("a", encoding="utf-8") as f:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")

    # -- abstract -----------------------------------------------------------

    @abstractmethod
    def collect(
        self, seed: str,
    ) -> Iterable[LegendRecord | HeritageContextRecord]:
        """
        Collect data for the given *seed* (heritage name or search term).

        Yields validated record instances.
        """
        ...
