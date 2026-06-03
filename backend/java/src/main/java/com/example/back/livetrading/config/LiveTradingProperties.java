package com.example.back.livetrading.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "live.trading")
public record LiveTradingProperties(
        boolean realOrderSubmissionEnabled,
        String credentialEncryptionKey,
        BigDecimal defaultMaxOrderNotional,
        BigDecimal defaultMaxPositionNotional,
        BigDecimal defaultMaxDailyNotional,
        BigDecimal defaultMaxDailyLossNotional,
        BigDecimal maxAllowedSlippagePercent,
        long maxMarketDataAgeSeconds,
        int maxFailedOrdersBeforeCircuitBreaker,
        int maxRejectedOrdersBeforeCircuitBreaker,
        Binance binance
) {

    public LiveTradingProperties {
        if (credentialEncryptionKey == null || credentialEncryptionKey.isBlank()) {
            credentialEncryptionKey = "change-me-live-credential-encryption-key";
        }
        if (defaultMaxOrderNotional == null) {
            defaultMaxOrderNotional = new BigDecimal("100.00000000");
        }
        if (defaultMaxPositionNotional == null) {
            defaultMaxPositionNotional = new BigDecimal("500.00000000");
        }
        if (defaultMaxDailyNotional == null) {
            defaultMaxDailyNotional = new BigDecimal("1000.00000000");
        }
        if (defaultMaxDailyLossNotional == null) {
            defaultMaxDailyLossNotional = new BigDecimal("250.00000000");
        }
        if (maxAllowedSlippagePercent == null) {
            maxAllowedSlippagePercent = new BigDecimal("2.00000000");
        }
        if (maxMarketDataAgeSeconds <= 0) {
            maxMarketDataAgeSeconds = 60;
        }
        if (maxFailedOrdersBeforeCircuitBreaker <= 0) {
            maxFailedOrdersBeforeCircuitBreaker = 3;
        }
        if (maxRejectedOrdersBeforeCircuitBreaker <= 0) {
            maxRejectedOrdersBeforeCircuitBreaker = 10;
        }
        if (binance == null) {
            binance = new Binance("https://api.binance.com", "https://testnet.binance.vision");
        }
    }

    public record Binance(
            String baseUrl,
            String testnetBaseUrl
    ) {
        public Binance {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.binance.com";
            }
            if (testnetBaseUrl == null || testnetBaseUrl.isBlank()) {
                testnetBaseUrl = "https://testnet.binance.vision";
            }
        }
    }
}
