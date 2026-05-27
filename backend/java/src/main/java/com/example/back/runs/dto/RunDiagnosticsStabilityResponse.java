package com.example.back.runs.dto;

import java.util.List;

public record RunDiagnosticsStabilityResponse(
        List<RunDiagnosticsStabilitySegmentResponse> segments,
        String status
) {
}
