"""
pytest configuration — 추후 fixture 추가용 스켈레톤.
"""
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.db.session import get_db


@pytest.fixture
def client():
    """Create test client."""
    return TestClient(app)
