package com.example.back.livetrading.service;

import com.example.back.livetrading.dto.CircuitBreakerResponse;
import com.example.back.livetrading.dto.LiveBalanceResponse;
import com.example.back.livetrading.dto.LiveAuditEventResponse;
import com.example.back.livetrading.dto.LiveCredentialStatusResponse;
import com.example.back.livetrading.dto.LiveOrderResponse;
import com.example.back.livetrading.dto.LivePositionResponse;
import com.example.back.livetrading.dto.LiveRiskEventResponse;
import com.example.back.livetrading.dto.LiveSessionResponse;
import com.example.back.livetrading.dto.TestnetCertificationReportResponse;
import com.example.back.livetrading.entity.CircuitBreakerStateEntity;
import com.example.back.livetrading.entity.LiveExchangeCredentialEntity;
import com.example.back.livetrading.entity.LiveOrderEntity;
import com.example.back.livetrading.entity.LivePositionEntity;
import com.example.back.livetrading.entity.LiveRiskEventEntity;
import com.example.back.livetrading.entity.LiveTradingSessionEntity;
import com.example.back.livetrading.entity.TestnetCertificationReportEntity;
import org.springframework.stereotype.Component;

@Component
public class LiveTradingMapper {

    public LiveCredentialStatusResponse toCredentialResponse(LiveExchangeCredentialEntity credential) {
        return new LiveCredentialStatusResponse(
                credential.getId(),
                credential.getExchange(),
                credential.getKeyReference(),
                credential.isActive(),
                credential.getCreatedAt(),
                credential.getUpdatedAt()
        );
    }

    public LiveSessionResponse toSessionResponse(LiveTradingSessionEntity session) {
        return new LiveSessionResponse(
                session.getId(),
                session.getUserId(),
                session.getName(),
                session.getExchange(),
                session.getSymbol(),
                session.getBaseCurrency(),
                session.getQuoteCurrency(),
                session.getStatus(),
                session.getMaxOrderNotional(),
                session.getMaxPositionNotional(),
                session.getMaxDailyNotional(),
                session.getSymbolWhitelist(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    public LiveOrderResponse toOrderResponse(LiveOrderEntity order) {
        return new LiveOrderResponse(
                order.getId(),
                order.getUserId(),
                order.getSessionId(),
                order.getStrategyId(),
                order.getStrategyVersionId(),
                order.getExchange(),
                order.getSymbol(),
                order.getSide(),
                order.getType(),
                order.getQuantity(),
                order.getRequestedPrice(),
                order.getExecutedPrice(),
                order.getStatus(),
                order.getExchangeOrderId(),
                order.getSubmittedAt(),
                order.getUpdatedAt(),
                order.getFilledAt(),
                order.getRejectedReason(),
                order.getSourceRunId()
        );
    }

    public LivePositionResponse toPositionResponse(LivePositionEntity position) {
        return new LivePositionResponse(
                position.getId(),
                position.getUserId(),
                position.getExchange(),
                position.getSymbol(),
                position.getQuantity(),
                position.getAverageEntryPrice(),
                position.getRealizedPnl(),
                position.getUnrealizedPnl(),
                position.getUpdatedAt(),
                position.getSyncStatus()
        );
    }

    public LiveBalanceResponse toBalanceResponse(ExchangeBalanceSnapshot balance) {
        return new LiveBalanceResponse(balance.asset(), balance.free(), balance.locked());
    }

    public CircuitBreakerResponse toCircuitBreakerResponse(CircuitBreakerStateEntity state) {
        return new CircuitBreakerResponse(
                state.getExchange(),
                state.isActive(),
                state.getReason(),
                state.getTriggeredAt(),
                state.getUpdatedAt()
        );
    }

    public LiveRiskEventResponse toRiskEventResponse(LiveRiskEventEntity event) {
        return new LiveRiskEventResponse(
                event.getId(),
                event.getOrderId(),
                event.getStrategyId(),
                event.getExchange(),
                event.getSymbol(),
                event.getEventType(),
                event.getReason(),
                event.getCreatedAt()
        );
    }

    public LiveAuditEventResponse toAuditEventResponse(LiveRiskEventEntity event, LiveOrderEntity order) {
        return new LiveAuditEventResponse(
                event.getId(),
                event.getOrderId(),
                event.getStrategyId(),
                event.getExchange(),
                event.getSymbol(),
                event.getEventType(),
                order == null ? null : order.getStatus(),
                event.getReason(),
                event.getCreatedAt()
        );
    }

    public TestnetCertificationReportResponse toCertificationReportResponse(TestnetCertificationReportEntity report) {
        return new TestnetCertificationReportResponse(
                report.getId(),
                report.getExchange(),
                report.getEnvironment(),
                report.getStartedAt(),
                report.getFinishedAt(),
                report.getConnectivityStatus(),
                report.getAccountSnapshotStatus(),
                report.getOpenOrdersStatus(),
                report.getReconciliationStatus(),
                report.getRiskChecksStatus(),
                report.getFinalResult(),
                report.isRealOrderSubmissionEnabled(),
                report.getMessage()
        );
    }
}
