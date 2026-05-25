from parser.runs.dto.run_execute_dto import BacktestTradePayload, EquityPointPayload
from parser.runs.services.backtest_diagnostics import calculate_backtest_diagnostics


def trade(pnl: float, index: int) -> BacktestTradePayload:
    return BacktestTradePayload(
        entryTime=f"2024-01-{index:02d}T00:00:00Z",
        exitTime=f"2024-01-{index:02d}T01:00:00Z",
        entryPrice=100.0,
        exitPrice=100.0 + pnl,
        quantity=1.0,
        pnl=pnl,
        fee=0.0,
    )


def equity(timestamp: str, value: float) -> EquityPointPayload:
    return EquityPointPayload(timestamp=timestamp, equity=value)


def diagnostics(trades, equity_curve=None):
    return calculate_backtest_diagnostics(
        metrics={},
        summary={},
        trades=trades,
        equity_curve=equity_curve or [],
    )


def codes(result):
    return {warning.code for warning in result.warnings}


def test_diagnostics_handles_empty_trades_and_equity_curve():
    result = diagnostics([])

    assert result.diagnostics_status == "unavailable"
    assert result.trades.trade_count == 0
    assert result.trades.win_rate is None
    assert result.risk.max_drawdown is None
    assert result.stability.status == "no_trades"
    assert "NO_TRADES" in codes(result)


def test_diagnostics_handles_only_losses():
    result = diagnostics([trade(-10.0, 1), trade(-5.0, 2), trade(-2.5, 3)])

    assert result.diagnostics_status == "fragile"
    assert result.trades.trade_count == 3
    assert result.trades.winning_trades == 0
    assert result.trades.losing_trades == 3
    assert result.trades.longest_loss_streak == 3
    assert result.trades.profit_factor == 0.0
    assert "ALL_TRADES_LOSING" in codes(result)
    assert "NEGATIVE_EXPECTANCY" in codes(result)


def test_diagnostics_handles_only_wins():
    result = diagnostics([trade(4.0, 1), trade(6.0, 2), trade(8.0, 3)])

    assert result.trades.winning_trades == 3
    assert result.trades.losing_trades == 0
    assert result.trades.win_rate == 100.0
    assert result.trades.profit_factor is None
    assert result.trades.longest_win_streak == 3
    assert "PROFIT_FACTOR_UNAVAILABLE" in codes(result)


def test_diagnostics_calculates_max_drawdown_and_recovery():
    result = diagnostics(
        [trade(1.0, 1), trade(-1.0, 2), trade(4.0, 3)],
        equity_curve=[
            equity("2024-01-01T00:00:00Z", 100.0),
            equity("2024-01-02T00:00:00Z", 120.0),
            equity("2024-01-03T00:00:00Z", 90.0),
            equity("2024-01-04T00:00:00Z", 121.0),
        ],
    )

    assert result.risk.max_drawdown == 30.0
    assert result.risk.max_drawdown_pct == 25.0
    assert result.risk.drawdown_start == "2024-01-02T00:00:00Z"
    assert result.risk.drawdown_end == "2024-01-03T00:00:00Z"
    assert result.risk.recovery_bars == 1
    assert "HIGH_DRAWDOWN" in codes(result)


def test_diagnostics_builds_stability_segments_and_warnings():
    result = diagnostics(
        [
            trade(10.0, 1),
            trade(12.0, 2),
            trade(-20.0, 3),
            trade(-15.0, 4),
            trade(8.0, 5),
            trade(9.0, 6),
            trade(-3.0, 7),
            trade(4.0, 8),
        ]
    )

    assert len(result.stability.segments) == 4
    assert result.stability.status in {"mixed", "weak"}
    assert result.stability.segments[0].status == "strong"
    assert result.stability.segments[1].status == "weak"
    assert "UNSTABLE_SEGMENTS" in codes(result)


def test_diagnostics_low_trade_count_warning():
    result = diagnostics([trade(1.0, 1), trade(-0.5, 2)])

    assert result.trades.trade_count == 2
    assert "LOW_TRADE_COUNT" in codes(result)
