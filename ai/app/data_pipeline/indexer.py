"""
Index builder — scans enriched JSON and produces CSV indexes.

Outputs:
- ``data/enriched/heritage_index.csv``
- ``data/enriched/legend_index.csv``
"""
from __future__ import annotations

import csv
import json
from pathlib import Path

from app.data_pipeline.collectors.base import ENRICHED_DIR, LEGENDS_DIR, HERITAGE_CTX_DIR
from app.data_pipeline.registry import SELECTED_HERITAGES, find_heritages_in_text

HERITAGE_INDEX_PATH = ENRICHED_DIR / "heritage_index.csv"
LEGEND_INDEX_PATH = ENRICHED_DIR / "legend_index.csv"

# -- column specs ----------------------------------------------------------

HERITAGE_INDEX_FIELDS = [
    "heritage_id",
    "heritage_name",
    "related_record_id",
    "source_type",
    "source_site",
    "source_url",
    "factuality_level",
    "needs_review",
]

LEGEND_INDEX_FIELDS = [
    "legend_id",
    "title",
    "source_site",
    "source_url",
    "era",
    "category",
    "related_places",
    "related_people",
    "factuality_level",
    "needs_review",
]


def _load_all_records() -> list[dict]:
    """Load every JSON file under enriched/ directories."""
    records: list[dict] = []
    for directory in (LEGENDS_DIR, HERITAGE_CTX_DIR):
        if not directory.is_dir():
            continue
        for fp in sorted(directory.glob("*.json")):
            try:
                records.append(json.loads(fp.read_text(encoding="utf-8")))
            except Exception:
                continue
    return records


def rebuild_indexes() -> dict[str, int]:
    """
    Re-generate ``heritage_index.csv`` and ``legend_index.csv`` from
    the enriched JSON files on disk.

    Returns a summary dict with counts.
    """
    records = _load_all_records()
    ENRICHED_DIR.mkdir(parents=True, exist_ok=True)

    # -- heritage_index.csv ------------------------------------------------
    heritage_rows: list[dict] = []

    # Ensure all 15 heritages appear even if no records reference them
    heritage_seen: set[str] = set()

    for rec in records:
        source_type = rec.get("source_type", "")
        record_id = rec.get("legend_id") or rec.get("record_id", "")
        source_site = rec.get("source_site", "")
        source_url = rec.get("source_url", "")
        factuality = rec.get("factuality_level", "")
        needs_review = rec.get("metadata", {}).get("needs_review", False)

        # Find matching heritages via text or explicit field
        body = rec.get("original_text", "") or rec.get("summary", "")
        title = rec.get("title", "") or rec.get("heritage_name", "")
        full_text = f"{title}\n{body}"

        matched = find_heritages_in_text(full_text)

        # Also include heritages explicitly listed
        for h_name in rec.get("related_heritages", []):
            from app.data_pipeline.registry import _ALIAS_MAP
            if h_name in _ALIAS_MAP:
                ent = _ALIAS_MAP[h_name]
                if ent not in matched:
                    matched.append(ent)

        # For heritage_context records, always link to the heritage_name
        if source_type == "heritage_context":
            h_name = rec.get("heritage_name", "")
            from app.data_pipeline.registry import _ALIAS_MAP
            if h_name in _ALIAS_MAP:
                ent = _ALIAS_MAP[h_name]
                if ent not in matched:
                    matched.append(ent)

        for entity in matched:
            heritage_seen.add(entity.heritage_id)
            heritage_rows.append({
                "heritage_id": entity.heritage_id,
                "heritage_name": entity.canonical_name,
                "related_record_id": record_id,
                "source_type": source_type,
                "source_site": source_site,
                "source_url": source_url,
                "factuality_level": factuality,
                "needs_review": needs_review,
            })

    # Add placeholder rows for heritages with no matching records
    for h in SELECTED_HERITAGES:
        if h.heritage_id not in heritage_seen:
            heritage_rows.append({
                "heritage_id": h.heritage_id,
                "heritage_name": h.canonical_name,
                "related_record_id": "",
                "source_type": "",
                "source_site": "",
                "source_url": "",
                "factuality_level": "",
                "needs_review": True,
            })

    with HERITAGE_INDEX_PATH.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=HERITAGE_INDEX_FIELDS, quoting=csv.QUOTE_ALL)
        w.writeheader()
        w.writerows(heritage_rows)

    # -- legend_index.csv --------------------------------------------------
    legend_rows: list[dict] = []
    for rec in records:
        if rec.get("source_type") != "legend":
            continue
        legend_rows.append({
            "legend_id": rec.get("legend_id", ""),
            "title": rec.get("title", ""),
            "source_site": rec.get("source_site", ""),
            "source_url": rec.get("source_url", ""),
            "era": rec.get("era", ""),
            "category": rec.get("category", ""),
            "related_places": "|".join(rec.get("related_places", [])),
            "related_people": "|".join(rec.get("related_people", [])),
            "factuality_level": rec.get("factuality_level", ""),
            "needs_review": rec.get("metadata", {}).get("needs_review", False),
        })

    with LEGEND_INDEX_PATH.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=LEGEND_INDEX_FIELDS, quoting=csv.QUOTE_ALL)
        w.writeheader()
        w.writerows(legend_rows)

    return {
        "heritage_index_rows": len(heritage_rows),
        "legend_index_rows": len(legend_rows),
        "total_records": len(records),
    }
