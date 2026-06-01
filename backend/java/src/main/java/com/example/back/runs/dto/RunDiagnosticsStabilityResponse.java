package com.example.back.runs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunDiagnosticsStabilityResponse(
        List<RunDiagnosticsStabilitySegmentResponse> segments,
        String status
) {
}
