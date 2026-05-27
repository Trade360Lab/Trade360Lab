package com.example.back.runs.dto;

public record RunDiagnosticsRiskResponse(
        Double totalPnl,
        Double totalReturnPct,
        Double maxDrawdown,
        Double maxDrawdownPct,
        String drawdownStart,
        String drawdownEnd,
        Integer recoveryBars
) {
}
