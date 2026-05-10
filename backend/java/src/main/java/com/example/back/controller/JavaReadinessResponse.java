package com.example.back.controller;

public record JavaReadinessResponse(
        String status,
        String service,
        String apiVersion,
        String database,
        String migrationStatus,
        String liveTradingMode,
        boolean realOrderSubmissionEnabled,
        String safety
) {
}
