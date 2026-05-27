package com.example.back.runs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunDiagnosticsStabilitySegmentResponse(
        Integer segmentIndex,
        @JsonProperty("from")
        String from,
        @JsonProperty("to")
        String to,
        Double pnl,
        Integer tradeCount,
        Double maxDrawdown,
        String status
) {
}
