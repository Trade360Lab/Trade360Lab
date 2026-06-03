package com.example.back.livetrading.service;

import com.example.back.auth.security.AuthContext;
import com.example.back.backtest.exception.BacktestResourceNotFoundException;
import com.example.back.livetrading.dto.CreateLiveCredentialRequest;
import com.example.back.livetrading.dto.CreateLiveOrderRequest;
import com.example.back.livetrading.dto.CreateLiveSessionRequest;
import com.example.back.livetrading.dto.BinanceTestnetCertificationResponse;
import com.example.back.livetrading.dto.ExchangeHealthResponse;
import com.example.back.livetrading.dto.KillSwitchRequest;
import com.example.back.livetrading.dto.LiveAuditEventResponse;
import com.example.back.livetrading.dto.LiveBalanceResponse;
import com.example.back.livetrading.dto.LiveCredentialStatusResponse;
import com.example.back.livetrading.dto.LiveOrderAuditResponse;
import com.example.back.livetrading.dto.LiveOrderResponse;
import com.example.back.livetrading.dto.LivePositionResponse;
import com.example.back.livetrading.dto.LiveRiskEventResponse;
import com.example.back.livetrading.dto.LiveRiskStatusResponse;
import com.example.back.livetrading.dto.LiveSessionResponse;
import com.example.back.livetrading.dto.TestnetCertificationReportResponse;
import com.example.back.livetrading.config.LiveTradingProperties;
import com.example.back.livetrading.entity.CircuitBreakerStateEntity;
import com.example.back.livetrading.entity.KillSwitchStateEntity;
import com.example.back.livetrading.entity.LiveExchangeCredentialEntity;
import com.example.back.livetrading.entity.LiveOrderEntity;
import com.example.back.livetrading.entity.LiveOrderSide;
import com.example.back.livetrading.entity.LiveOrderStatus;
import com.example.back.livetrading.entity.LiveOrderType;
import com.example.back.livetrading.entity.LivePositionEntity;
import com.example.back.livetrading.entity.LivePositionSyncStatus;
import com.example.back.livetrading.entity.LiveRiskEventEntity;
import com.example.back.livetrading.entity.LiveRiskEventType;
import com.example.back.livetrading.entity.LiveSessionStatus;
import com.example.back.livetrading.entity.LiveTradingSessionEntity;
import com.example.back.livetrading.entity.TestnetCertificationReportEntity;
import com.example.back.livetrading.repository.CircuitBreakerStateRepository;
import com.example.back.livetrading.repository.KillSwitchStateRepository;
import com.example.back.livetrading.repository.LiveExchangeCredentialRepository;
import com.example.back.livetrading.repository.LiveOrderRepository;
import com.example.back.livetrading.repository.LivePositionRepository;
import com.example.back.livetrading.repository.LiveRiskEventRepository;
import com.example.back.livetrading.repository.LiveTradingSessionRepository;
import com.example.back.livetrading.repository.TestnetCertificationReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveTradingService {

    private static final int MONEY_SCALE = 8;
    private static final Set<LiveOrderStatus> OPEN_STATUSES = Set.of(
            LiveOrderStatus.CREATED,
            LiveOrderStatus.SUBMITTED,
            LiveOrderStatus.ACCEPTED,
            LiveOrderStatus.PARTIALLY_FILLED
    );
    private static final Set<LiveOrderStatus> DAILY_NOTIONAL_STATUSES = Set.of(
            LiveOrderStatus.SUBMITTED,
            LiveOrderStatus.ACCEPTED,
            LiveOrderStatus.PARTIALLY_FILLED,
            LiveOrderStatus.FILLED
    );

    private final LiveTradingProperties properties;
    private final LiveExchangeCredentialRepository credentialRepository;
    private final LiveTradingSessionRepository sessionRepository;
    private final LiveOrderRepository orderRepository;
    private final LivePositionRepository positionRepository;
    private final CircuitBreakerStateRepository circuitBreakerRepository;
    private final KillSwitchStateRepository killSwitchRepository;
    private final LiveRiskEventRepository riskEventRepository;
    private final TestnetCertificationReportRepository certificationReportRepository;
    private final LiveCredentialCryptoService cryptoService;
    private final LiveExchangeAdapterRegistry adapterRegistry;
    private final LiveTradingMapper mapper;

    @Transactional
    public LiveCredentialStatusResponse storeCredentials(CreateLiveCredentialRequest request) {
        Long userId = AuthContext.requireUserId();
        String exchange = normalizeExchange(request.exchange());
        LiveExchangeCredentialEntity credential = new LiveExchangeCredentialEntity();
        credential.setUserId(userId);
        credential.setExchange(exchange);
        credential.setKeyReference(maskReference(request.apiKey()));
        credential.setEncryptedApiKey(cryptoService.encrypt(request.apiKey()));
        credential.setEncryptedApiSecret(cryptoService.encrypt(request.apiSecret()));
        credential.setActive(request.active());
        LiveExchangeCredentialEntity saved = credentialRepository.save(credential);
        log.info("Live credential stored user_id={} credential_id={} exchange={}", userId, saved.getId(), exchange);
        return mapper.toCredentialResponse(saved);
    }

    public List<LiveCredentialStatusResponse> credentialStatus() {
        Long userId = AuthContext.requireUserId();
        return credentialRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toCredentialResponse)
                .toList();
    }

    @Transactional
    public LiveSessionResponse createSession(CreateLiveSessionRequest request) {
        Long userId = AuthContext.requireUserId();
        LiveTradingSessionEntity session = new LiveTradingSessionEntity();
        session.setUserId(userId);
        session.setName(request.name().trim());
        session.setExchange(normalizeExchange(request.exchange()));
        session.setSymbol(request.symbol().trim().toUpperCase());
        session.setBaseCurrency(request.baseCurrency().trim().toUpperCase());
        session.setQuoteCurrency(request.quoteCurrency().trim().toUpperCase());
        session.setStatus(LiveSessionStatus.CREATED);
        session.setMaxOrderNotional(scale(request.maxOrderNotional()));
        session.setMaxPositionNotional(scale(request.maxPositionNotional()));
        session.setMaxDailyNotional(scale(request.maxDailyNotional()));
        session.setSymbolWhitelist(request.symbolWhitelist());
        LiveTradingSessionEntity saved = sessionRepository.save(session);
        log.info("Live session created user_id={} session_id={} exchange={} symbol={}",
                userId, saved.getId(), saved.getExchange(), saved.getSymbol());
        return mapper.toSessionResponse(saved);
    }

    public List<LiveSessionResponse> listSessions() {
        Long userId = AuthContext.requireUserId();
        return sessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toSessionResponse)
                .toList();
    }

    @Transactional
    public LiveSessionResponse enableSession(Long id) {
        LiveTradingSessionEntity session = requireOwnedSession(id);
        requireActiveCredentials(session.getUserId(), session.getExchange());
        session.setStatus(LiveSessionStatus.ENABLED);
        LiveTradingSessionEntity saved = sessionRepository.save(session);
        log.info("Live session enabled user_id={} session_id={} exchange={} symbol={}",
                saved.getUserId(), saved.getId(), saved.getExchange(), saved.getSymbol());
        return mapper.toSessionResponse(saved);
    }

    @Transactional
    public LiveSessionResponse disableSession(Long id) {
        LiveTradingSessionEntity session = requireOwnedSession(id);
        session.setStatus(LiveSessionStatus.DISABLED);
        LiveTradingSessionEntity saved = sessionRepository.save(session);
        log.info("Live session disabled user_id={} session_id={}", saved.getUserId(), saved.getId());
        return mapper.toSessionResponse(saved);
    }

    @Transactional
    public LiveOrderResponse placeOrder(CreateLiveOrderRequest request) {
        LiveTradingSessionEntity session = requireOwnedSession(request.sessionId());
        LiveOrderEntity order = buildOrder(session, request);
        String rejection = validateRisk(session, order);
        if (rejection != null) {
            return rejectOrder(order, rejection);
        }
        LiveExchangeCredentialEntity credential = requireActiveCredentials(session.getUserId(), session.getExchange());
        ExchangeCredentials credentials = decrypt(credential);
        LiveExchangeAdapter adapter = adapterRegistry.requireAdapter(session.getExchange());
        try {
            order.setStatus(LiveOrderStatus.SUBMITTED);
            order.setSubmittedAt(Instant.now());
            orderRepository.save(order);
            LiveOrderResult result = adapter.placeOrder(toAdapterRequest(order), credentials);
            order.setExchangeOrderId(result.exchangeOrderId());
            order.setExecutedPrice(result.executedPrice());
            order.setStatus(result.status());
            order.setRejectedReason(result.rejectedReason());
            if (result.status() == LiveOrderStatus.FILLED) {
                order.setFilledAt(Instant.now());
            }
            LiveOrderEntity saved = orderRepository.save(order);
            recordEvent(saved, LiveRiskEventType.ORDER_ACCEPTED, "Live order submitted");
            log.info("Live order submitted user_id={} strategy_id={} order_id={} exchange={} symbol={} status={}",
                    saved.getUserId(), saved.getStrategyId(), saved.getId(), saved.getExchange(), saved.getSymbol(),
                    saved.getStatus());
            return mapper.toOrderResponse(saved);
        } catch (Exception exception) {
            order.setStatus(LiveOrderStatus.FAILED);
            order.setRejectedReason("Exchange submission failed");
            LiveOrderEntity saved = orderRepository.save(order);
            maybeTriggerCircuitBreaker(saved, "Exchange submission failed");
            log.warn("Live order failed user_id={} order_id={} exchange={} symbol={} error={}",
                    saved.getUserId(), saved.getId(), saved.getExchange(), saved.getSymbol(), exception.getMessage());
            return mapper.toOrderResponse(saved);
        }
    }

    public List<LiveOrderResponse> listOrders() {
        Long userId = AuthContext.requireUserId();
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toOrderResponse)
                .toList();
    }

    public LiveOrderResponse getOrder(Long id) {
        Long userId = AuthContext.requireUserId();
        return orderRepository.findByIdAndUserId(id, userId)
                .map(mapper::toOrderResponse)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Live order not found: " + id));
    }

    @Transactional
    public LiveOrderResponse cancelOrder(Long id) {
        Long userId = AuthContext.requireUserId();
        LiveOrderEntity order = orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Live order not found: " + id));
        if (!OPEN_STATUSES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only open live orders can be canceled");
        }
        if (order.getExchangeOrderId() != null) {
            LiveExchangeCredentialEntity credential = requireActiveCredentials(userId, order.getExchange());
            adapterRegistry.requireAdapter(order.getExchange())
                    .cancelOrder(order.getExchangeOrderId(), order.getSymbol(), decrypt(credential));
        }
        order.setStatus(LiveOrderStatus.CANCELED);
        LiveOrderEntity saved = orderRepository.save(order);
        log.info("Live order canceled user_id={} order_id={} exchange={} symbol={}",
                userId, saved.getId(), saved.getExchange(), saved.getSymbol());
        return mapper.toOrderResponse(saved);
    }

    @Transactional
    public List<LivePositionResponse> syncPositions() {
        Long userId = AuthContext.requireUserId();
        List<LiveTradingSessionEntity> sessions = sessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        for (LiveTradingSessionEntity session : sessions) {
            try {
                LiveExchangeCredentialEntity credential = requireActiveCredentials(userId, session.getExchange());
                List<ExchangePositionSnapshot> snapshots = adapterRegistry.requireAdapter(session.getExchange())
                        .getPositions(decrypt(credential));
                for (ExchangePositionSnapshot snapshot : snapshots) {
                    LivePositionEntity position = positionRepository
                            .findByUserIdAndExchangeAndSymbol(userId, session.getExchange(), snapshot.symbol())
                            .orElseGet(LivePositionEntity::new);
                    position.setUserId(userId);
                    position.setExchange(session.getExchange());
                    position.setSymbol(snapshot.symbol());
                    position.setQuantity(scale(snapshot.quantity()));
                    position.setAverageEntryPrice(scale(snapshot.averageEntryPrice()));
                    position.setRealizedPnl(scale(snapshot.realizedPnl()));
                    position.setUnrealizedPnl(scale(snapshot.unrealizedPnl()));
                    position.setSyncStatus(LivePositionSyncStatus.SYNCED);
                    positionRepository.save(position);
                }
            } catch (Exception exception) {
                recordEvent(userId, null, null, session.getExchange(), session.getSymbol(),
                        LiveRiskEventType.POSITION_SYNC_FAILED, "Position sync failed");
                log.warn("Live position sync failed user_id={} exchange={} error={}",
                        userId, session.getExchange(), exception.getMessage());
            }
        }
        return listPositions();
    }

    public List<LivePositionResponse> listPositions() {
        Long userId = AuthContext.requireUserId();
        return positionRepository.findAllByUserIdOrderByExchangeAscSymbolAsc(userId).stream()
                .map(mapper::toPositionResponse)
                .toList();
    }

    public List<LiveBalanceResponse> getBalances() {
        Long userId = AuthContext.requireUserId();
        return sessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .findFirst()
                .flatMap(session -> credentialRepository.findFirstByUserIdAndExchangeAndActiveTrueOrderByUpdatedAtDesc(
                        userId, session.getExchange()))
                .map(credential -> adapterRegistry.requireAdapter(credential.getExchange()).getBalances(decrypt(credential))
                        .stream().map(mapper::toBalanceResponse).toList())
                .orElse(List.of());
    }

    public LiveRiskStatusResponse riskStatus() {
        Long userId = AuthContext.requireUserId();
        KillSwitchStateEntity killSwitch = killSwitchRepository.findByUserId(userId).orElse(null);
        return new LiveRiskStatusResponse(
                killSwitch != null && killSwitch.isActive(),
                killSwitch == null ? null : killSwitch.getReason(),
                killSwitch == null ? null : killSwitch.getActivatedAt(),
                circuitBreakerRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                        .map(mapper::toCircuitBreakerResponse)
                        .toList(),
                scale(totalSyncedPositionExposure(userId, null)),
                scale(totalOpenOrderExposure(userId, null)),
                scale(totalAcceptedDailyNotional(userId, null)),
                scale(totalRealizedIntradayLoss(userId, null)),
                properties.defaultMaxDailyLossNotional(),
                properties.maxAllowedSlippagePercent(),
                properties.maxMarketDataAgeSeconds()
        );
    }

    public List<LiveRiskEventResponse> riskEvents() {
        Long userId = AuthContext.requireUserId();
        return riskEventRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toRiskEventResponse)
                .toList();
    }

    public List<LiveAuditEventResponse> auditEvents(
            Instant from,
            Instant to,
            String exchange,
            String symbol,
            LiveOrderStatus status,
            Long orderId,
            String reason
    ) {
        Long userId = AuthContext.requireUserId();
        Map<Long, LiveOrderEntity> ordersById = orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .collect(java.util.stream.Collectors.toMap(LiveOrderEntity::getId, order -> order));
        Specification<LiveRiskEventEntity> specification = auditSpecification(
                userId, from, to, exchange, symbol, orderId, reason);
        return riskEventRepository.findAll(specification).stream()
                .filter(event -> status == null || orderStatusMatches(ordersById, event, status))
                .sorted(Comparator.comparing(LiveRiskEventEntity::getCreatedAt).reversed())
                .map(event -> mapper.toAuditEventResponse(event, ordersById.get(event.getOrderId())))
                .toList();
    }

    public String auditEventsCsv(
            Instant from,
            Instant to,
            String exchange,
            String symbol,
            LiveOrderStatus status,
            Long orderId,
            String reason
    ) {
        StringBuilder csv = new StringBuilder("eventId,orderId,strategyId,exchange,symbol,eventType,orderStatus,reason,createdAt\n");
        for (LiveAuditEventResponse event : auditEvents(from, to, exchange, symbol, status, orderId, reason)) {
            csv.append(event.eventId()).append(',')
                    .append(nullToBlank(event.orderId())).append(',')
                    .append(nullToBlank(event.strategyId())).append(',')
                    .append(csvCell(event.exchange())).append(',')
                    .append(csvCell(event.symbol())).append(',')
                    .append(event.eventType()).append(',')
                    .append(nullToBlank(event.orderStatus())).append(',')
                    .append(csvCell(event.reason())).append(',')
                    .append(event.createdAt())
                    .append('\n');
        }
        return csv.toString();
    }

    public LiveOrderAuditResponse orderAudit(Long id) {
        Long userId = AuthContext.requireUserId();
        LiveOrderEntity order = orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Live order not found: " + id));
        List<LiveAuditEventResponse> events = riskEventRepository.findAllByUserIdAndOrderIdOrderByCreatedAtDesc(userId, id).stream()
                .map(event -> mapper.toAuditEventResponse(event, order))
                .toList();
        return new LiveOrderAuditResponse(mapper.toOrderResponse(order), events);
    }

    @Transactional
    public LiveRiskStatusResponse activateKillSwitch(KillSwitchRequest request) {
        Long userId = AuthContext.requireUserId();
        KillSwitchStateEntity state = killSwitchRepository.findByUserId(userId).orElseGet(KillSwitchStateEntity::new);
        state.setUserId(userId);
        state.setActive(true);
        state.setReason(request.reason() == null || request.reason().isBlank() ? "Manual emergency stop" : request.reason());
        state.setActivatedAt(Instant.now());
        killSwitchRepository.save(state);
        recordEvent(userId, null, null, "all", null, LiveRiskEventType.KILL_SWITCH_ACTIVATED, state.getReason());
        log.warn("Live kill switch activated user_id={} reason={}", userId, state.getReason());
        return riskStatus();
    }

    @Transactional
    public LiveRiskStatusResponse resetKillSwitch() {
        Long userId = AuthContext.requireUserId();
        KillSwitchStateEntity state = killSwitchRepository.findByUserId(userId).orElseGet(KillSwitchStateEntity::new);
        state.setUserId(userId);
        state.setActive(false);
        state.setReason(null);
        state.setActivatedAt(null);
        killSwitchRepository.save(state);
        recordEvent(userId, null, null, "all", null, LiveRiskEventType.KILL_SWITCH_RESET, "Manual reset");
        log.info("Live kill switch reset user_id={}", userId);
        return riskStatus();
    }

    @Transactional
    public LiveRiskStatusResponse resetCircuitBreakers() {
        Long userId = AuthContext.requireUserId();
        for (CircuitBreakerStateEntity state : circuitBreakerRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)) {
            state.setActive(false);
            state.setReason(null);
            state.setTriggeredAt(null);
            circuitBreakerRepository.save(state);
            recordEvent(userId, null, null, state.getExchange(), null,
                    LiveRiskEventType.CIRCUIT_BREAKER_RESET, "Manual reset");
        }
        return riskStatus();
    }

    public ExchangeHealthResponse exchangeHealth(String exchange) {
        Long userId = AuthContext.requireUserId();
        String normalizedExchange = normalizeExchange(exchange == null ? "binance" : exchange);
        LiveExchangeAdapter adapter = adapterRegistry.requireAdapter(normalizedExchange);
        boolean connected = adapter.pingConnection();
        boolean credentialsValid = credentialRepository
                .findFirstByUserIdAndExchangeAndActiveTrueOrderByUpdatedAtDesc(userId, normalizedExchange)
                .map(credential -> adapter.validateCredentials(decrypt(credential)))
                .orElse(false);
        return new ExchangeHealthResponse(
                normalizedExchange,
                connected,
                credentialsValid,
                properties.realOrderSubmissionEnabled(),
                connected ? "Exchange ping completed" : "Exchange ping failed"
        );
    }

    @Transactional
    public TestnetCertificationReportResponse runBinanceTestnetCertificationReport() {
        Instant startedAt = Instant.now();
        BinanceTestnetCertificationResponse certification = certifyBinanceTestnet();
        TestnetCertificationReportEntity report = new TestnetCertificationReportEntity();
        report.setUserId(AuthContext.requireUserId());
        report.setExchange(certification.exchange());
        report.setEnvironment("testnet");
        report.setStartedAt(startedAt);
        report.setFinishedAt(certification.checkedAt());
        report.setConnectivityStatus(status(certification.credentialsValid() || certification.credentialsPresent()));
        report.setAccountSnapshotStatus(status(certification.accountSnapshotReachable()));
        report.setOpenOrdersStatus(status(certification.openOrdersSnapshotReachable()));
        report.setReconciliationStatus(certification.certified() ? "PASS" : "NOT_CERTIFIED");
        report.setRiskChecksStatus(certification.realOrderSubmissionEnabled() ? "FAIL_REAL_SUBMISSION_ENABLED" : "PASS_SAFE_DEFAULT");
        report.setFinalResult(certification.certified() && !certification.realOrderSubmissionEnabled() ? "PASS" : "FAIL");
        report.setRealOrderSubmissionEnabled(certification.realOrderSubmissionEnabled());
        report.setMessage(certification.message());
        return mapper.toCertificationReportResponse(certificationReportRepository.save(report));
    }

    public TestnetCertificationReportResponse latestBinanceTestnetCertificationReport() {
        Long userId = AuthContext.requireUserId();
        return certificationReportRepository.findFirstByUserIdAndExchangeOrderByFinishedAtDesc(userId, "binance")
                .map(mapper::toCertificationReportResponse)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Testnet certification report not found"));
    }

    public TestnetCertificationReportResponse getCertificationReport(Long id) {
        Long userId = AuthContext.requireUserId();
        return certificationReportRepository.findByIdAndUserId(id, userId)
                .map(mapper::toCertificationReportResponse)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Testnet certification report not found: " + id));
    }

    public BinanceTestnetCertificationResponse certifyBinanceTestnet() {
        Long userId = AuthContext.requireUserId();
        LiveExchangeCredentialEntity credential = credentialRepository
                .findFirstByUserIdAndExchangeAndActiveTrueOrderByUpdatedAtDesc(userId, "binance")
                .orElse(null);
        if (credential == null) {
            return new BinanceTestnetCertificationResponse(
                    "binance",
                    true,
                    properties.realOrderSubmissionEnabled(),
                    false,
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    "Active Binance testnet credentials are required",
                    Instant.now()
            );
        }
        LiveExchangeAdapter adapter = adapterRegistry.requireAdapter("binance");
        if (!(adapter instanceof BinanceLiveExchangeAdapter binanceAdapter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Binance adapter is not available");
        }
        return binanceAdapter.certifyTestnetReadOnly(decrypt(credential));
    }

    private Specification<LiveRiskEventEntity> auditSpecification(
            Long userId,
            Instant from,
            Instant to,
            String exchange,
            String symbol,
            Long orderId,
            String reason
    ) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(builder.equal(root.get("userId"), userId));
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (exchange != null && !exchange.isBlank()) {
                predicates.add(builder.equal(builder.lower(root.get("exchange")), normalizeExchange(exchange)));
            }
            if (symbol != null && !symbol.isBlank()) {
                predicates.add(builder.equal(builder.upper(root.get("symbol")), symbol.trim().toUpperCase()));
            }
            if (orderId != null) {
                predicates.add(builder.equal(root.get("orderId"), orderId));
            }
            if (reason != null && !reason.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("reason")), "%" + reason.trim().toLowerCase() + "%"));
            }
            query.orderBy(builder.desc(root.get("createdAt")));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private boolean orderStatusMatches(Map<Long, LiveOrderEntity> ordersById, LiveRiskEventEntity event, LiveOrderStatus status) {
        LiveOrderEntity order = ordersById.get(event.getOrderId());
        return order != null && order.getStatus() == status;
    }

    private String status(boolean passed) {
        return passed ? "PASS" : "FAIL";
    }

    private String nullToBlank(Object value) {
        return value == null ? "" : value.toString();
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String validateRisk(LiveTradingSessionEntity session, LiveOrderEntity order) {
        if (killSwitchRepository.findByUserId(session.getUserId()).filter(KillSwitchStateEntity::isActive).isPresent()) {
            return "Kill switch is active";
        }
        if (circuitBreakerRepository.findByUserIdAndExchange(session.getUserId(), session.getExchange())
                .filter(CircuitBreakerStateEntity::isActive).isPresent()) {
            return "Circuit breaker is active for exchange " + session.getExchange();
        }
        if (session.getStatus() != LiveSessionStatus.ENABLED) {
            return "Live session must be ENABLED";
        }
        LiveExchangeCredentialEntity credential = credentialRepository
                .findFirstByUserIdAndExchangeAndActiveTrueOrderByUpdatedAtDesc(session.getUserId(), session.getExchange())
                .orElse(null);
        if (credential == null) {
            return "Active exchange credentials are required";
        }
        LiveExchangeAdapter adapter = adapterRegistry.requireAdapter(session.getExchange());
        if (!adapter.pingConnection()) {
            return "Exchange connectivity check failed";
        }
        if (!adapter.validateCredentials(decrypt(credential))) {
            return "Exchange credentials are invalid";
        }
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return "Quantity must be positive";
        }
        if (order.getType() == LiveOrderType.LIMIT
                && (order.getRequestedPrice() == null || order.getRequestedPrice().compareTo(BigDecimal.ZERO) <= 0)) {
            return "Limit price must be positive";
        }
        if (session.getSymbolWhitelist() != null && !session.getSymbolWhitelist().isBlank()
                && !List.of(session.getSymbolWhitelist().split(",")).contains(order.getSymbol())) {
            return "Symbol is not whitelisted for live trading";
        }
        ExchangePriceSnapshot priceSnapshot = adapter.getLatestPriceSnapshot(order.getSymbol()).orElse(null);
        if (priceSnapshot == null || priceSnapshot.price() == null || priceSnapshot.price().compareTo(BigDecimal.ZERO) <= 0) {
            return "Latest market price is unavailable";
        }
        if (priceSnapshot.observedAt() == null || Duration.between(priceSnapshot.observedAt(), Instant.now())
                .compareTo(Duration.ofSeconds(properties.maxMarketDataAgeSeconds())) > 0) {
            return "Market data is stale for symbol " + order.getSymbol();
        }
        BigDecimal marketPrice = priceSnapshot.price();
        BigDecimal riskPrice = order.getType() == LiveOrderType.LIMIT ? order.getRequestedPrice() : marketPrice;
        if (order.getType() == LiveOrderType.MARKET && order.getRequestedPrice() == null) {
            order.setRequestedPrice(scale(marketPrice));
        }
        if (order.getType() == LiveOrderType.LIMIT) {
            BigDecimal slippagePercent = order.getRequestedPrice()
                    .subtract(marketPrice)
                    .abs()
                    .multiply(new BigDecimal("100"))
                    .divide(marketPrice, MONEY_SCALE, RoundingMode.HALF_UP);
            if (slippagePercent.compareTo(properties.maxAllowedSlippagePercent()) > 0) {
                return "Limit price exceeds max slippage percent " + properties.maxAllowedSlippagePercent();
            }
        }
        BigDecimal notional = scale(order.getQuantity().multiply(riskPrice));
        if (notional.compareTo(session.getMaxOrderNotional()) > 0) {
            return "Order notional exceeds session max order limit " + session.getMaxOrderNotional();
        }
        BigDecimal projectedExposure = totalSyncedPositionExposure(session.getUserId(), session.getExchange())
                .add(totalOpenOrderExposure(session.getUserId(), session.getExchange()))
                .add(notional);
        if (projectedExposure.compareTo(session.getMaxPositionNotional()) > 0) {
            return "Portfolio exposure would exceed max position limit " + session.getMaxPositionNotional();
        }
        BigDecimal projectedDailyNotional = totalAcceptedDailyNotional(session.getUserId(), session.getId()).add(notional);
        if (projectedDailyNotional.compareTo(session.getMaxDailyNotional()) > 0) {
            return "Daily notional would exceed session max daily limit " + session.getMaxDailyNotional();
        }
        BigDecimal realizedIntradayLoss = totalRealizedIntradayLoss(session.getUserId(), session.getExchange());
        if (realizedIntradayLoss.compareTo(properties.defaultMaxDailyLossNotional()) > 0) {
            return "Realized intraday loss exceeds configured limit " + properties.defaultMaxDailyLossNotional();
        }
        if (orderRepository.existsByUserIdAndExchangeAndSymbolAndSideAndTypeAndQuantityAndStatusInAndIdNot(
                order.getUserId(), order.getExchange(), order.getSymbol(), order.getSide(), order.getType(),
                order.getQuantity(), OPEN_STATUSES, order.getId())) {
            return "Duplicate open live order is already present";
        }
        if (order.getSide() == LiveOrderSide.BUY) {
            List<ExchangeBalanceSnapshot> balances = adapter.getBalances(decrypt(credential));
            BigDecimal available = balances.stream()
                    .filter(balance -> session.getQuoteCurrency().equalsIgnoreCase(balance.asset()))
                    .map(ExchangeBalanceSnapshot::free)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
            if (!balances.isEmpty() && available.compareTo(notional) < 0) {
                return "Available balance is below order notional";
            }
        }
        return null;
    }

    private BigDecimal totalSyncedPositionExposure(Long userId, String exchange) {
        return positionRepository.findAllByUserIdOrderByExchangeAscSymbolAsc(userId).stream()
                .filter(position -> exchange == null || exchange.equals(position.getExchange()))
                .map(position -> safe(position.getQuantity()).abs().multiply(safe(position.getAverageEntryPrice())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalOpenOrderExposure(Long userId, String exchange) {
        List<LiveOrderEntity> openOrders = exchange == null
                ? orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                        .filter(order -> OPEN_STATUSES.contains(order.getStatus()))
                        .toList()
                : orderRepository.findAllByUserIdAndExchangeAndStatusIn(userId, exchange, OPEN_STATUSES);
        return openOrders.stream()
                .map(this::orderExposure)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalAcceptedDailyNotional(Long userId, Long sessionId) {
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<LiveOrderEntity> dailyOrders = sessionId == null
                ? orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                        .filter(order -> DAILY_NOTIONAL_STATUSES.contains(order.getStatus()))
                        .filter(order -> !order.getCreatedAt().isBefore(dayStart))
                        .toList()
                : orderRepository.findAllByUserIdAndSessionIdAndStatusInAndCreatedAtGreaterThanEqual(
                        userId, sessionId, DAILY_NOTIONAL_STATUSES, dayStart);
        return dailyOrders.stream()
                .map(this::orderExposure)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalRealizedIntradayLoss(Long userId, String exchange) {
        return positionRepository.findAllByUserIdOrderByExchangeAscSymbolAsc(userId).stream()
                .filter(position -> exchange == null || exchange.equals(position.getExchange()))
                .map(position -> safe(position.getRealizedPnl()))
                .filter(realizedPnl -> realizedPnl.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal orderExposure(LiveOrderEntity order) {
        BigDecimal price = order.getExecutedPrice() != null ? order.getExecutedPrice() : order.getRequestedPrice();
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return safe(order.getQuantity()).multiply(price);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private LiveOrderResponse rejectOrder(LiveOrderEntity order, String reason) {
        order.setStatus(LiveOrderStatus.REJECTED);
        order.setRejectedReason(reason);
        LiveOrderEntity saved = orderRepository.save(order);
        recordEvent(saved, LiveRiskEventType.ORDER_REJECTED, reason);
        maybeTriggerCircuitBreaker(saved, reason);
        log.warn("Live order rejected user_id={} strategy_id={} order_id={} exchange={} symbol={} reason={}",
                saved.getUserId(), saved.getStrategyId(), saved.getId(), saved.getExchange(), saved.getSymbol(), reason);
        return mapper.toOrderResponse(saved);
    }

    private void maybeTriggerCircuitBreaker(LiveOrderEntity order, String reason) {
        long failedCount = orderRepository.countByUserIdAndExchangeAndStatus(
                order.getUserId(), order.getExchange(), LiveOrderStatus.FAILED);
        long rejectedCount = orderRepository.countByUserIdAndExchangeAndStatus(
                order.getUserId(), order.getExchange(), LiveOrderStatus.REJECTED);
        if (failedCount >= properties.maxFailedOrdersBeforeCircuitBreaker()
                || rejectedCount >= properties.maxRejectedOrdersBeforeCircuitBreaker()) {
            CircuitBreakerStateEntity state = circuitBreakerRepository
                    .findByUserIdAndExchange(order.getUserId(), order.getExchange())
                    .orElseGet(CircuitBreakerStateEntity::new);
            state.setUserId(order.getUserId());
            state.setExchange(order.getExchange());
            state.setActive(true);
            state.setReason(reason);
            state.setTriggeredAt(Instant.now());
            circuitBreakerRepository.save(state);
            recordEvent(order, LiveRiskEventType.CIRCUIT_BREAKER_TRIGGERED, reason);
            log.warn("Live circuit breaker triggered user_id={} exchange={} reason={}",
                    order.getUserId(), order.getExchange(), reason);
        }
    }

    private LiveOrderEntity buildOrder(LiveTradingSessionEntity session, CreateLiveOrderRequest request) {
        LiveOrderEntity order = new LiveOrderEntity();
        order.setUserId(session.getUserId());
        order.setSessionId(session.getId());
        order.setStrategyId(request.strategyId());
        order.setStrategyVersionId(request.strategyVersionId());
        order.setExchange(session.getExchange());
        order.setSymbol(session.getSymbol());
        order.setSide(request.side());
        order.setType(request.type());
        order.setQuantity(scale(request.quantity()));
        order.setRequestedPrice(request.requestedPrice() == null ? null : scale(request.requestedPrice()));
        order.setStatus(LiveOrderStatus.CREATED);
        order.setSourceRunId(request.sourceRunId());
        return orderRepository.save(order);
    }

    private LiveOrderRequest toAdapterRequest(LiveOrderEntity order) {
        return new LiveOrderRequest(
                order.getExchange(),
                order.getSymbol(),
                order.getSide(),
                order.getType(),
                order.getQuantity(),
                order.getRequestedPrice()
        );
    }

    private LiveTradingSessionEntity requireOwnedSession(Long id) {
        Long userId = AuthContext.requireUserId();
        return sessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Live session not found: " + id));
    }

    private LiveExchangeCredentialEntity requireActiveCredentials(Long userId, String exchange) {
        return credentialRepository.findFirstByUserIdAndExchangeAndActiveTrueOrderByUpdatedAtDesc(userId, exchange)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Active live exchange credentials are required"
                ));
    }

    private ExchangeCredentials decrypt(LiveExchangeCredentialEntity credential) {
        return new ExchangeCredentials(
                cryptoService.decrypt(credential.getEncryptedApiKey()),
                cryptoService.decrypt(credential.getEncryptedApiSecret())
        );
    }

    private void recordEvent(LiveOrderEntity order, LiveRiskEventType type, String reason) {
        recordEvent(order.getUserId(), order.getId(), order.getStrategyId(), order.getExchange(),
                order.getSymbol(), type, reason);
    }

    private void recordEvent(
            Long userId,
            Long orderId,
            Long strategyId,
            String exchange,
            String symbol,
            LiveRiskEventType type,
            String reason
    ) {
        LiveRiskEventEntity event = new LiveRiskEventEntity();
        event.setUserId(userId);
        event.setOrderId(orderId);
        event.setStrategyId(strategyId);
        event.setExchange(exchange);
        event.setSymbol(symbol);
        event.setEventType(type);
        event.setReason(reason);
        riskEventRepository.save(event);
    }

    private String normalizeExchange(String exchange) {
        return exchange.trim().toLowerCase();
    }

    private String maskReference(String apiKey) {
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
