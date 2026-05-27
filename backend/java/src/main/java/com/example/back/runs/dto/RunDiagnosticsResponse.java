package com.example.back.runs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunDiagnosticsResponse(
        String diagnosticsStatus,
        String diagnosticsSummary,
        Double totalPnL,
        Double totalReturnPct,
        RunDiagnosticsRiskResponse risk,
        RunDiagnosticsTradesResponse trades,
        RunDiagnosticsStabilityResponse stability,
        List<RunDiagnosticsWarningResponse> warnings
) {
}
