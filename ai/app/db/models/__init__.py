"""Register SQLAlchemy models so Base.metadata sees them."""
from app.db.models.rag_chunk import RagChunk
from app.db.models.rag_document import RagSourceDocument

__all__ = ["RagChunk", "RagSourceDocument"]
