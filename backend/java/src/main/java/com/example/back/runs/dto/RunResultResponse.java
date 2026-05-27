package com.example.back.runs.dto;

import com.example.back.backtest.dto.BacktestTrade;
import com.example.back.backtest.dto.EquityPoint;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public record RunResultResponse(
        Long runId,
        RunStatusResponse status,
        String engineVersion,
        Instant startedAt,
        Instant finishedAt,
        Long executionDurationMs,
        Map<String, Object> summary,
        Map<String, Object> metrics,
        Map<String, Object> artifacts,
        RunDiagnosticsResponse diagnostics,
        List<BacktestTrade> trades,
        List<EquityPoint> equityCurve,
        String errorMessage,
        Map<String, Object> errorDetails
) {
}
