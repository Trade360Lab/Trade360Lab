package com.example.back.livetrading.service;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangePriceSnapshot(
        BigDecimal price,
        Instant observedAt
) {
}
