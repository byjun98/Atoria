"""
Preview script — chunk all heritage_context + heritage_legend_materials
JSON files and print summary stats. No DB / API / embedding calls.

Run:
    python -m scripts.preview_chunking
or:
    python ai/scripts/preview_chunking.py
"""
from __future__ import annotations

import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

# Make `app` importable when run as a plain script.
ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.schemas.chunk_schema import RagChunk  # noqa: E402
from app.services.chunking import (  # noqa: E402
    SemanticChunker,
    SourceDocumentNormalizer,
)


HERITAGE_CONTEXT_DIR = ROOT / "data" / "enriched" / "heritage_context"
LEGEND_FILE = ROOT / "data" / "seed" / "heritage_legend_materials.json"


def _load_heritage_context_items() -> list[dict]:
    items: list[dict] = []
    if not HERITAGE_CONTEXT_DIR.exists():
        return items
    for path in sorted(HERITAGE_CONTEXT_DIR.glob("*.json")):
        with path.open(encoding="utf-8") as f:
            items.append(json.load(f))
    return items


def _load_legend_items() -> list[dict]:
    if not LEGEND_FILE.exists():
        return []
    with LEGEND_FILE.open(encoding="utf-8") as f:
        return json.load(f)


def main() -> None:
    raw_items = _load_heritage_context_items() + _load_legend_items()

    normalizer = SourceDocumentNormalizer()
    chunker = SemanticChunker()

    documents = normalizer.normalize_many(raw_items)
    chunks: list[RagChunk] = chunker.chunk_documents(documents)

    print(f"Loaded source documents: {len(documents)}")
    print(f"Generated chunks: {len(chunks)}")

    role_counts = Counter(c.content_role for c in chunks)
    print("\nBy content_role:")
    for role, n in sorted(role_counts.items()):
        print(f"- {role}: {n}")

    fact_counts = Counter((c.factuality_level or "unknown") for c in chunks)
    print("\nBy factuality_level:")
    for lvl, n in sorted(fact_counts.items()):
        print(f"- {lvl}: {n}")

    type_counts = Counter(c.source_type for c in chunks)
    print("\nBy source_type:")
    for t, n in sorted(type_counts.items()):
        print(f"- {t}: {n}")

    # Per-record preview: group chunks by source_record_id (one document = one block).
    by_record: dict[str, list[RagChunk]] = defaultdict(list)
    for c in chunks:
        by_record[c.source_record_id].append(c)

    print("\nPer-document previews:")
    for record_id, doc_chunks in by_record.items():
        first = doc_chunks[0]
        preview = first.context_text[:200].replace("\n", " ")
        print(
            f"\n[{first.heritage_name}] ({record_id}) chunk_count: {len(doc_chunks)}"
        )
        print(f"preview: {preview}...")


if __name__ == "__main__":
    main()
