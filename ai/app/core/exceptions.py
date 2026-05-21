"""
Custom exceptions for ATORIA AI service.
"""


class AIDomainException(Exception):
    """Base exception for all AI service errors."""

    def __init__(self, message: str, code: str = "AI_ERROR") -> None:
        self.message = message
        self.code = code
        super().__init__(self.message)


class IngestionException(AIDomainException):
    """Raised when document ingestion fails."""

    def __init__(self, message: str, code: str = "INGESTION_ERROR") -> None:
        super().__init__(message, code)


class ChunkingException(AIDomainException):
    """Raised when text chunking fails."""

    def __init__(self, message: str, code: str = "CHUNKING_ERROR") -> None:
        super().__init__(message, code)


class EmbeddingException(AIDomainException):
    """Raised when embedding generation fails."""

    def __init__(self, message: str, code: str = "EMBEDDING_ERROR") -> None:
        super().__init__(message, code)


class RetrievalException(AIDomainException):
    """Raised when retrieval/search fails."""

    def __init__(self, message: str, code: str = "RETRIEVAL_ERROR") -> None:
        super().__init__(message, code)


class StoryGenerationException(AIDomainException):
    """Raised when story generation fails."""

    def __init__(self, message: str, code: str = "STORY_GENERATION_ERROR") -> None:
        super().__init__(message, code)


class LLMException(AIDomainException):
    """Raised when LLM API calls fail."""

    def __init__(self, message: str, code: str = "LLM_ERROR") -> None:
        super().__init__(message, code)


class ValidationException(AIDomainException):
    """Raised when schema/data validation fails."""

    def __init__(self, message: str, code: str = "VALIDATION_ERROR") -> None:
        super().__init__(message, code)


class DatabaseException(AIDomainException):
    """Raised when database operations fail."""

    def __init__(self, message: str, code: str = "DATABASE_ERROR") -> None:
        super().__init__(message, code)
