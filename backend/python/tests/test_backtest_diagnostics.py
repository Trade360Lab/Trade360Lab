from parser.services.backtest_diagnostics import (
    calculate_backtest_diagnostics,
    calculate_drawdown,
)


def warning_codes(diagnostics):
    return {warning["code"] for warning in diagnostics["warnings"]}


def test_diagnostics_handles_empty_trades_and_equity():
    diagnostics = calculate_backtest_diagnostics(trades=[], equity_curve=[])

    assert diagnostics["diagnosticsStatus"] == "unavailable"
    assert diagnostics["trades"]["tradeCount"] == 0
    assert diagnostics["risk"]["maxDrawdown"] is None
    assert {"NO_TRADES", "LOW_TRADE_COUNT", "PROFIT_FACTOR_UNAVAILABLE"} <= warning_codes(
        diagnostics
    )


def test_diagnostics_handles_losing_only_trades():
    diagnostics = calculate_backtest_diagnostics(
        trades=[
            {"entry_time": "2024-01-01T00:00:00Z", "exit_time": "2024-01-01T01:00:00Z", "pnl": -4},
            {"entry_time": "2024-01-02T00:00:00Z", "exit_time": "2024-01-02T01:00:00Z", "pnl": -6},
        ],
        equity_curve=[
            {"timestamp": "2024-01-01T00:00:00Z", "equity": 100},
            {"timestamp": "2024-01-02T00:00:00Z", "equity": 90},
        ],
    )

    assert diagnostics["diagnosticsStatus"] == "fragile"
    assert diagnostics["trades"]["losingTrades"] == 2
    assert diagnostics["trades"]["profitFactor"] is None
    assert {"ALL_TRADES_LOSING", "NEGATIVE_EXPECTANCY"} <= warning_codes(diagnostics)


def test_diagnostics_handles_winning_only_trades():
    diagnostics = calculate_backtest_diagnostics(
        trades=[
            {"entry_time": "2024-01-01T00:00:00Z", "exit_time": "2024-01-01T01:00:00Z", "pnl": 5},
            {"entry_time": "2024-01-02T00:00:00Z", "exit_time": "2024-01-02T01:00:00Z", "pnl": 7},
        ],
        equity_curve=[
            {"timestamp": "2024-01-01T00:00:00Z", "equity": 100},
            {"timestamp": "2024-01-02T00:00:00Z", "equity": 112},
        ],
    )

    assert diagnostics["trades"]["winningTrades"] == 2
    assert diagnostics["trades"]["profitFactor"] is None
    assert "PROFIT_FACTOR_UNAVAILABLE" in warning_codes(diagnostics)


def test_drawdown_calculation_tracks_peak_trough_and_recovery():
    drawdown = calculate_drawdown(
        [
            {"timestamp": "2024-01-01T00:00:00Z", "equity": 100},
            {"timestamp": "2024-01-02T00:00:00Z", "equity": 120},
            {"timestamp": "2024-01-03T00:00:00Z", "equity": 90},
            {"timestamp": "2024-01-04T00:00:00Z", "equity": 105},
            {"timestamp": "2024-01-05T00:00:00Z", "equity": 121},
        ]
    )

    assert drawdown.amount == 30
    assert drawdown.pct == 25
    assert drawdown.start == "2024-01-02T00:00:00Z"
    assert drawdown.end == "2024-01-03T00:00:00Z"
    assert drawdown.recovery_bars == 2


def test_stability_segmentation_emits_segment_statuses():
    diagnostics = calculate_backtest_diagnostics(
        trades=[
            {"exit_time": "2024-01-01T00:00:00Z", "pnl": 4},
            {"exit_time": "2024-01-02T00:00:00Z", "pnl": 3},
            {"exit_time": "2024-01-03T00:00:00Z", "pnl": -8},
            {"exit_time": "2024-01-04T00:00:00Z", "pnl": 2},
        ],
        equity_curve=[
            {"timestamp": "2024-01-01T00:00:00Z", "equity": 100},
            {"timestamp": "2024-01-02T00:00:00Z", "equity": 107},
            {"timestamp": "2024-01-03T00:00:00Z", "equity": 99},
            {"timestamp": "2024-01-04T00:00:00Z", "equity": 101},
        ],
    )

    assert len(diagnostics["stability"]["segments"]) == 4
    assert diagnostics["stability"]["status"] == "weak"
    assert "UNSTABLE_SEGMENTS" in warning_codes(diagnostics)


def test_warning_generation_marks_high_drawdown_low_sample():
    diagnostics = calculate_backtest_diagnostics(
        trades=[{"exit_time": "2024-01-01T00:00:00Z", "pnl": 5}],
        equity_curve=[
            {"timestamp": "2024-01-01T00:00:00Z", "equity": 100},
            {"timestamp": "2024-01-02T00:00:00Z", "equity": 70},
        ],
    )

    assert {"LOW_TRADE_COUNT", "HIGH_DRAWDOWN"} <= warning_codes(diagnostics)
