"""
Manual / WebFetch collector — absorbs pre-existing JSON files into the
standard schema.

Use this when data has already been collected (e.g. the cheomseongdae
heritage context JSON) and needs to be normalised into the pipeline format.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Iterable

from app.data_pipeline.collectors.base import BaseCollector
from app.schemas.data.legend import LegendRecord
from app.schemas.data.heritage_context import HeritageContextRecord


class ManualWebfetchCollector(BaseCollector):
    """Reads existing JSON files and yields validated records."""

    site_key = "manual_webfetch"

    def collect(
        self, seed: str,
    ) -> Iterable[LegendRecord | HeritageContextRecord]:
        """
        *seed* is either:
        - a path to a single JSON file, or
        - a path to a directory of JSON files.
        """
        path = Path(seed)
        if path.is_file():
            yield from self._load_file(path)
        elif path.is_dir():
            for fp in sorted(path.glob("*.json")):
                yield from self._load_file(fp)
        else:
            self.append_failure(
                {"site": self.site_key, "stage": "input", "seed": seed,
                 "error": f"Path not found: {seed}"}
            )

    def _load_file(self, path: Path) -> Iterable[LegendRecord | HeritageContextRecord]:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception as e:
            self.append_failure(
                {"site": self.site_key, "stage": "json_parse",
                 "file": str(path), "error": str(e)}
            )
            return

        source_type = data.get("source_type", "")
        try:
            if source_type == "heritage_context":
                yield HeritageContextRecord.model_validate(data)
            elif source_type == "legend":
                yield LegendRecord.model_validate(data)
            else:
                # Try heritage_context first (has record_id), then legend
                if "record_id" in data:
                    yield HeritageContextRecord.model_validate(data)
                elif "legend_id" in data:
                    yield LegendRecord.model_validate(data)
                else:
                    self.append_failure(
                        {"site": self.site_key, "stage": "unknown_type",
                         "file": str(path), "source_type": source_type}
                    )
        except Exception as e:
            self.append_failure(
                {"site": self.site_key, "stage": "validation",
                 "file": str(path), "error": str(e)}
            )
