package com.example.back.runs.dto;

public record RunDiagnosticsTradesResponse(
        Integer tradeCount,
        Integer winningTrades,
        Integer losingTrades,
        Double winRate,
        Double profitFactor,
        Double averageWin,
        Double averageLoss,
        Double bestTrade,
        Double worstTrade,
        Integer longestWinStreak,
        Integer longestLossStreak,
        Double averageTradePnl,
        Double medianTradePnl
) {
}
