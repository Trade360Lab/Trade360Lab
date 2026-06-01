from __future__ import annotations

import json
import sys
import tempfile
from typing import Any

from backtesting.artifacts.writer import JsonArtifactWriter
from backtesting.engine.backtest_engine import BacktestConfig, BacktestEngine
from backtesting.execution.context import ExecutionContext
from parser.services.backtest_diagnostics import calculate_backtest_diagnostics


def execute_run(payload: dict[str, Any]) -> dict[str, Any]:
    data_path = payload.get("data_path")
    strategy_path = payload.get("strategy_path")
    if not data_path or not strategy_path:
        raise ValueError("Missing required fields: data_path and strategy_path")

    config = BacktestConfig(
        initial_cash=float(payload.get("initial_cash", 10_000.0)),
        fee_rate=float(payload.get("fee_rate", 0.0)),
        slippage_bps=float(payload.get("slippage_bps", 0.0)),
        strict_data=bool(payload.get("strict_data", True)),
    )

    engine = BacktestEngine(config=config)
    with tempfile.TemporaryDirectory(prefix="backtest-artifacts-") as output_dir:
        context = ExecutionContext(
            strategy_path=strategy_path,
            data_path=data_path,
            output_dir=output_dir,
            run_id=payload.get("run_id"),
            correlation_id=payload.get("correlation_id"),
        )
        result = engine.run_from_context(
            context=context,
            strategy_params=payload.get("strategy_params") or {},
        )
        artifacts = JsonArtifactWriter().write(context=context, result=result)
        result_payload = result.to_dict()
        diagnostics = calculate_backtest_diagnostics(
            summary=result_payload.get("summary", {}),
            metrics=normalize_metrics(result_payload.get("summary", {})),
            trades=result_payload.get("trades", []),
            equity_curve=result_payload.get("equity_curve", []),
            starting_equity=config.initial_cash,
        )
        output = {
            "status": "SUCCESS",
            "metrics": normalize_metrics(result_payload.get("summary", {})),
            "errorMessage": None,
            "artifacts": artifacts,
            "diagnostics": diagnostics,
            "metadata": result.metadata,
        }
        persisted_artifacts = materialize_artifacts(artifacts)

    output["artifacts"] = persisted_artifacts
    return output


def main() -> int:
    raw_input = sys.stdin.read()
    if not raw_input.strip():
        output = failure_output("Empty input payload")
        sys.stdout.write(json.dumps(output))
        sys.stdout.write("\n")
        return 2

    try:
        payload = json.loads(raw_input)
        if not isinstance(payload, dict):
            raise ValueError("Input payload must be a JSON object")
    except (json.JSONDecodeError, ValueError) as exc:
        output = failure_output(f"Invalid JSON payload: {exc}")
        sys.stdout.write(json.dumps(output))
        sys.stdout.write("\n")
        return 2

    try:
        output = execute_run(payload)
        exit_code = 0
    except Exception as exc:
        output = failure_output(str(exc))
        exit_code = 1

    sys.stdout.write(json.dumps(output))
    sys.stdout.write("\n")
    return exit_code


def normalize_metrics(summary: dict[str, Any]) -> dict[str, Any]:
    return {
        "netProfit": round_number(summary.get("net_profit")),
        "winRate": to_percent(summary.get("win_rate")),
        "maxDrawdown": to_percent(summary.get("max_drawdown")),
        "sharpe": round_number(summary.get("sharpe"), digits=4),
        "trades": to_int(summary.get("number_of_trades")),
    }


def materialize_artifacts(artifacts: dict[str, object]) -> dict[str, object]:
    persisted = dict(artifacts)
    for key in (
        "tradesPath",
        "equityCurvePath",
        "summaryPath",
        "logsPath",
        "warningsPath",
    ):
        value = artifacts.get(key)
        if isinstance(value, str):
            persisted[key] = write_artifact_file(key.replace("Path", "-"), value)
    return persisted


def write_artifact_file(prefix: str, source_path: str) -> str:
    with open(source_path, encoding="utf-8") as source_file:
        payload = json.load(source_file)

    with tempfile.NamedTemporaryFile(
        mode="w",
        suffix=".json",
        prefix=f"backtest-{prefix}",
        delete=False,
        encoding="utf-8",
    ) as artifact_file:
        json.dump(make_json_safe(payload), artifact_file)
        artifact_file.write("\n")
        return artifact_file.name


def make_json_safe(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: make_json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [make_json_safe(item) for item in value]
    if isinstance(value, float):
        return round(value, 4)
    return value


def failure_output(message: str) -> dict[str, Any]:
    return {
        "status": "FAILED",
        "metrics": None,
        "errorMessage": message or "Backtest execution failed",
        "artifacts": None,
        "metadata": None,
    }


def round_number(value: Any, digits: int = 2) -> float:
    try:
        return round(float(value), digits)
    except (TypeError, ValueError):
        return 0.0


def to_percent(value: Any) -> float:
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        return 0.0

    return round(numeric * 100, 2)


def to_int(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
