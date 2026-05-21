"""
Validator — applies Pydantic validation plus custom review-flag rules.
"""
from __future__ import annotations

from app.schemas.data.legend import LegendRecord
from app.schemas.data.heritage_context import HeritageContextRecord


def needs_review_legend(record: LegendRecord) -> bool:
    """
    Determine whether a legend record should be flagged for manual review.

    Flags:
    - body text shorter than 120 characters
    - no related heritages linked
    - missing source URL
    """
    if len(record.original_text) < 120:
        return True
    if not record.related_heritages:
        return True
    if not record.source_url:
        return True
    return False


def needs_review_heritage_context(record: HeritageContextRecord) -> bool:
    """
    Determine whether a heritage-context record needs review.

    Flags:
    - summary shorter than 50 characters
    - missing source URL
    - empty definition
    """
    if len(record.summary) < 50:
        return True
    if not record.source_url:
        return True
    if not record.definition:
        return True
    return False


def validate_and_flag(
    record: LegendRecord | HeritageContextRecord,
) -> LegendRecord | HeritageContextRecord:
    """
    Re-validate a record through Pydantic and set ``metadata.needs_review``.

    Returns the (possibly mutated) record.
    """
    if isinstance(record, LegendRecord):
        # Round-trip through Pydantic to catch schema violations
        record = LegendRecord.model_validate(record.model_dump())
        record.metadata.needs_review = needs_review_legend(record)
    elif isinstance(record, HeritageContextRecord):
        record = HeritageContextRecord.model_validate(record.model_dump())
        record.metadata.needs_review = needs_review_heritage_context(record)
    return record
