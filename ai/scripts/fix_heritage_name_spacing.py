"""
Fix heritage_name spacing: '동궁과월지' → '동궁과 월지'.

Usage:
    python -m scripts.fix_heritage_name_spacing          # dry-run (default)
    python -m scripts.fix_heritage_name_spacing --apply   # actually commit
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Ensure project root is on sys.path
ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from sqlalchemy import text
from app.db.session import engine


OLD_NAME = "동궁과월지"
NEW_NAME = "동궁과 월지"

QUERIES = [
    (
        "rag_chunks",
        text(
            "UPDATE rag_chunks SET heritage_name = :new "
            "WHERE heritage_name = :old"
        ),
    ),
    (
        "rag_source_documents",
        text(
            "UPDATE rag_source_documents SET heritage_name = :new "
            "WHERE heritage_name = :old"
        ),
    ),
]


def run(*, apply: bool = False) -> None:
    with engine.connect() as conn:
        trans = conn.begin()
        for table, stmt in QUERIES:
            result = conn.execute(stmt, {"old": OLD_NAME, "new": NEW_NAME})
            count = result.rowcount
            action = "UPDATED" if apply else "WOULD UPDATE"
            print(f"[{table}] {action} {count} rows  ('{OLD_NAME}' → '{NEW_NAME}')")
        if apply:
            trans.commit()
            print("\nChanges committed.")
        else:
            trans.rollback()
            print("\n(dry-run) No changes committed. Use --apply to commit.")


def main() -> None:
    parser = argparse.ArgumentParser(description="Fix heritage_name spacing")
    parser.add_argument("--apply", action="store_true", help="Actually commit changes")
    args = parser.parse_args()
    run(apply=args.apply)


if __name__ == "__main__":
    main()
