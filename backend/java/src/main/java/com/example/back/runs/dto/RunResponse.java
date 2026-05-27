package com.example.back.runs.dto;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;

@Builder
public record RunResponse(
        Long id,
        String runName,
        Long strategyId,
        Long strategyVersionId,
        Long parameterPresetId,
        String strategyName,
        String datasetId,
        String correlationId,
        RunStatusResponse status,
        String exchange,
        String symbol,
        String interval,
        Instant from,
        Instant to,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long executionDurationMs,
        String engineVersion,
        Map<String, Object> config,
        Map<String, Object> snapshot,
        Map<String, Object> parameters,
        Map<String, Object> summary,
        Map<String, Object> metrics,
        Map<String, Object> artifacts,
        RunDiagnosticsResponse diagnostics,
        String errorMessage,
        Map<String, Object> errorDetails
) {
}
