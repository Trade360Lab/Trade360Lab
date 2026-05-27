package com.example.back.runs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunDiagnosticsWarningResponse(
        String code,
        String message,
        String severity
) {
}
