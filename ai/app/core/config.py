"""
Application configuration using Pydantic v2 settings.
"""
import json
from pydantic_settings import BaseSettings
from pydantic import Field, field_validator


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # Application
    APP_NAME: str = Field(default="ATORIA AI Service", validation_alias="APP_NAME")
    APP_VERSION: str = Field(default="0.1.0", validation_alias="APP_VERSION")
    DEBUG: bool = Field(default=False, validation_alias="DEBUG")
    LOG_LEVEL: str = Field(default="info", validation_alias="LOG_LEVEL")

    # Database
    POSTGRES_HOST: str = Field(default="localhost", validation_alias="POSTGRES_HOST")
    POSTGRES_PORT: int = Field(default=5432, validation_alias="POSTGRES_PORT")
    POSTGRES_DB: str = Field(default="atoria_ai", validation_alias="POSTGRES_DB")
    POSTGRES_USER: str = Field(default="postgres", validation_alias="POSTGRES_USER")
    POSTGRES_PASSWORD: str = Field(default="postgres", validation_alias="POSTGRES_PASSWORD")

    @property
    def DATABASE_URL(self) -> str:
        """Construct database URL from components."""
        return (
            f"postgresql+psycopg://{self.POSTGRES_USER}:{self.POSTGRES_PASSWORD}"
            f"@{self.POSTGRES_HOST}:{self.POSTGRES_PORT}/{self.POSTGRES_DB}"
        )

    # OpenAI (SSAFY GMS proxy)
    OPENAI_API_KEY: str = Field(default="", validation_alias="OPENAI_API_KEY")
    OPENAI_BASE_URL: str = Field(
        default="https://gms.ssafy.io/gmsapi/api.openai.com/v1",
        validation_alias="OPENAI_BASE_URL",
    )
    OPENAI_MODEL: str = Field(default="gpt-4o", validation_alias="OPENAI_MODEL")
    OPENAI_EMBEDDING_MODEL: str = Field(
        default="text-embedding-3-small", validation_alias="OPENAI_EMBEDDING_MODEL"
    )
    OPENAI_REQUEST_TIMEOUT: int = Field(default=60, validation_alias="OPENAI_REQUEST_TIMEOUT")

    # LLM Parameters
    LLM_MAX_TOKENS: int = Field(default=2048, validation_alias="LLM_MAX_TOKENS")
    LLM_TEMPERATURE: float = Field(default=0.7, validation_alias="LLM_TEMPERATURE")
    LLM_TOP_P: float = Field(default=0.9, validation_alias="LLM_TOP_P")

    # RAG Configuration
    CHUNK_SIZE: int = Field(default=500, validation_alias="CHUNK_SIZE")
    CHUNK_OVERLAP: int = Field(default=50, validation_alias="CHUNK_OVERLAP")
    NUM_RETRIEVAL_CHUNKS: int = Field(default=5, validation_alias="NUM_RETRIEVAL_CHUNKS")
    SIMILARITY_THRESHOLD: float = Field(default=0.6, validation_alias="SIMILARITY_THRESHOLD")

    # API Configuration
    CORS_ORIGINS: list[str] = Field(
        default=["http://localhost:3000", "http://localhost:8080"],
        validation_alias="CORS_ORIGINS",
    )
    API_V1_PREFIX: str = Field(default="/api/v1", validation_alias="API_V1_PREFIX")

    # Service Configuration
    ENABLE_AI_REQUEST_LOGGING: bool = Field(
        default=True, validation_alias="ENABLE_AI_REQUEST_LOGGING"
    )
    RETRY_ATTEMPTS: int = Field(default=3, validation_alias="RETRY_ATTEMPTS")
    RETRY_DELAY: int = Field(default=1, validation_alias="RETRY_DELAY")

    # RAG Storage / Embedding (147~148)
    RAG_DB_SCHEMA: str = Field(default="public", validation_alias="RAG_DB_SCHEMA")
    RAG_EMBEDDING_DIM: int = Field(default=1536, validation_alias="RAG_EMBEDDING_DIM")
    RAG_EMBEDDING_BATCH_SIZE: int = Field(
        default=50,
        validation_alias="RAG_EMBEDDING_BATCH_SIZE",
    )
    RAG_EMBEDDING_LIMIT: int = Field(
        default=100,
        validation_alias="RAG_EMBEDDING_LIMIT",
    )
    RAG_CHUNKS_READY_PATH: str = Field(
        default="data/processed/rag_chunks_ready.jsonl",
        validation_alias="RAG_CHUNKS_READY_PATH",
    )

    # RAG Search (149)
    RAG_SEARCH_DEFAULT_TOP_K: int = Field(
        default=5, validation_alias="RAG_SEARCH_DEFAULT_TOP_K"
    )
    RAG_SEARCH_MAX_TOP_K: int = Field(
        default=20, validation_alias="RAG_SEARCH_MAX_TOP_K"
    )
    RAG_SEARCH_DEFAULT_DISTANCE_THRESHOLD: float | None = Field(
        default=None, validation_alias="RAG_SEARCH_DEFAULT_DISTANCE_THRESHOLD"
    )

    # Story Generation (152)
    STORY_LLM_TEMPERATURE: float = Field(
        default=0.7, validation_alias="STORY_LLM_TEMPERATURE"
    )
    STORY_FACT_TOP_K: int = Field(default=2, validation_alias="STORY_FACT_TOP_K")
    STORY_MATERIAL_TOP_K: int = Field(default=3, validation_alias="STORY_MATERIAL_TOP_K")
    STORY_RAG_ENABLE_FALLBACK: bool = Field(
        default=True, validation_alias="STORY_RAG_ENABLE_FALLBACK"
    )
    STORY_RAG_METADATA_FIRST: bool = Field(
        default=True, validation_alias="STORY_RAG_METADATA_FIRST"
    )
    STORY_RAG_METADATA_MIN_RESULTS: int = Field(
        default=1, validation_alias="STORY_RAG_METADATA_MIN_RESULTS"
    )

    # RAG metadata-first search (canonical names — preferred by story flow)
    RAG_METADATA_FIRST_ENABLED: bool = Field(
        default=True, validation_alias="RAG_METADATA_FIRST_ENABLED"
    )
    RAG_VECTOR_FALLBACK_ENABLED: bool = Field(
        default=False, validation_alias="RAG_VECTOR_FALLBACK_ENABLED"
    )
    RAG_MIN_CHUNKS_PER_PLACE: int = Field(
        default=1, validation_alias="RAG_MIN_CHUNKS_PER_PLACE"
    )
    RAG_MAX_CHUNKS_PER_PLACE: int = Field(
        default=2, validation_alias="RAG_MAX_CHUNKS_PER_PLACE"
    )
    RAG_MAX_TOTAL_CHUNKS: int = Field(
        default=0, validation_alias="RAG_MAX_TOTAL_CHUNKS"
    )
    RAG_EMBEDDING_BATCH_ENABLED: bool = Field(
        default=True, validation_alias="RAG_EMBEDDING_BATCH_ENABLED"
    )

    # Story Quality Control (154)
    STORY_QUALITY_ENABLE_RETRY: bool = Field(
        default=True, validation_alias="STORY_QUALITY_ENABLE_RETRY"
    )
    STORY_QUALITY_MAX_RETRIES: int = Field(
        default=2, validation_alias="STORY_QUALITY_MAX_RETRIES"
    )
    STORY_MIN_INTRO_CHARS: int = Field(default=80, validation_alias="STORY_MIN_INTRO_CHARS")
    STORY_MAX_INTRO_CHARS: int = Field(default=900, validation_alias="STORY_MAX_INTRO_CHARS")
    STORY_MIN_OUTRO_CHARS: int = Field(default=60, validation_alias="STORY_MIN_OUTRO_CHARS")
    STORY_MAX_OUTRO_CHARS: int = Field(default=800, validation_alias="STORY_MAX_OUTRO_CHARS")
    STORY_MIN_PLACE_STORY_CHARS: int = Field(
        default=80, validation_alias="STORY_MIN_PLACE_STORY_CHARS"
    )
    STORY_MAX_PLACE_STORY_CHARS: int = Field(
        default=900, validation_alias="STORY_MAX_PLACE_STORY_CHARS"
    )
    STORY_MIN_MISSION_DESCRIPTION_CHARS: int = Field(
        default=30, validation_alias="STORY_MIN_MISSION_DESCRIPTION_CHARS"
    )

    # E-book Narrative Generation (158)
    EBOOK_NARRATIVE_USE_LLM: bool = Field(
        default=True, validation_alias="EBOOK_NARRATIVE_USE_LLM"
    )
    EBOOK_NARRATIVE_TEMPERATURE: float = Field(
        default=0.7, validation_alias="EBOOK_NARRATIVE_TEMPERATURE"
    )
    EBOOK_NARRATIVE_MAX_RETRIES: int = Field(
        default=1, validation_alias="EBOOK_NARRATIVE_MAX_RETRIES"
    )

    # Weather API (KMA AWS)
    WEATHER_KEY: str = Field(default="", validation_alias="WEATHER_KEY")

    # Data Pipeline
    ENABLE_LLM_SUMMARY: bool = Field(default=False, validation_alias="ENABLE_LLM_SUMMARY")
    CRAWL_POLITE_DELAY_SEC: float = Field(default=1.5, validation_alias="CRAWL_POLITE_DELAY_SEC")
    CRAWL_USER_AGENT: str = Field(
        default=(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        ),
        validation_alias="CRAWL_USER_AGENT",
    )

    @field_validator("CORS_ORIGINS", mode="before")
    @classmethod
    def assemble_cors_origins(cls, v: str | list[str]) -> list[str]:
        if isinstance(v, str) and not v.startswith("["):
            return [i.strip() for i in v.split(",") if i.strip()]
        elif isinstance(v, str):
            try:
                return json.loads(v)
            except ValueError:
                pass
        return v

    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()
