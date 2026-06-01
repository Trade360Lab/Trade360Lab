from __future__ import annotations

from pathlib import Path

from backtesting.run_backtest import execute_run, failure_output, normalize_metrics


def test_normalize_metrics_returns_expected_contract():
    metrics = normalize_metrics(
        {
            "net_profit": 12.34567,
            "win_rate": 0.582,
            "max_drawdown": 0.0612,
            "sharpe": 1.423456,
            "number_of_trades": 84,
        }
    )

    assert metrics == {
        "netProfit": 12.35,
        "winRate": 58.2,
        "maxDrawdown": 6.12,
        "sharpe": 1.4235,
        "trades": 84,
    }


def test_failure_output_returns_stable_json_contract():
    assert failure_output("Indicator period must be greater than 0") == {
        "status": "FAILED",
        "metrics": None,
        "errorMessage": "Indicator period must be greater than 0",
        "artifacts": None,
        "metadata": None,
    }


def test_execute_run_returns_success_contract_with_artifacts(tmp_path):
    payload = {
        "data_path": str(write_market_data(tmp_path)),
        "strategy_path": str(write_strategy(tmp_path)),
        "strategy_params": {"fast": 2, "slow": 3},
        "initial_cash": 1000.0,
    }

    output = execute_run(payload)

    assert output["status"] == "SUCCESS"
    assert output["errorMessage"] is None
    assert output["metrics"]["trades"] >= 1
    assert output["diagnostics"]["diagnosticsStatus"] in {"healthy", "mixed", "fragile"}
    assert output["diagnostics"]["trades"]["tradeCount"] >= 1
    assert output["artifacts"]["equityCurvePath"]
    assert output["artifacts"]["tradesPath"]
    assert output["artifacts"]["summaryPath"]
    assert output["metadata"]["dataset"]["rowCount"] >= 1


def write_market_data(tmp_path: Path) -> Path:
    path = tmp_path / "market.csv"
    path.write_text(
        "\n".join(
            [
                "timestamp,open,high,low,close,volume",
                "2024-01-01T00:00:00Z,10,10,10,10,100",
                "2024-01-02T00:00:00Z,10,10,10,10,100",
                "2024-01-03T00:00:00Z,10,11,10,11,100",
                "2024-01-04T00:00:00Z,11,12,11,12,100",
                "2024-01-05T00:00:00Z,9,9,9,9,100",
                "2024-01-06T00:00:00Z,9,9,8,8,100",
                "2024-01-07T00:00:00Z,10,10,10,10,100",
            ]
        ),
        encoding="utf-8",
    )
    return path


def write_strategy(tmp_path: Path) -> Path:
    path = tmp_path / "ma_crossover.py"
    path.write_text(
        """
from backtesting.strategy.base import Strategy as BaseStrategy


class Strategy(BaseStrategy):
    def initialize(self, context):
        return None

    def on_bar(self, bar, context):
        history = context.historical_data
        slow_window = int(self.params["slow"])
        fast_window = int(self.params["fast"])
        if len(history) < slow_window:
            return

        fast = history["close"].rolling(fast_window).mean().iloc[-1]
        slow = history["close"].rolling(slow_window).mean().iloc[-1]
        position = context.current_position

        if fast > slow and position is None:
            context.buy()
        elif fast < slow and position is not None:
            context.close()

    def finalize(self, context):
        return None
""".strip(),
        encoding="utf-8",
    )
    return path
