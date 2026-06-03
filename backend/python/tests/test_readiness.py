import asyncio
import json

from parser.main import app


class FakeCursor:
    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def execute(self, _query):
        return None


class FakeConnection:
    def cursor(self):
        return FakeCursor()

    def close(self):
        return None


def test_readiness_does_not_expose_internal_secret(monkeypatch) -> None:
    monkeypatch.setattr("parser.main.get_connection", lambda: FakeConnection())
    messages: list[dict[str, object]] = []

    async def receive() -> dict[str, object]:
        return {"type": "http.request", "body": b"", "more_body": False}

    async def send(message: dict[str, object]) -> None:
        messages.append(message)

    scope = {
        "type": "http",
        "asgi": {"version": "3.0"},
        "http_version": "1.1",
        "method": "GET",
        "scheme": "http",
        "path": "/readiness",
        "raw_path": b"/readiness",
        "query_string": b"",
        "headers": [(b"host", b"testserver")],
        "client": ("testclient", 50000),
        "server": ("testserver", 80),
    }

    asyncio.run(app(scope, receive, send))

    response_start = next(
        message for message in messages if message["type"] == "http.response.start"
    )
    response_body = next(message for message in messages if message["type"] == "http.response.body")
    payload = json.loads(response_body["body"])

    assert response_start["status"] == 200
    assert payload["service"] == "python-parser"
    assert payload["parserVersion"] == "0.9.6-alpha.1"
    assert "secret" not in payload
    assert "internalAuthConfigured" in payload
