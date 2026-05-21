"""
Pipeline orchestrator — runs the full collect → clean → enrich → validate → index pipeline.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Iterable

from app.core.config import settings
from app.data_pipeline.collectors.base import (
    BaseCollector,
    ENRICHED_DIR,
    LEGENDS_DIR,
    HERITAGE_CTX_DIR,
    FAILURES_PATH,
    content_hash,
)
from app.data_pipeline.collectors.encykorea import EncykoreaCollector
from app.data_pipeline.collectors.kdp import KdpCollector
from app.data_pipeline.collectors.gyeongju_tour import GyeongjuTourCollector
from app.data_pipeline.collectors.manual_webfetch import ManualWebfetchCollector
from app.data_pipeline.processors.text_cleaner import clean_text
from app.data_pipeline.processors.entity_linker import link_entities
from app.data_pipeline.processors.motif_detector import detect_motifs
from app.data_pipeline.processors.tone_tagger import detect_tone_tags
from app.data_pipeline.processors.summarizer import summarise
from app.data_pipeline.processors.story_hook_generator import generate_story_hooks
from app.data_pipeline.processors.validator import validate_and_flag
from app.data_pipeline.indexer import rebuild_indexes
from app.data_pipeline.registry import SELECTED_HERITAGES
from app.schemas.data.legend import LegendRecord
from app.schemas.data.heritage_context import HeritageContextRecord

logger = logging.getLogger("data_pipeline.run")

# ---------------------------------------------------------------------------
# Collector registry
# ---------------------------------------------------------------------------

COLLECTOR_MAP: dict[str, type[BaseCollector]] = {
    "encykorea": EncykoreaCollector,
    "kdp": KdpCollector,
    "gyeongju_tour": GyeongjuTourCollector,
    "manual_webfetch": ManualWebfetchCollector,
}


def _get_collector(site: str) -> BaseCollector:
    cls = COLLECTOR_MAP.get(site)
    if cls is None:
        raise ValueError(f"Unknown site: {site}. Available: {', '.join(COLLECTOR_MAP)}")
    return cls()


# ---------------------------------------------------------------------------
# Enrichment helpers
# ---------------------------------------------------------------------------

def enrich_record(
    record: LegendRecord | HeritageContextRecord,
) -> LegendRecord | HeritageContextRecord:
    """Apply all enrichment processors to a single record."""

    # Determine the text to analyse
    if isinstance(record, LegendRecord):
        body = record.original_text or record.summary
        title = record.title
    else:
        body = record.summary or record.definition
        title = record.heritage_name

    full_text = f"{title}\n{body}"

    # Text cleaning
    if isinstance(record, LegendRecord):
        record.original_text = clean_text(record.original_text)
        body = record.original_text  # refresh
        full_text = f"{title}\n{body}"

    # Entity linking
    entities = link_entities(full_text)
    if not record.related_heritages:
        record.related_heritages = entities["related_heritages"]
    if not record.related_people:
        record.related_people = entities["related_people"]
    if not record.related_places:
        record.related_places = entities["related_places"]

    # Motifs & tone
    if not record.motifs:
        record.motifs = detect_motifs(full_text)
    if not record.tone_tags:
        record.tone_tags = detect_tone_tags(full_text)

    # Summary (legend only, if empty)
    if isinstance(record, LegendRecord) and not record.summary:
        record.summary = summarise(body, use_llm=settings.ENABLE_LLM_SUMMARY)

    # Story hooks
    if not record.story_hooks:
        record.story_hooks = generate_story_hooks(
            title,
            record.motifs,
            record.related_people,
            record.related_heritages,
        )

    return record


# ---------------------------------------------------------------------------
# Idempotency — hash-based skip
# ---------------------------------------------------------------------------

def _existing_hashes() -> dict[str, str]:
    """Build a map of record_id → content_hash from existing JSON files."""
    hashes: dict[str, str] = {}
    for directory in (LEGENDS_DIR, HERITAGE_CTX_DIR):
        if not directory.is_dir():
            continue
        for fp in directory.glob("*.json"):
            try:
                data = json.loads(fp.read_text(encoding="utf-8"))
                rid = data.get("legend_id") or data.get("record_id", "")
                body = data.get("original_text") or data.get("summary") or ""
                hashes[rid] = content_hash(body)
            except Exception:
                continue
    return hashes


# ---------------------------------------------------------------------------
# Pipeline steps
# ---------------------------------------------------------------------------

def step_collect(
    site: str,
    heritage: str | None = None,
) -> list[LegendRecord | HeritageContextRecord]:
    """Collect records from a site for the given heritage (or all 15)."""
    collector = _get_collector(site)
    records: list[LegendRecord | HeritageContextRecord] = []

    seeds: list[str]
    if heritage:
        seeds = [heritage]
    else:
        seeds = [h.canonical_name for h in SELECTED_HERITAGES]

    for seed in seeds:
        logger.info("collecting site=%s seed=%s", site, seed)
        try:
            for rec in collector.collect(seed):
                records.append(rec)
                collector.save_record_json(rec)
                logger.info("collected %s",
                            getattr(rec, "legend_id", None) or getattr(rec, "record_id", None))
        except Exception as e:
            logger.error("collect error site=%s seed=%s: %s", site, seed, e)
            collector.append_failure(
                {"site": site, "stage": "collect", "seed": seed, "error": str(e)}
            )

    return records


def step_enrich_all() -> int:
    """Enrich all existing JSON files in-place. Returns count of processed files."""
    count = 0
    for directory in (LEGENDS_DIR, HERITAGE_CTX_DIR):
        if not directory.is_dir():
            continue
        for fp in sorted(directory.glob("*.json")):
            try:
                data = json.loads(fp.read_text(encoding="utf-8"))
                source_type = data.get("source_type", "")
                if source_type == "legend":
                    rec = LegendRecord.model_validate(data)
                elif source_type == "heritage_context":
                    rec = HeritageContextRecord.model_validate(data)
                else:
                    continue

                rec = enrich_record(rec)
                fp.write_text(
                    json.dumps(rec.model_dump(), ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                count += 1
            except Exception as e:
                logger.error("enrich error file=%s: %s", fp.name, e)
    return count


def step_validate_all() -> dict[str, int]:
    """Validate all records and set needs_review flags. Returns stats."""
    stats = {"ok": 0, "review": 0, "error": 0}
    for directory in (LEGENDS_DIR, HERITAGE_CTX_DIR):
        if not directory.is_dir():
            continue
        for fp in sorted(directory.glob("*.json")):
            try:
                data = json.loads(fp.read_text(encoding="utf-8"))
                source_type = data.get("source_type", "")
                if source_type == "legend":
                    rec = LegendRecord.model_validate(data)
                elif source_type == "heritage_context":
                    rec = HeritageContextRecord.model_validate(data)
                else:
                    continue

                rec = validate_and_flag(rec)
                fp.write_text(
                    json.dumps(rec.model_dump(), ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                if rec.metadata.needs_review:
                    stats["review"] += 1
                else:
                    stats["ok"] += 1
            except Exception as e:
                logger.error("validate error file=%s: %s", fp.name, e)
                stats["error"] += 1
    return stats


def step_index() -> dict[str, int]:
    """Rebuild index CSVs."""
    return rebuild_indexes()


def run_all(
    sites: list[str] | None = None,
    heritage: str | None = None,
    retry_failures: bool = False,
) -> None:
    """
    Execute the complete pipeline: collect → enrich → validate → index.
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    target_sites = sites or list(COLLECTOR_MAP.keys())
    # Remove manual_webfetch from "all" runs unless explicit
    if not sites and "manual_webfetch" in target_sites:
        target_sites.remove("manual_webfetch")

    # Retry failures first if requested
    if retry_failures and FAILURES_PATH.exists():
        logger.info("=== retrying failures ===")
        _retry_failures()

    # Collect
    for site in target_sites:
        logger.info("=== collect %s ===", site)
        step_collect(site, heritage)

    # Enrich
    logger.info("=== enrich ===")
    enriched = step_enrich_all()
    logger.info("enriched %d records", enriched)

    # Validate
    logger.info("=== validate ===")
    vstats = step_validate_all()
    logger.info("validate results: %s", vstats)

    # Index
    logger.info("=== index ===")
    istats = step_index()
    logger.info("index results: %s", istats)

    logger.info("=== pipeline complete ===")


def _retry_failures() -> None:
    """Re-attempt failed items from failures.jsonl."""
    if not FAILURES_PATH.exists():
        return
    lines = FAILURES_PATH.read_text(encoding="utf-8").strip().splitlines()
    # Clear the file
    FAILURES_PATH.write_text("", encoding="utf-8")
    for line in lines:
        try:
            item = json.loads(line)
            site = item.get("site", "")
            seed = item.get("seed", "")
            if site and seed and site in COLLECTOR_MAP:
                logger.info("retrying site=%s seed=%s", site, seed)
                step_collect(site, seed)
        except Exception as e:
            logger.error("retry error: %s", e)
