package com.example.back.runs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunDiagnosticsRiskResponse(
        Double maxDrawdown,
        Double maxDrawdownPct,
        String drawdownStart,
        String drawdownEnd,
        Integer recoveryBars
) {
}
