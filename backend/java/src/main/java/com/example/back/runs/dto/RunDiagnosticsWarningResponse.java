package com.example.back.runs.dto;

public record RunDiagnosticsWarningResponse(
        String code,
        String severity,
        String message
) {
}
