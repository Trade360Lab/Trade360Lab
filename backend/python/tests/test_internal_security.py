import asyncio
import json

from parser.main import app


def test_internal_run_endpoint_requires_shared_secret() -> None:
    messages: list[dict[str, object]] = []
    payload = json.dumps(
        {
            "strategyFilePath": "strategy.py",
            "exchange": "binance",
            "symbol": "BTCUSDT",
            "interval": "1h",
            "from": "2024-01-01T00:00:00Z",
            "to": "2024-01-01T01:00:00Z",
            "params": {},
            "runId": "1",
            "jobId": "job-1",
            "correlationId": "run-1",
        }
    ).encode()

    async def receive() -> dict[str, object]:
        return {"type": "http.request", "body": payload, "more_body": False}

    async def send(message: dict[str, object]) -> None:
        messages.append(message)

    scope = {
        "type": "http",
        "asgi": {"version": "3.0"},
        "http_version": "1.1",
        "method": "POST",
        "scheme": "http",
        "path": "/internal/runs/execute",
        "raw_path": b"/internal/runs/execute",
        "query_string": b"",
        "headers": [
            (b"host", b"testserver"),
            (b"content-type", b"application/json"),
            (b"x-correlation-id", b"run-1"),
            (b"x-run-id", b"1"),
            (b"x-job-id", b"job-1"),
        ],
        "client": ("testclient", 50000),
        "server": ("testserver", 80),
    }

    asyncio.run(app(scope, receive, send))

    response_start = next(
        message for message in messages if message["type"] == "http.response.start"
    )
    response_body = next(message for message in messages if message["type"] == "http.response.body")
    headers = {
        key.decode().lower(): value.decode()
        for key, value in response_start["headers"]
        if isinstance(key, bytes) and isinstance(value, bytes)
    }

    assert response_start["status"] == 401
    assert json.loads(response_body["body"])["message"] == "Unauthorized internal request"
    assert headers["x-correlation-id"] == "run-1"
    assert headers["x-run-id"] == "1"
    assert headers["x-job-id"] == "job-1"
