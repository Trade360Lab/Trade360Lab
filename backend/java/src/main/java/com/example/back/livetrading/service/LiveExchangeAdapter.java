package com.example.back.livetrading.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LiveExchangeAdapter {

    String exchange();

    Optional<BigDecimal> getLatestPrice(String symbol);

    default Optional<ExchangePriceSnapshot> getLatestPriceSnapshot(String symbol) {
        return getLatestPrice(symbol).map(price -> new ExchangePriceSnapshot(price, Instant.now()));
    }

    LiveOrderResult placeOrder(LiveOrderRequest orderRequest, ExchangeCredentials credentials);

    void cancelOrder(String orderId, String symbol, ExchangeCredentials credentials);

    LiveOrderResult getOrder(String orderId, String symbol, ExchangeCredentials credentials);

    List<LiveOrderResult> getOpenOrders(String symbol, ExchangeCredentials credentials);

    List<ExchangePositionSnapshot> getPositions(ExchangeCredentials credentials);

    List<ExchangeBalanceSnapshot> getBalances(ExchangeCredentials credentials);

    boolean pingConnection();

    boolean validateCredentials(ExchangeCredentials credentials);
}
