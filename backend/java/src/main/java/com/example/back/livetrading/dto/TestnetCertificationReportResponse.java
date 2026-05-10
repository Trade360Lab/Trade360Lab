package com.example.back.livetrading.dto;

import java.time.Instant;

public record TestnetCertificationReportResponse(
        Long id,
        String exchange,
        String environment,
        Instant startedAt,
        Instant finishedAt,
        String connectivityStatus,
        String accountSnapshotStatus,
        String openOrdersStatus,
        String reconciliationStatus,
        String riskChecksStatus,
        String finalResult,
        boolean realOrderSubmissionEnabled,
        String message
) {
}
