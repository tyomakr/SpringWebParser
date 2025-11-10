from fastapi.testclient import TestClient

from main import app, Decision

client = TestClient(app)


def test_recommend_returns_entries():
    response = client.post(
        "/recommend",
        json={
            "images": [
                {"id": "a", "url": "https://example.com/1.jpg"},
                {"id": "b", "url": "https://example.com/2.jpg"},
            ]
        },
    )
    assert response.status_code == 200
    data = response.json()
    assert "recommendations" in data
    assert len(data["recommendations"]) == 2
    for rec in data["recommendations"]:
        assert rec["id"] in {"a", "b"}
        assert rec["url"].startswith("https://example.com")
        assert rec["reason"] in {"high score", "medium score", "low score"}


def test_decisions_are_valid():
    response = client.post(
        "/recommend",
        json={
            "images": [
                {"id": "c", "url": "https://example.com/long-url"},
            ]
        },
    )
    rec = response.json()["recommendations"][0]
    assert rec["decision"] in {d.value for d in Decision}


def test_api_key_validation(monkeypatch):
    monkeypatch.setenv("ML_PUBLISH_API_KEY", "secret")
    response = client.post(
        "/recommend",
        json={"images": [{"id": "x", "url": "https://example.com/1.jpg"}]},
    )
    assert response.status_code == 401


def test_api_key_allows_valid(monkeypatch):
    monkeypatch.setenv("ML_PUBLISH_API_KEY", "secret")
    response = client.post(
        "/recommend",
        json={"images": [{"id": "x", "url": "https://example.com/1.jpg"}]},
        headers={"Authorization": "Bearer secret"},
    )
    assert response.status_code == 200
