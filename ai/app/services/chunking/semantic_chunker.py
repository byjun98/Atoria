"""
Semantic chunker for ChunkingSourceDocument → list[RagChunk].

- 문단(\n\n) → 문장 → 청크 순으로 처리.
- target ~450자, min ~150자, max ~700자.
- 짧은 문서도 최소 1개 청크는 생성.
"""
from __future__ import annotations

import re

from app.schemas.chunk_schema import ChunkingSourceDocument, RagChunk
from app.services.chunking.context_builder import ContextBuilder
from app.services.chunking.mission_hook_extractor import MissionHookExtractor


# Korean sentence terminator. Splits after . ! ? 。 다. and Korean enders.
# Keeps the terminator with the preceding sentence.
_SENT_SPLIT_RE = re.compile(r"(?<=[.!?。])\s+|(?<=다\.)\s+|(?<=요\.)\s+|(?<=습니다\.)\s+")
_PARAGRAPH_SPLIT_RE = re.compile(r"\n\s*\n+")


class SemanticChunker:
    def __init__(
        self,
        target_chunk_size: int = 450,
        min_chunk_size: int = 150,
        max_chunk_size: int = 700,
        overlap_size: int = 80,
        context_builder: ContextBuilder | None = None,
        mission_hook_extractor: MissionHookExtractor | None = None,
    ) -> None:
        if not (0 < min_chunk_size <= target_chunk_size <= max_chunk_size):
            raise ValueError("min <= target <= max chunk sizes required")
        self.target = target_chunk_size
        self.min_size = min_chunk_size
        self.max_size = max_chunk_size
        self.overlap = max(0, overlap_size)
        self.context_builder = context_builder or ContextBuilder()
        self.mission_hook_extractor = mission_hook_extractor or MissionHookExtractor()

    # ---- public API ------------------------------------------------------

    def chunk_documents(
        self, documents: list[ChunkingSourceDocument]
    ) -> list[RagChunk]:
        out: list[RagChunk] = []
        for doc in documents:
            out.extend(self.chunk_document(doc))
        return out

    def chunk_document(self, document: ChunkingSourceDocument) -> list[RagChunk]:
        text = (document.text or "").strip()
        if not text:
            return []

        raw_chunks = self._split_text(text)
        if not raw_chunks:
            raw_chunks = [text]

        results: list[RagChunk] = []
        for idx, raw in enumerate(raw_chunks):
            raw = raw.strip()
            if not raw:
                continue
            context_text = self.context_builder.build_context_text(raw, document)
            mission_hooks = self.mission_hook_extractor.extract(raw, document)
            chunk = RagChunk(
                chunk_id=f"{document.source_record_id}_chunk_{idx:03d}",
                source_record_id=document.source_record_id,
                source_type=document.source_type,
                source_site=document.source_site,
                source_urls=list(document.source_urls),
                content_role=document.content_role,
                heritage_name=document.heritage_name,
                related_heritages=list(document.related_heritages),
                related_people=list(document.related_people),
                related_places=list(document.related_places),
                title=document.title,
                chunk_index=idx,
                factuality_level=document.factuality_level,
                narrative_type=document.narrative_type,
                folklore_status=document.folklore_status,
                source_confidence=document.source_confidence,
                needs_review=document.needs_review,
                era=document.era,
                region=document.region,
                motifs=list(document.motifs),
                tone_tags=list(document.tone_tags),
                story_hooks=list(document.story_hooks),
                mission_keywords=list(document.mission_keywords),
                mission_hooks=mission_hooks,
                raw_text=raw,
                context_text=context_text,
                char_count=len(raw),
                metadata=dict(document.metadata),
            )
            results.append(chunk)
        return results

    # ---- splitting helpers ----------------------------------------------

    def split_paragraphs(self, text: str) -> list[str]:
        paragraphs = [p.strip() for p in _PARAGRAPH_SPLIT_RE.split(text)]
        return [p for p in paragraphs if p]

    def split_sentences(self, text: str) -> list[str]:
        # Single-line text → split on sentence terminators.
        sentences = [s.strip() for s in _SENT_SPLIT_RE.split(text)]
        return [s for s in sentences if s]

    def merge_sentences_into_chunks(self, sentences: list[str]) -> list[str]:
        chunks: list[str] = []
        buf: list[str] = []
        buf_len = 0
        for sent in sentences:
            sent_len = len(sent)
            if buf and buf_len + 1 + sent_len > self.max_size:
                chunks.append(" ".join(buf))
                # overlap: carry the tail of the last chunk
                if self.overlap and chunks[-1]:
                    tail = chunks[-1][-self.overlap :]
                    buf = [tail]
                    buf_len = len(tail)
                else:
                    buf = []
                    buf_len = 0
            buf.append(sent)
            buf_len += sent_len + (1 if buf_len else 0)
            if buf_len >= self.target:
                chunks.append(" ".join(buf))
                if self.overlap and chunks[-1]:
                    tail = chunks[-1][-self.overlap :]
                    buf = [tail]
                    buf_len = len(tail)
                else:
                    buf = []
                    buf_len = 0
        if buf:
            chunks.append(" ".join(buf))

        # Merge a too-small final chunk into its predecessor when possible.
        if (
            len(chunks) >= 2
            and len(chunks[-1]) < self.min_size
            and len(chunks[-2]) + 1 + len(chunks[-1]) <= self.max_size
        ):
            chunks[-2] = chunks[-2] + " " + chunks[-1]
            chunks.pop()
        return chunks

    def _split_text(self, text: str) -> list[str]:
        paragraphs = self.split_paragraphs(text)
        if not paragraphs:
            return []

        # First, group small paragraphs together up to ~target,
        # and split long paragraphs into sentences.
        units: list[str] = []
        for p in paragraphs:
            if len(p) <= self.max_size:
                units.append(p)
            else:
                sentences = self.split_sentences(p)
                if not sentences:
                    units.append(p)
                else:
                    units.extend(self.merge_sentences_into_chunks(sentences))

        # Now greedily merge units into chunks within size limits.
        chunks: list[str] = []
        buf: list[str] = []
        buf_len = 0
        for u in units:
            if buf and buf_len + 2 + len(u) > self.max_size:
                chunks.append("\n\n".join(buf))
                buf = []
                buf_len = 0
            buf.append(u)
            buf_len += len(u) + (2 if buf_len else 0)
            if buf_len >= self.target:
                chunks.append("\n\n".join(buf))
                buf = []
                buf_len = 0
        if buf:
            chunks.append("\n\n".join(buf))

        # Merge tiny tail chunk into predecessor when possible.
        if (
            len(chunks) >= 2
            and len(chunks[-1]) < self.min_size
            and len(chunks[-2]) + 2 + len(chunks[-1]) <= self.max_size
        ):
            chunks[-2] = chunks[-2] + "\n\n" + chunks[-1]
            chunks.pop()

        # If the whole text is below min_size we still emit one chunk.
        if not chunks:
            chunks = [text]
        return chunks
