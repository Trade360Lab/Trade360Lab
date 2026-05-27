package com.example.back.runs.dto;

import java.util.List;

public record RunDiagnosticsResponse(
        String diagnosticsStatus,
        String diagnosticsSummary,
        RunDiagnosticsRiskResponse risk,
        RunDiagnosticsTradesResponse trades,
        RunDiagnosticsStabilityResponse stability,
        List<RunDiagnosticsWarningResponse> warnings
) {
}
