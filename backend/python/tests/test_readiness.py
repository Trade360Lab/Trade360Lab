from fastapi.testclient import TestClient

from parser.main import app


def test_readiness_does_not_expose_internal_secret() -> None:
    client = TestClient(app)

    response = client.get("/readiness")

    assert response.status_code == 200
    payload = response.json()
    assert payload["service"] == "python-parser"
    assert payload["parserVersion"] == "0.9.5-alpha.1"
    assert "secret" not in payload
    assert "internalAuthConfigured" in payload
