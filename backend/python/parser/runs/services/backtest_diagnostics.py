from __future__ import annotations

from statistics import median
from typing import Any

from parser.runs.dto.run_execute_dto import (
    BacktestTradePayload,
    EquityPointPayload,
    RunDiagnosticsPayload,
    RunDiagnosticsRiskPayload,
    RunDiagnosticsStabilityPayload,
    RunDiagnosticsStabilitySegmentPayload,
    RunDiagnosticsTradesPayload,
    RunDiagnosticsWarningPayload,
)

LOW_TRADE_COUNT_THRESHOLD = 10
HIGH_DRAWDOWN_PCT_THRESHOLD = 20.0


def calculate_backtest_diagnostics(
    *,
    metrics: dict[str, Any] | None,
    summary: dict[str, Any] | None,
    trades: list[BacktestTradePayload],
    equity_curve: list[EquityPointPayload],
) -> RunDiagnosticsPayload:
    trade_pnls = [float(trade.pnl) for trade in trades]
    total_pnl = _number_from(metrics, summary, keys=("totalPnL", "total_pnl", "pnl", "profit"))
    if total_pnl is None:
        total_pnl = sum(trade_pnls) if trade_pnls else None

    total_return_pct = _number_from(
        metrics,
        summary,
        keys=("totalReturnPct", "total_return_pct", "total_return", "return"),
    )
    if total_return_pct is not None and abs(total_return_pct) <= 1:
        total_return_pct *= 100.0
    elif total_return_pct is None:
        total_return_pct = _total_return_pct_from_equity(equity_curve)

    risk = _calculate_risk(
        equity_curve=equity_curve,
        total_pnl=total_pnl,
        total_return_pct=total_return_pct,
    )
    trade_stats = _calculate_trade_stats(trade_pnls)
    stability = _calculate_stability_segments(trades)
    warnings = _build_warnings(risk=risk, trades=trade_stats, stability=stability)
    diagnostics_status = _diagnostics_status(warnings, trades=trade_stats, stability=stability)
    diagnostics_summary = _diagnostics_summary(
        status=diagnostics_status,
        warnings=warnings,
        risk=risk,
        trades=trade_stats,
        stability=stability,
    )

    return RunDiagnosticsPayload(
        diagnostics_status=diagnostics_status,
        diagnostics_summary=diagnostics_summary,
        risk=risk,
        trades=trade_stats,
        stability=stability,
        warnings=warnings,
    )


def _calculate_risk(
    *,
    equity_curve: list[EquityPointPayload],
    total_pnl: float | None,
    total_return_pct: float | None,
) -> RunDiagnosticsRiskPayload:
    drawdown = _max_drawdown(equity_curve)
    return RunDiagnosticsRiskPayload(
        total_pnl=_round(total_pnl),
        total_return_pct=_round(total_return_pct),
        max_drawdown=_round(drawdown["max_drawdown"]),
        max_drawdown_pct=_round(drawdown["max_drawdown_pct"]),
        drawdown_start=drawdown["drawdown_start"],
        drawdown_end=drawdown["drawdown_end"],
        recovery_bars=drawdown["recovery_bars"],
    )


def _max_drawdown(equity_curve: list[EquityPointPayload]) -> dict[str, Any]:
    if not equity_curve:
        return {
            "max_drawdown": None,
            "max_drawdown_pct": None,
            "drawdown_start": None,
            "drawdown_end": None,
            "recovery_bars": None,
        }

    peak_equity = float(equity_curve[0].equity)
    peak_time = equity_curve[0].timestamp
    max_drawdown = 0.0
    max_drawdown_pct: float | None = 0.0 if peak_equity != 0 else None
    drawdown_start = peak_time
    drawdown_end = peak_time
    trough_index = 0
    recovery_target: float | None = peak_equity

    for index, point in enumerate(equity_curve):
        equity = float(point.equity)
        if equity > peak_equity:
            peak_equity = equity
            peak_time = point.timestamp

        drawdown = peak_equity - equity
        drawdown_pct = (drawdown / peak_equity * 100.0) if peak_equity else None
        if drawdown > max_drawdown:
            max_drawdown = drawdown
            max_drawdown_pct = drawdown_pct
            drawdown_start = peak_time
            drawdown_end = point.timestamp
            trough_index = index
            recovery_target = peak_equity

    recovery_bars: int | None = 0
    if max_drawdown > 0 and recovery_target is not None:
        recovery_bars = None
        for offset, point in enumerate(equity_curve[trough_index + 1 :], start=1):
            if float(point.equity) >= recovery_target:
                recovery_bars = offset
                break

    return {
        "max_drawdown": max_drawdown,
        "max_drawdown_pct": max_drawdown_pct,
        "drawdown_start": drawdown_start,
        "drawdown_end": drawdown_end,
        "recovery_bars": recovery_bars,
    }


def _calculate_trade_stats(trade_pnls: list[float]) -> RunDiagnosticsTradesPayload:
    winners = [pnl for pnl in trade_pnls if pnl > 0]
    losers = [pnl for pnl in trade_pnls if pnl < 0]
    trade_count = len(trade_pnls)
    gross_profit = sum(winners)
    gross_loss = abs(sum(losers))
    profit_factor = gross_profit / gross_loss if gross_loss > 0 else None

    return RunDiagnosticsTradesPayload(
        trade_count=trade_count,
        winning_trades=len(winners),
        losing_trades=len(losers),
        win_rate=_round(len(winners) / trade_count * 100.0) if trade_count else None,
        profit_factor=_round(profit_factor),
        average_win=_round(sum(winners) / len(winners)) if winners else None,
        average_loss=_round(sum(losers) / len(losers)) if losers else None,
        best_trade=_round(max(trade_pnls)) if trade_pnls else None,
        worst_trade=_round(min(trade_pnls)) if trade_pnls else None,
        longest_win_streak=_longest_streak(trade_pnls, winning=True),
        longest_loss_streak=_longest_streak(trade_pnls, winning=False),
        average_trade_pnl=_round(sum(trade_pnls) / trade_count) if trade_count else None,
        median_trade_pnl=_round(float(median(trade_pnls))) if trade_pnls else None,
    )


def _calculate_stability_segments(
    trades: list[BacktestTradePayload],
    segment_count: int = 4,
) -> RunDiagnosticsStabilityPayload:
    if not trades:
        return RunDiagnosticsStabilityPayload(segments=[], status="no_trades")

    sorted_trades = sorted(trades, key=lambda trade: trade.entry_time or trade.exit_time or "")
    size = max(1, (len(sorted_trades) + segment_count - 1) // segment_count)
    segments: list[RunDiagnosticsStabilitySegmentPayload] = []

    for index in range(0, len(sorted_trades), size):
        chunk = sorted_trades[index : index + size]
        pnls = [float(trade.pnl) for trade in chunk]
        pnl = sum(pnls)
        max_drawdown = _max_drawdown_from_pnls(pnls)
        segments.append(
            RunDiagnosticsStabilitySegmentPayload(
                segment_index=len(segments) + 1,
                from_time=chunk[0].entry_time or chunk[0].exit_time,
                to_time=chunk[-1].exit_time or chunk[-1].entry_time,
                pnl=_round(pnl) or 0.0,
                trade_count=len(chunk),
                max_drawdown=_round(max_drawdown),
                status=_segment_status(pnl, len(chunk), max_drawdown),
            )
        )

    weak_count = sum(1 for segment in segments if segment.status == "weak")
    strong_count = sum(1 for segment in segments if segment.status == "strong")
    if weak_count == 0 and strong_count == len(segments):
        status = "strong"
    elif weak_count > 0 or strong_count == 0:
        status = "weak"
    else:
        status = "mixed"

    return RunDiagnosticsStabilityPayload(segments=segments, status=status)


def _build_warnings(
    *,
    risk: RunDiagnosticsRiskPayload,
    trades: RunDiagnosticsTradesPayload,
    stability: RunDiagnosticsStabilityPayload,
) -> list[RunDiagnosticsWarningPayload]:
    warnings: list[RunDiagnosticsWarningPayload] = []
    if trades.trade_count == 0:
        warnings.append(_warning("NO_TRADES", "high", "Backtest produced no closed trades."))
    elif trades.trade_count < LOW_TRADE_COUNT_THRESHOLD:
        warnings.append(
            _warning(
                "LOW_TRADE_COUNT",
                "medium",
                "Trade sample is too small for stability analysis.",
            )
        )

    if risk.max_drawdown_pct is not None and risk.max_drawdown_pct >= HIGH_DRAWDOWN_PCT_THRESHOLD:
        warnings.append(
            _warning("HIGH_DRAWDOWN", "high", "Maximum drawdown is high for this backtest sample.")
        )

    if trades.average_trade_pnl is not None and trades.average_trade_pnl < 0:
        warnings.append(_warning("NEGATIVE_EXPECTANCY", "high", "Average trade PnL is negative."))

    if stability.status in {"weak", "mixed"} and trades.trade_count > 0:
        warnings.append(
            _warning("UNSTABLE_SEGMENTS", "medium", "Stability segments are mixed or weak.")
        )

    if trades.trade_count > 0 and trades.winning_trades == 0:
        warnings.append(
            _warning("ALL_TRADES_LOSING", "high", "All closed trades are losing trades.")
        )

    if trades.trade_count > 0 and trades.profit_factor is None:
        warnings.append(
            _warning(
                "PROFIT_FACTOR_UNAVAILABLE",
                "low",
                "Profit factor is unavailable without losing trades.",
            )
        )

    return warnings


def _diagnostics_status(
    warnings: list[RunDiagnosticsWarningPayload],
    trades: RunDiagnosticsTradesPayload,
    stability: RunDiagnosticsStabilityPayload,
) -> str:
    if trades.trade_count == 0:
        return "unavailable"
    high_count = sum(1 for warning in warnings if warning.severity == "high")
    if high_count > 0:
        return "fragile"
    if warnings or stability.status != "strong":
        return "mixed"
    return "healthy"


def _diagnostics_summary(
    *,
    status: str,
    warnings: list[RunDiagnosticsWarningPayload],
    risk: RunDiagnosticsRiskPayload,
    trades: RunDiagnosticsTradesPayload,
    stability: RunDiagnosticsStabilityPayload,
) -> str:
    warning_codes = {warning.code for warning in warnings}
    if "NO_TRADES" in warning_codes:
        return "Backtest produced no closed trades, so strategy diagnostics are unavailable."
    if "LOW_TRADE_COUNT" in warning_codes:
        return "Backtest produced too few trades to evaluate stability."
    if "ALL_TRADES_LOSING" in warning_codes:
        return "Strategy result is fragile because all closed trades are losing trades."
    if "NEGATIVE_EXPECTANCY" in warning_codes:
        return "Strategy result is fragile because average trade expectancy is negative."
    if status == "healthy":
        return "Strategy result is profitable with stable segment behavior in this backtest sample."
    if stability.status == "mixed" and risk.total_pnl is not None and risk.total_pnl > 0:
        return (
            "Strategy result is profitable but mixed because gains are uneven across "
            "stability segments."
        )
    if trades.trade_count > 0:
        return (
            "Strategy diagnostics are available, but warnings should be reviewed before "
            "further validation."
        )
    return "Backtest diagnostics are unavailable for this run."


def _number_from(
    metrics: dict[str, Any] | None,
    summary: dict[str, Any] | None,
    *,
    keys: tuple[str, ...],
) -> float | None:
    for payload in (metrics, summary):
        if not payload:
            continue
        for key in keys:
            value = payload.get(key)
            if isinstance(value, int | float) and not isinstance(value, bool):
                return float(value)
            if isinstance(value, str) and value.strip():
                try:
                    return float(value)
                except ValueError:
                    continue
    return None


def _total_return_pct_from_equity(equity_curve: list[EquityPointPayload]) -> float | None:
    if len(equity_curve) < 2:
        return None
    start = float(equity_curve[0].equity)
    end = float(equity_curve[-1].equity)
    if start == 0:
        return None
    return _round((end - start) / start * 100.0)


def _max_drawdown_from_pnls(pnls: list[float]) -> float:
    equity = 0.0
    peak = 0.0
    max_drawdown = 0.0
    for pnl in pnls:
        equity += pnl
        peak = max(peak, equity)
        max_drawdown = max(max_drawdown, peak - equity)
    return max_drawdown


def _segment_status(pnl: float, trade_count: int, max_drawdown: float) -> str:
    if trade_count == 0:
        return "no_trades"
    if pnl > 0 and max_drawdown <= abs(pnl):
        return "strong"
    if pnl < 0:
        return "weak"
    return "mixed"


def _longest_streak(values: list[float], *, winning: bool) -> int:
    best = 0
    current = 0
    for value in values:
        matched = value > 0 if winning else value < 0
        if matched:
            current += 1
            best = max(best, current)
        else:
            current = 0
    return best


def _warning(code: str, severity: str, message: str) -> RunDiagnosticsWarningPayload:
    return RunDiagnosticsWarningPayload(code=code, severity=severity, message=message)


def _round(value: float | None) -> float | None:
    if value is None:
        return None
    return round(value, 4)
