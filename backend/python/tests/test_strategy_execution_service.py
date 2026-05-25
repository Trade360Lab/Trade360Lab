from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path

from parser.candles.models.candle import Candle
from parser.runs.dto.run_execute_dto import RunExecuteRequest
from parser.runs.services.strategy_execution_service import StrategyExecutionService


class FakeCandleRepository:
    def __init__(self, candles):
        self.candles = candles
        self.calls = []

    def find_by_market_range(self, **kwargs):
        self.calls.append(kwargs)
        return self.candles


def build_request(strategy_file_path: str, **overrides) -> RunExecuteRequest:
    payload = {
        "strategyFilePath": strategy_file_path,
        "exchange": " Binance ",
        "symbol": " btcusdt ",
        "interval": "1h",
        "from": "2024-01-01T00:00:00Z",
        "to": "2024-01-01T02:00:00Z",
        "params": {"fast": 9},
        "runId": "101",
        "jobId": "501",
        "correlationId": "run-101",
    }
    payload.update(overrides)
    return RunExecuteRequest(**payload)


def write_strategy(tmp_path: Path, name: str, body: str) -> str:
    file_path = tmp_path / name
    file_path.write_text(body, encoding="utf-8")
    return str(file_path)


def sample_candle() -> Candle:
    return Candle(
        exchange="binance",
        symbol="BTCUSDT",
        interval="1h",
        open_time=datetime(2024, 1, 1, 0, 0, 0, tzinfo=UTC),
        close_time=datetime(2024, 1, 1, 1, 0, 0, tzinfo=UTC),
        open=Decimal("1.0"),
        high=Decimal("2.0"),
        low=Decimal("0.5"),
        close=Decimal("1.5"),
        volume=Decimal("10.0"),
    )


def test_execute_returns_metrics_for_valid_strategy(tmp_path):
    strategy_path = write_strategy(
        tmp_path,
        "valid_strategy.py",
        """
class Strategy:
    def run(self, candles, params):
        assert candles[0]["open"] == 1.0
        return {
            "metrics": {"total_return": params["fast"]},
            "trades": [
                {
                    "entry_time": "2024-01-01T00:00:00Z",
                    "exit_time": "2024-01-01T01:00:00Z",
                    "entry_price": 1.0,
                    "exit_price": 2.0,
                    "quantity": 1.0,
                    "pnl": 1.0,
                    "fee": 0.0,
                }
            ],
            "equityCurve": [
                {"timestamp": "2024-01-01T00:00:00Z", "equity": 100.0},
                {"timestamp": "2024-01-01T01:00:00Z", "equity": 101.0},
            ],
        }
""".strip(),
    )
    repository = FakeCandleRepository([sample_candle()])

    response = StrategyExecutionService(repository).execute(build_request(strategy_path))

    assert response.success is True
    assert response.metrics == {"total_return": 9}
    assert response.error is None
    assert response.run_id == "101"
    assert response.job_id == "501"
    assert response.correlation_id == "run-101"
    assert response.diagnostics is not None
    assert response.diagnostics.trades.trade_count == 1
    assert response.diagnostics.risk.max_drawdown == 0
    assert response.started_at is not None
    assert response.finished_at is not None
    assert response.execution_duration_ms is not None
    assert repository.calls[0]["exchange"] == "binance"
    assert repository.calls[0]["symbol"] == "BTCUSDT"


def test_execute_rejects_invalid_datetime(tmp_path):
    strategy_path = write_strategy(
        tmp_path,
        "valid_strategy.py",
        """
class Strategy:
    def run(self, candles, params):
        return {"metrics": {"total_return": 1}}
""".strip(),
    )
    repository = FakeCandleRepository([sample_candle()])

    response = StrategyExecutionService(repository).execute(
        build_request(strategy_path, **{"from": "not-a-date"})
    )

    assert response.success is False
    assert response.error_code == "INVALID_FROM_DATETIME"
    assert response.error_message == "Invalid datetime for 'from': not-a-date"
    assert response.error == "Invalid datetime for 'from': not-a-date"
    assert response.execution_duration_ms is not None


def test_execute_rejects_strategy_result_without_metrics(tmp_path):
    strategy_path = write_strategy(
        tmp_path,
        "invalid_result_strategy.py",
        """
class Strategy:
    def run(self, candles, params):
        return {"summary": {}}
""".strip(),
    )
    repository = FakeCandleRepository([sample_candle()])

    response = StrategyExecutionService(repository).execute(build_request(strategy_path))

    assert response.success is False
    assert response.error_code == "RESULT_METRICS_MISSING"
    assert response.error_message == "metrics missing in result"


def test_execute_returns_structured_runtime_error(tmp_path):
    strategy_path = write_strategy(
        tmp_path,
        "runtime_error_strategy.py",
        """
class Strategy:
    def run(self, candles, params):
        raise ValueError("boom")
""".strip(),
    )
    repository = FakeCandleRepository([sample_candle()])

    response = StrategyExecutionService(repository).execute(build_request(strategy_path))

    assert response.success is False
    assert response.error_code == "STRATEGY_RUNTIME_ERROR"
    assert response.error_message == "Strategy.run raised exception: boom"
    assert "ValueError: boom" in response.stacktrace
