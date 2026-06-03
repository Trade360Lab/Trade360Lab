import importlib.util
import logging
import sys
import time
import traceback
from datetime import UTC, datetime
from pathlib import Path
from types import ModuleType
from uuid import uuid4

from parser.candles.models.candle import Candle
from parser.candles.repositories.candle_repository import CandleRepository
from parser.common.util.logging import bind_log_context
from parser.runs.dto.run_execute_dto import RunExecuteRequest, RunExecuteResponse
from parser.services.backtest_diagnostics import calculate_backtest_diagnostics

logger = logging.getLogger(__name__)


ENGINE_VERSION = "python-execution-engine/0.9.6-alpha.1"


class StrategyExecutionService:
    def __init__(self, candle_repository: CandleRepository) -> None:
        self.candle_repository = candle_repository

    def execute(self, request: RunExecuteRequest) -> RunExecuteResponse:
        started_at = datetime.now(tz=UTC)
        started_monotonic = time.perf_counter()
        with bind_log_context(
            correlation_id=request.correlation_id,
            run_id=request.run_id,
            job_id=request.job_id,
        ):
            exchange = request.exchange.strip().lower()
            symbol = request.symbol.strip().upper()
            interval = request.interval.strip()
            from_time = self._parse_datetime(request.from_time, "from")
            if from_time is None:
                return self._failed(
                    request=request,
                    started_at=started_at,
                    started_monotonic=started_monotonic,
                    error_code="INVALID_FROM_DATETIME",
                    error_message=f"Invalid datetime for 'from': {request.from_time}",
                )

            to_time = self._parse_datetime(request.to_time, "to")
            if to_time is None:
                return self._failed(
                    request=request,
                    started_at=started_at,
                    started_monotonic=started_monotonic,
                    error_code="INVALID_TO_DATETIME",
                    error_message=f"Invalid datetime for 'to': {request.to_time}",
                )

            strategy_path = Path(request.strategy_file_path).expanduser().resolve(strict=False)

            logger.info(
                "Starting strategy run",
                extra={
                    "event": "run_started",
                    "user_id": request.user_id,
                    "strategy_id": request.strategy_id,
                    "strategy_version_id": request.strategy_version_id,
                    "strategy_file": strategy_path.name,
                    "exchange": exchange,
                    "symbol": symbol,
                    "interval": interval,
                    "from_time": from_time.isoformat(),
                    "to_time": to_time.isoformat(),
                },
            )

            if from_time >= to_time:
                return self._failed(
                    request=request,
                    started_at=started_at,
                    started_monotonic=started_monotonic,
                    error_code="INVALID_TIME_RANGE",
                    error_message="'from' must be earlier than 'to'",
                )

            if not strategy_path.exists():
                return self._failed(
                    request=request,
                    started_at=started_at,
                    started_monotonic=started_monotonic,
                    error_code="STRATEGY_FILE_NOT_FOUND",
                    error_message=f"Strategy file does not exist: {strategy_path}",
                )

            if not strategy_path.is_file():
                return self._failed(
                    request=request,
                    started_at=started_at,
                    started_monotonic=started_monotonic,
                    error_code="STRATEGY_PATH_INVALID",
                    error_message=f"Strategy path is not a file: {strategy_path}",
                )

            module_name = f"strategy_execution_{uuid4().hex}"

            try:
                try:
                    module = self._load_module(module_name, strategy_path)
                except Exception as exc:  # noqa: BLE001
                    logger.exception(
                        "Failed to load strategy module",
                        extra={
                            "event": "strategy_load_failed",
                            "strategy_file": strategy_path.name,
                        },
                    )
                    return self._failed(
                        request=request,
                        started_at=started_at,
                        started_monotonic=started_monotonic,
                        error_code="STRATEGY_LOAD_ERROR",
                        error_message=f"Failed to load strategy module: {exc}",
                        stacktrace=traceback.format_exc(),
                    )

                strategy_class = getattr(module, "Strategy", None)
                if strategy_class is None or not isinstance(strategy_class, type):
                    return self._failed(
                        request=request,
                        started_at=started_at,
                        started_monotonic=started_monotonic,
                        error_code="STRATEGY_CLASS_NOT_FOUND",
                        error_message="Strategy class not found",
                    )

                try:
                    strategy = strategy_class()
                except Exception as exc:  # noqa: BLE001
                    logger.exception(
                        "Failed to instantiate strategy",
                        extra={
                            "event": "strategy_init_failed",
                            "strategy_file": strategy_path.name,
                        },
                    )
                    return self._failed(
                        request=request,
                        started_at=started_at,
                        started_monotonic=started_monotonic,
                        error_code="STRATEGY_INIT_ERROR",
                        error_message=f"Failed to instantiate Strategy: {exc}",
                        stacktrace=traceback.format_exc(),
                    )

                run_method = getattr(strategy, "run", None)
                if not callable(run_method):
                    return self._failed(
                        request=request,
                        started_at=started_at,
                        started_monotonic=started_monotonic,
                        error_code="STRATEGY_RUN_METHOD_MISSING",
                        error_message="Strategy.run method not found",
                    )

                candles = self.candle_repository.find_by_market_range(
                    exchange=exchange,
                    symbol=symbol,
                    interval=interval,
                    from_time=from_time,
                    to_time=to_time,
                )
                if not candles:
                    return self._failed(
                        request=request,
                        started_at=started_at,
                        started_monotonic=started_monotonic,
                        error_code="CANDLES_NOT_FOUND",
                        error_message="candles not found for requested range",
                    )

                candles_payload = [self._serialize_candle(candle) for candle in candles]
                logger.info(
                    "Loaded candles for strategy run",
                    extra={
                        "event": "candles_loaded",
                        "strategy_file": strategy_path.name,
                        "candles_count": len(candles_payload),
                    },
                )

                try:
                    result = run_method(candles_payload, request.params)
                except Exception as exc:  # noqa: BLE001
                    logger.exception(
                        "Strategy.run raised exception",
                        extra={
                            "event": "strategy_runtime_failed",
                            "strategy_file": strategy_path.name,
                        },
                    )
                    return self._failed(
                        request=request,
                        started_at=started_at,
                        started_monotonic=started_monotonic,
                        error_code="STRATEGY_RUNTIME_ERROR",
                        error_message=f"Strategy.run raised exception: {exc}",
                        stacktrace=traceback.format_exc(),
                    )

                if not isinstance(result, dict):
                    return self._failed(
                        request=request,
                        started_at=started_at,
                        started_monotonic=started_monotonic,
                        error_code="INVALID_RESULT",
                        error_message="invalid run result",
                    )

                if "metrics" not in result:
                    return self._failed(
                        request=request,
                        started_at=started_at,
                        started_monotonic=started_monotonic,
                        error_code="RESULT_METRICS_MISSING",
                        error_message="metrics missing in result",
                    )

                metrics = result["metrics"]
                if not isinstance(metrics, dict):
                    return self._failed(
                        request=request,
                        started_at=started_at,
                        started_monotonic=started_monotonic,
                        error_code="INVALID_RESULT",
                        error_message="invalid run result",
                    )

                summary = self._coerce_map(result.get("summary"), fallback=metrics)
                trades = self._coerce_trades(result.get("trades"))
                equity_curve = self._coerce_equity_curve(
                    result.get("equity_curve", result.get("equityCurve"))
                )
                artifacts = self._coerce_map(
                    result.get("artifacts"),
                    fallback={
                        "tradesCount": len(trades),
                        "equityPointCount": len(equity_curve),
                    },
                )
                diagnostics = calculate_backtest_diagnostics(
                    summary=summary,
                    metrics=metrics,
                    trades=trades,
                    equity_curve=equity_curve,
                )

                finished_at = datetime.now(tz=UTC)
                execution_duration_ms = self._duration_ms(started_monotonic)
                logger.info(
                    "Strategy run completed successfully",
                    extra={
                        "event": "run_completed",
                        "strategy_file": strategy_path.name,
                        "candles_count": len(candles_payload),
                        "execution_duration_ms": execution_duration_ms,
                    },
                )
                return RunExecuteResponse(
                    success=True,
                    summary=summary,
                    metrics=metrics,
                    trades=trades,
                    equity_curve=equity_curve,
                    artifacts=artifacts,
                    diagnostics=diagnostics,
                    engine_version=ENGINE_VERSION,
                    run_id=request.run_id,
                    job_id=request.job_id,
                    correlation_id=request.correlation_id,
                    started_at=started_at.isoformat().replace("+00:00", "Z"),
                    finished_at=finished_at.isoformat().replace("+00:00", "Z"),
                    execution_duration_ms=execution_duration_ms,
                    error_code=None,
                    error_message=None,
                    stacktrace=None,
                    error=None,
                )
            except Exception as exc:  # noqa: BLE001
                logger.exception(
                    "Strategy execution failed",
                    extra={"event": "run_failed_unexpected", "strategy_file": strategy_path.name},
                )
                return self._failed(
                    request=request,
                    started_at=started_at,
                    started_monotonic=started_monotonic,
                    error_code="UNEXPECTED_ERROR",
                    error_message=str(exc),
                    stacktrace=traceback.format_exc(),
                )
            finally:
                sys.modules.pop(module_name, None)

    @staticmethod
    def _load_module(module_name: str, file_path: Path) -> ModuleType:
        spec = importlib.util.spec_from_file_location(module_name, file_path)
        if spec is None or spec.loader is None:
            raise ValueError(f"Unable to load strategy module from {file_path}")

        module = importlib.util.module_from_spec(spec)
        sys.modules[module_name] = module
        spec.loader.exec_module(module)
        return module

    @staticmethod
    def _parse_datetime(value: str, field_name: str) -> datetime | None:
        try:
            normalized = value.replace("Z", "+00:00")
            parsed = datetime.fromisoformat(normalized)
        except ValueError:
            logger.warning(
                "Invalid datetime provided",
                extra={"event": "invalid_datetime", "field_name": field_name, "value": value},
            )
            return None

        if parsed.tzinfo is None:
            return parsed.replace(tzinfo=UTC)
        return parsed.astimezone(UTC)

    @staticmethod
    def _normalize_datetime(value: datetime) -> datetime:
        if value.tzinfo is None:
            return value.replace(tzinfo=UTC)
        return value.astimezone(UTC)

    @staticmethod
    def _serialize_candle(candle: Candle) -> dict[str, object]:
        return {
            "open_time": StrategyExecutionService._normalize_datetime(candle.open_time).isoformat(),
            "close_time": StrategyExecutionService._normalize_datetime(
                candle.close_time
            ).isoformat(),
            "open": float(candle.open),
            "high": float(candle.high),
            "low": float(candle.low),
            "close": float(candle.close),
            "volume": float(candle.volume),
        }

    @staticmethod
    def _failed(
        request: RunExecuteRequest,
        started_at: datetime,
        started_monotonic: float,
        error_code: str,
        error_message: str,
        stacktrace: str | None = None,
    ) -> RunExecuteResponse:
        finished_at = datetime.now(tz=UTC)
        execution_duration_ms = StrategyExecutionService._duration_ms(started_monotonic)
        logger.warning(
            "Strategy execution failed",
            extra={
                "event": "run_failed",
                "error_code": error_code,
                "error_message": error_message,
                "execution_duration_ms": execution_duration_ms,
            },
        )
        return RunExecuteResponse(
            success=False,
            summary=None,
            metrics=None,
            trades=[],
            equity_curve=[],
            artifacts=None,
            engine_version=ENGINE_VERSION,
            run_id=request.run_id,
            job_id=request.job_id,
            correlation_id=request.correlation_id,
            started_at=started_at.isoformat().replace("+00:00", "Z"),
            finished_at=finished_at.isoformat().replace("+00:00", "Z"),
            execution_duration_ms=execution_duration_ms,
            error_code=error_code,
            error_message=error_message,
            stacktrace=stacktrace,
            error=error_message,
        )

    @staticmethod
    def _coerce_map(value: object, *, fallback: dict[str, object]) -> dict[str, object]:
        if isinstance(value, dict):
            return value
        return fallback

    @staticmethod
    def _coerce_trades(value: object) -> list[dict[str, object]]:
        if not isinstance(value, list):
            return []

        trades: list[dict[str, object]] = []
        for item in value:
            if isinstance(item, dict):
                trades.append(
                    {
                        "entry_time": item.get("entry_time", item.get("entryTime")),
                        "exit_time": item.get("exit_time", item.get("exitTime")),
                        "entry_price": float(item.get("entry_price", item.get("entryPrice", 0.0))),
                        "exit_price": float(item.get("exit_price", item.get("exitPrice", 0.0))),
                        "quantity": float(item.get("quantity", item.get("qty", 0.0))),
                        "pnl": float(item.get("pnl", 0.0)),
                        "fee": float(item.get("fee", 0.0)),
                    }
                )
        return trades

    @staticmethod
    def _coerce_equity_curve(value: object) -> list[dict[str, object]]:
        if not isinstance(value, list):
            return []

        points: list[dict[str, object]] = []
        for item in value:
            if isinstance(item, dict) and item.get("timestamp") is not None:
                points.append(
                    {
                        "timestamp": str(item["timestamp"]),
                        "equity": float(item.get("equity", 0.0)),
                    }
                )
        return points

    @staticmethod
    def _duration_ms(started_monotonic: float) -> int:
        return int((time.perf_counter() - started_monotonic) * 1000)
