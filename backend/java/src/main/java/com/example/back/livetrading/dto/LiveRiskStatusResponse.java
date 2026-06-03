package com.example.back.livetrading.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LiveRiskStatusResponse(
        boolean killSwitchActive,
        String killSwitchReason,
        Instant killSwitchActivatedAt,
        List<CircuitBreakerResponse> circuitBreakers,
        BigDecimal syncedPositionExposure,
        BigDecimal openOrderExposure,
        BigDecimal acceptedDailyNotional,
        BigDecimal realizedIntradayLoss,
        BigDecimal maxAllowedDailyLoss,
        BigDecimal maxAllowedSlippagePercent,
        long maxMarketDataAgeSeconds
) {
}
