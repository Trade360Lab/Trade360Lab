package com.example.back.livetrading.dto;

import com.example.back.livetrading.entity.LiveOrderStatus;
import com.example.back.livetrading.entity.LiveRiskEventType;
import java.time.Instant;

public record LiveAuditEventResponse(
        Long eventId,
        Long orderId,
        Long strategyId,
        String exchange,
        String symbol,
        LiveRiskEventType eventType,
        LiveOrderStatus orderStatus,
        String reason,
        Instant createdAt
) {
}
