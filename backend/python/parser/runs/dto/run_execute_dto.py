from typing import Any

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class BacktestTradePayload(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    entry_time: str | None = None
    exit_time: str | None = None
    entry_price: float = 0.0
    exit_price: float = 0.0
    quantity: float = 0.0
    pnl: float = 0.0
    fee: float = 0.0


class EquityPointPayload(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    timestamp: str
    equity: float


class RunDiagnosticsRiskPayload(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    max_drawdown: float | None = None
    max_drawdown_pct: float | None = None
    drawdown_start: str | None = None
    drawdown_end: str | None = None
    recovery_bars: int | None = None


class RunDiagnosticsTradesPayload(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    trade_count: int = 0
    winning_trades: int = 0
    losing_trades: int = 0
    win_rate: float | None = None
    profit_factor: float | None = None
    average_win: float | None = None
    average_loss: float | None = None
    best_trade: float | None = None
    worst_trade: float | None = None
    longest_win_streak: int = 0
    longest_loss_streak: int = 0
    average_trade_pnl: float | None = None
    median_trade_pnl: float | None = None
    flat_trades: int = 0


class RunDiagnosticsStabilitySegmentPayload(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    segment_index: int = Field(alias="segmentIndex", serialization_alias="segmentIndex")
    from_time: str | None = Field(default=None, alias="from", serialization_alias="from")
    to_time: str | None = Field(default=None, alias="to", serialization_alias="to")
    pnl: float | None = None
    trade_count: int = 0
    max_drawdown: float | None = None
    status: str


class RunDiagnosticsStabilityPayload(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    segments: list[RunDiagnosticsStabilitySegmentPayload] = Field(default_factory=list)
    status: str


class RunDiagnosticsWarningPayload(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    code: str
    message: str
    severity: str


class RunDiagnosticsPayload(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    diagnostics_status: str
    diagnostics_summary: str
    total_pnl: float | None = None
    total_return_pct: float | None = None
    risk: RunDiagnosticsRiskPayload
    trades: RunDiagnosticsTradesPayload
    stability: RunDiagnosticsStabilityPayload
    warnings: list[RunDiagnosticsWarningPayload] = Field(default_factory=list)


class RunExecuteRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    strategy_file_path: str = Field(
        alias="strategyFilePath",
        serialization_alias="strategyFilePath",
        description="Absolute or service-local path to the strategy file.",
        examples=["/app/strategies/ema_cross.py"],
    )
    user_id: int | None = Field(
        default=None,
        alias="userId",
        serialization_alias="userId",
    )
    strategy_id: int | None = Field(
        default=None,
        alias="strategyId",
        serialization_alias="strategyId",
    )
    strategy_version_id: int | None = Field(
        default=None,
        alias="strategyVersionId",
        serialization_alias="strategyVersionId",
    )
    exchange: str = Field(description="Exchange code.", examples=["binance"])
    symbol: str = Field(description="Trading pair symbol.", examples=["BTCUSDT"])
    interval: str = Field(description="Candle interval.", examples=["1h"])
    from_time: str = Field(
        alias="from",
        serialization_alias="from",
        description="Execution range start in ISO-8601 UTC format.",
        examples=["2024-01-01T00:00:00Z"],
    )
    to_time: str = Field(
        alias="to",
        serialization_alias="to",
        description="Execution range end in ISO-8601 UTC format.",
        examples=["2024-01-31T23:59:59Z"],
    )
    params: dict[str, Any] = Field(
        default_factory=dict,
        description="Strategy runtime parameters.",
        examples=[{"fast_period": 9, "slow_period": 21}],
    )
    run_id: str | None = Field(
        default=None,
        alias="runId",
        serialization_alias="runId",
        description="Optional platform-level run identifier for logging correlation.",
        examples=["101"],
    )
    job_id: str | None = Field(
        default=None,
        alias="jobId",
        serialization_alias="jobId",
        description="Optional execution job identifier for queue/worker correlation.",
        examples=["501"],
    )
    correlation_id: str | None = Field(
        default=None,
        alias="correlationId",
        serialization_alias="correlationId",
        description="Optional correlation identifier propagated from the caller.",
        examples=["run-101"],
    )


class RunExecuteResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    success: bool = Field(description="Whether execution completed successfully.", examples=[True])
    summary: dict[str, Any] | None = Field(
        default=None,
        description="Execution summary payload.",
        examples=[{"netProfit": 0.124, "trades": 2}],
    )
    metrics: dict[str, Any] | None = Field(
        default=None,
        description="Strategy execution metrics payload.",
        examples=[{"total_return": 0.124, "max_drawdown": 0.038}],
    )
    trades: list[BacktestTradePayload] = Field(default_factory=list)
    equity_curve: list[EquityPointPayload] = Field(
        default_factory=list,
        alias="equityCurve",
        serialization_alias="equityCurve",
    )
    artifacts: dict[str, Any] | None = Field(default=None)
    diagnostics: RunDiagnosticsPayload | None = Field(default=None)
    engine_version: str | None = Field(
        default=None,
        alias="engineVersion",
        serialization_alias="engineVersion",
    )
    run_id: str | None = Field(
        default=None,
        alias="runId",
        serialization_alias="runId",
    )
    job_id: str | None = Field(
        default=None,
        alias="jobId",
        serialization_alias="jobId",
    )
    correlation_id: str | None = Field(
        default=None,
        alias="correlationId",
        serialization_alias="correlationId",
    )
    started_at: str | None = Field(
        default=None,
        alias="startedAt",
        serialization_alias="startedAt",
    )
    finished_at: str | None = Field(
        default=None,
        alias="finishedAt",
        serialization_alias="finishedAt",
    )
    execution_duration_ms: int | None = Field(
        default=None,
        alias="executionDurationMs",
        serialization_alias="executionDurationMs",
    )
    error_code: str | None = Field(
        default=None,
        alias="errorCode",
        serialization_alias="errorCode",
    )
    error_message: str | None = Field(
        default=None,
        alias="errorMessage",
        serialization_alias="errorMessage",
        description="Execution error message.",
        examples=[None],
    )
    stacktrace: str | None = Field(default=None)
    error: str | None = Field(
        default=None,
        description="Legacy execution error message.",
        examples=[None],
    )
