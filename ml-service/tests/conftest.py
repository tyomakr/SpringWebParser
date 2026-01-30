import os
import pytest

os.environ["SEMANTIC_BACKEND"] = "lite"
os.environ["SIMILARITY_MODE"] = "phash"
os.environ["SYNC_STARTUP"] = "false"
os.environ["OCR_ENABLED"] = "false"


@pytest.fixture(autouse=True, scope="session")
def _normalize_test_env():
    # Ensure overrides are applied even if a test mutates env at runtime.
    os.environ["SEMANTIC_BACKEND"] = "lite"
    os.environ["SIMILARITY_MODE"] = "phash"
    os.environ["SYNC_STARTUP"] = "false"
    os.environ["OCR_ENABLED"] = "false"
