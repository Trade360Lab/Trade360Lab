from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from statistics import median
from typing import Any

LOW_TRADE_COUNT_THRESHOLD = 10
HIGH_DRAWDOWN_PCT_THRESHOLD = 20.0
SEGMENT_COUNT = 4
SAFETY_NOTE = (
    "This report is for alpha research and validation only. It is not financial advice "
    "and does not certify production trading readiness."
)


@dataclass(frozen=True, slots=True)
class DrawdownResult:
    amount: float | None
    pct: float | None
    start: str | None
    end: str | None
    recovery_bars: int | None


def calculate_backtest_diagnostics(
    *,
    summary: dict[str, Any] | None = None,
    metrics: dict[str, Any] | None = None,
    trades: list[dict[str, Any]] | None = None,
    equity_curve: list[dict[str, Any]] | None = None,
    starting_equity: float | None = None,
) -> dict[str, Any]:
    safe_summary = summary or {}
    safe_metrics = metrics or {}
    safe_trades = trades or []
    safe_equity_curve = equity_curve or []

    trade_pnls = [_to_float(trade.get("pnl")) for trade in safe_trades if isinstance(trade, dict)]
    trade_pnls = [value for value in trade_pnls if value is not None]
    wins = [value for value in trade_pnls if value > 0]
    losses = [value for value in trade_pnls if value < 0]
    flat_trades = [value for value in trade_pnls if value == 0]

    trade_count = len(trade_pnls)
    total_pnl = _resolve_total_pnl(
        safe_summary,
        safe_metrics,
        trade_pnls,
        safe_equity_curve,
        starting_equity,
    )
    total_return_pct = _resolve_total_return_pct(
        safe_summary,
        safe_metrics,
        total_pnl,
        safe_equity_curve,
        starting_equity,
    )
    drawdown = calculate_drawdown(safe_equity_curve, starting_equity=starting_equity)
    profit_factor = _profit_factor(wins, losses)

    warnings = _build_warnings(
        trade_count=trade_count,
        wins=wins,
        losses=losses,
        profit_factor=profit_factor,
        average_trade_pnl=_average(trade_pnls),
        max_drawdown_pct=drawdown.pct,
    )
    stability = _calculate_stability_segments(
        safe_trades,
        safe_equity_curve,
        starting_equity=starting_equity,
    )
    if stability["status"] == "weak":
        _add_warning(
            warnings,
            "UNSTABLE_SEGMENTS",
            "Backtest performance is concentrated in weak or uneven stability segments.",
            "warning",
        )

    diagnostics_status = _diagnostics_status(warnings, stability["status"], trade_count)

    return {
        "diagnosticsStatus": diagnostics_status,
        "diagnosticsSummary": _summary_text(
            diagnostics_status=diagnostics_status,
            total_pnl=total_pnl,
            trade_count=trade_count,
            warnings=warnings,
            stability_status=stability["status"],
        ),
        "totalPnL": _round_or_none(total_pnl),
        "totalReturnPct": _round_or_none(total_return_pct),
        "risk": {
            "maxDrawdown": _round_or_none(drawdown.amount),
            "maxDrawdownPct": _round_or_none(drawdown.pct),
            "drawdownStart": drawdown.start,
            "drawdownEnd": drawdown.end,
            "recoveryBars": drawdown.recovery_bars,
        },
        "trades": {
            "tradeCount": trade_count,
            "winningTrades": len(wins),
            "losingTrades": len(losses),
            "winRate": _round_or_none((len(wins) / trade_count) * 100 if trade_count else None),
            "profitFactor": _round_or_none(profit_factor),
            "averageWin": _round_or_none(_average(wins)),
            "averageLoss": _round_or_none(_average(losses)),
            "bestTrade": _round_or_none(max(trade_pnls) if trade_pnls else None),
            "worstTrade": _round_or_none(min(trade_pnls) if trade_pnls else None),
            "longestWinStreak": _longest_streak(trade_pnls, lambda value: value > 0),
            "longestLossStreak": _longest_streak(trade_pnls, lambda value: value < 0),
            "averageTradePnl": _round_or_none(_average(trade_pnls)),
            "medianTradePnl": _round_or_none(median(trade_pnls) if trade_pnls else None),
            "flatTrades": len(flat_trades),
        },
        "stability": stability,
        "warnings": warnings,
    }


def calculate_drawdown(
    equity_curve: list[dict[str, Any]],
    *,
    starting_equity: float | None = None,
) -> DrawdownResult:
    points = _equity_points(equity_curve)
    if not points:
        return DrawdownResult(None, None, None, None, None)

    first_timestamp, first_equity = points[0]
    initial_peak = starting_equity if starting_equity is not None else first_equity
    peak = initial_peak
    peak_timestamp = first_timestamp
    max_drawdown = 0.0
    max_drawdown_pct = 0.0
    drawdown_start: str | None = None
    drawdown_end: str | None = None
    recovery_peak = initial_peak
    recovery_end_index: int | None = None

    for index, (timestamp, equity) in enumerate(points):
        if equity >= peak:
            peak = equity
            peak_timestamp = timestamp

        drawdown = peak - equity
        drawdown_pct = (drawdown / peak) * 100 if peak else 0.0
        if drawdown > max_drawdown:
            max_drawdown = drawdown
            max_drawdown_pct = drawdown_pct
            drawdown_start = peak_timestamp
            drawdown_end = timestamp
            recovery_peak = peak
            recovery_end_index = index

    recovery_bars = None
    if max_drawdown > 0 and recovery_end_index is not None:
        for recovered_index, (_, equity) in enumerate(
            points[recovery_end_index + 1 :],
            start=recovery_end_index + 1,
        ):
            if equity >= recovery_peak:
                recovery_bars = recovered_index - recovery_end_index
                break

    return DrawdownResult(
        amount=max_drawdown,
        pct=max_drawdown_pct,
        start=drawdown_start,
        end=drawdown_end,
        recovery_bars=recovery_bars,
    )


def _calculate_stability_segments(
    trades: list[dict[str, Any]],
    equity_curve: list[dict[str, Any]],
    *,
    starting_equity: float | None,
) -> dict[str, Any]:
    equity_points = _equity_points(equity_curve)
    if equity_points:
        segments = _equity_segments(equity_points, trades, starting_equity=starting_equity)
    else:
        segments = _trade_segments(trades)

    statuses = [segment["status"] for segment in segments]
    if not segments or all(status == "no_trades" for status in statuses):
        status = "no_trades"
    elif any(status == "weak" for status in statuses):
        status = "weak"
    elif all(status == "strong" for status in statuses):
        status = "strong"
    else:
        status = "mixed"

    return {
        "segments": segments,
        "status": status,
    }


def _equity_segments(
    equity_points: list[tuple[str, float]],
    trades: list[dict[str, Any]],
    *,
    starting_equity: float | None,
) -> list[dict[str, Any]]:
    segment_count = min(SEGMENT_COUNT, len(equity_points))
    segments: list[dict[str, Any]] = []
    previous_end_equity = starting_equity
    for index in range(segment_count):
        start_index = (len(equity_points) * index) // segment_count
        end_index = (len(equity_points) * (index + 1)) // segment_count - 1
        segment_points = equity_points[start_index : end_index + 1]
        if not segment_points:
            continue

        from_timestamp = segment_points[0][0]
        to_timestamp = segment_points[-1][0]
        start_equity = (
            previous_end_equity if previous_end_equity is not None else segment_points[0][1]
        )
        end_equity = segment_points[-1][1]
        previous_end_equity = end_equity
        segment_curve = [
            {"timestamp": timestamp, "equity": equity} for timestamp, equity in segment_points
        ]
        drawdown = calculate_drawdown(segment_curve, starting_equity=start_equity)
        segment_trades = _trades_in_period(trades, from_timestamp, to_timestamp)
        pnl = end_equity - start_equity
        trade_count = len(segment_trades)
        segments.append(
            {
                "segmentIndex": index + 1,
                "from": from_timestamp,
                "to": to_timestamp,
                "pnl": _round_or_none(pnl),
                "tradeCount": trade_count,
                "maxDrawdown": _round_or_none(drawdown.amount),
                "status": _segment_status(pnl, trade_count, drawdown.pct),
            }
        )
    return segments


def _trade_segments(trades: list[dict[str, Any]]) -> list[dict[str, Any]]:
    pnls = [
        (trade, _to_float(trade.get("pnl")))
        for trade in trades
        if isinstance(trade, dict) and _to_float(trade.get("pnl")) is not None
    ]
    if not pnls:
        return [
            {
                "segmentIndex": 1,
                "from": None,
                "to": None,
                "pnl": 0.0,
                "tradeCount": 0,
                "maxDrawdown": None,
                "status": "no_trades",
            }
        ]

    segment_count = min(SEGMENT_COUNT, len(pnls))
    segments: list[dict[str, Any]] = []
    for index in range(segment_count):
        start_index = (len(pnls) * index) // segment_count
        end_index = (len(pnls) * (index + 1)) // segment_count
        segment = pnls[start_index:end_index]
        segment_pnls = [pnl for _, pnl in segment if pnl is not None]
        pnl = sum(segment_pnls)
        segments.append(
            {
                "segmentIndex": index + 1,
                "from": _trade_time(segment[0][0]) if segment else None,
                "to": _trade_time(segment[-1][0]) if segment else None,
                "pnl": _round_or_none(pnl),
                "tradeCount": len(segment_pnls),
                "maxDrawdown": None,
                "status": _segment_status(pnl, len(segment_pnls), None),
            }
        )
    return segments


def _build_warnings(
    *,
    trade_count: int,
    wins: list[float],
    losses: list[float],
    profit_factor: float | None,
    average_trade_pnl: float | None,
    max_drawdown_pct: float | None,
) -> list[dict[str, str]]:
    warnings: list[dict[str, str]] = []
    if trade_count == 0:
        _add_warning(
            warnings,
            "NO_TRADES",
            "Backtest produced no closed trades.",
            "warning",
        )
    if trade_count < LOW_TRADE_COUNT_THRESHOLD:
        _add_warning(
            warnings,
            "LOW_TRADE_COUNT",
            "Backtest produced too few trades to evaluate stability.",
            "warning",
        )
    if max_drawdown_pct is not None and max_drawdown_pct >= HIGH_DRAWDOWN_PCT_THRESHOLD:
        _add_warning(
            warnings,
            "HIGH_DRAWDOWN",
            "Maximum drawdown is high for an alpha research backtest.",
            "warning",
        )
    if average_trade_pnl is not None and average_trade_pnl < 0:
        _add_warning(
            warnings,
            "NEGATIVE_EXPECTANCY",
            "Average closed-trade PnL is negative.",
            "warning",
        )
    if trade_count > 0 and losses and not wins:
        _add_warning(
            warnings,
            "ALL_TRADES_LOSING",
            "Every closed trade in the sample is losing.",
            "warning",
        )
    if profit_factor is None:
        _add_warning(
            warnings,
            "PROFIT_FACTOR_UNAVAILABLE",
            "Profit factor is unavailable without both winning and losing trades.",
            "info",
        )
    return warnings


def _diagnostics_status(
    warnings: list[dict[str, str]],
    stability_status: str,
    trade_count: int,
) -> str:
    warning_codes = {warning["code"] for warning in warnings}
    if trade_count == 0:
        return "unavailable"
    if {
        "HIGH_DRAWDOWN",
        "NEGATIVE_EXPECTANCY",
        "ALL_TRADES_LOSING",
        "UNSTABLE_SEGMENTS",
    } & warning_codes:
        return "fragile"
    if stability_status in {"weak", "no_trades"}:
        return "fragile"
    if warning_codes or stability_status == "mixed":
        return "mixed"
    return "healthy"


def _summary_text(
    *,
    diagnostics_status: str,
    total_pnl: float | None,
    trade_count: int,
    warnings: list[dict[str, str]],
    stability_status: str,
) -> str:
    codes = {warning["code"] for warning in warnings}
    if trade_count == 0:
        return "Backtest produced too few trades to evaluate stability."
    if "ALL_TRADES_LOSING" in codes:
        return "Backtest result is fragile because every closed trade in the sample is losing."
    if "HIGH_DRAWDOWN" in codes:
        return "Strategy result is fragile because drawdown is high for this alpha research sample."
    if "NEGATIVE_EXPECTANCY" in codes:
        return "Strategy result is fragile because the average closed-trade PnL is negative."
    if stability_status == "weak":
        return "Strategy result is fragile because gains are uneven across stability segments."
    if diagnostics_status == "healthy":
        return "Strategy result has a balanced alpha research diagnostics profile."
    if total_pnl is not None and total_pnl > 0:
        return "Strategy result is profitable but mixed because diagnostics warnings remain."
    return "Strategy result requires review because diagnostics are mixed."


def _resolve_total_pnl(
    summary: dict[str, Any],
    metrics: dict[str, Any],
    trade_pnls: list[float],
    equity_curve: list[dict[str, Any]],
    starting_equity: float | None,
) -> float | None:
    for key in ("totalPnL", "total_pnl", "netProfit", "net_profit", "profit", "pnl"):
        value = _to_float(summary.get(key, metrics.get(key)))
        if value is not None:
            return value

    points = _equity_points(equity_curve)
    if points:
        start = starting_equity if starting_equity is not None else points[0][1]
        return points[-1][1] - start

    if trade_pnls:
        return sum(trade_pnls)
    return None


def _resolve_total_return_pct(
    summary: dict[str, Any],
    metrics: dict[str, Any],
    total_pnl: float | None,
    equity_curve: list[dict[str, Any]],
    starting_equity: float | None,
) -> float | None:
    for key in ("totalReturnPct", "total_return_pct", "totalReturn", "total_return", "return"):
        value = _to_float(summary.get(key, metrics.get(key)))
        if value is not None:
            return value * 100 if abs(value) <= 1 else value

    points = _equity_points(equity_curve)
    start = starting_equity
    if start is None and points:
        start = points[0][1]
    if total_pnl is None or not start:
        return None
    return (total_pnl / start) * 100


def _profit_factor(wins: list[float], losses: list[float]) -> float | None:
    if not wins or not losses:
        return None
    gross_profit = sum(wins)
    gross_loss = abs(sum(losses))
    if gross_loss == 0:
        return None
    return gross_profit / gross_loss


def _longest_streak(values: list[float], predicate: Any) -> int:
    longest = 0
    current = 0
    for value in values:
        if predicate(value):
            current += 1
            longest = max(longest, current)
        else:
            current = 0
    return longest


def _segment_status(pnl: float | None, trade_count: int, drawdown_pct: float | None) -> str:
    if trade_count == 0:
        return "no_trades"
    if pnl is not None and pnl < 0:
        return "weak"
    if drawdown_pct is not None and drawdown_pct >= HIGH_DRAWDOWN_PCT_THRESHOLD:
        return "weak"
    if pnl is not None and pnl > 0 and (drawdown_pct is None or drawdown_pct < 10.0):
        return "strong"
    return "mixed"


def _trades_in_period(
    trades: list[dict[str, Any]],
    from_timestamp: str,
    to_timestamp: str,
) -> list[dict[str, Any]]:
    from_dt = _parse_datetime(from_timestamp)
    to_dt = _parse_datetime(to_timestamp)
    if from_dt is None or to_dt is None:
        return []

    selected = []
    for trade in trades:
        timestamp = _parse_datetime(_trade_time(trade))
        if timestamp is not None and from_dt <= timestamp <= to_dt:
            selected.append(trade)
    return selected


def _trade_time(trade: dict[str, Any]) -> str | None:
    value = trade.get("exit_time", trade.get("exitTime"))
    if value is None:
        value = trade.get("entry_time", trade.get("entryTime"))
    return str(value) if value is not None else None


def _equity_points(equity_curve: list[dict[str, Any]]) -> list[tuple[str, float]]:
    points = []
    for index, item in enumerate(equity_curve):
        if not isinstance(item, dict):
            continue
        equity = _to_float(item.get("equity"))
        if equity is None:
            continue
        timestamp = item.get("timestamp")
        points.append((str(timestamp) if timestamp is not None else str(index), equity))
    return points


def _parse_datetime(value: str | None) -> datetime | None:
    if value is None:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def _average(values: list[float]) -> float | None:
    if not values:
        return None
    return sum(values) / len(values)


def _to_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _round_or_none(value: float | None, digits: int = 4) -> float | None:
    if value is None:
        return None
    return round(value, digits)


def _add_warning(
    warnings: list[dict[str, str]],
    code: str,
    message: str,
    severity: str,
) -> None:
    if any(warning["code"] == code for warning in warnings):
        return
    warnings.append({"code": code, "message": message, "severity": severity})
