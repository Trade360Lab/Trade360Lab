package com.example.back.livetrading.dto;

import java.util.List;

public record LiveOrderAuditResponse(
        LiveOrderResponse order,
        List<LiveAuditEventResponse> events
) {
}
