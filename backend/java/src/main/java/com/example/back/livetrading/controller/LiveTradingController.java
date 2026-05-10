package com.example.back.livetrading.controller;

import com.example.back.livetrading.dto.BinanceTestnetCertificationResponse;
import com.example.back.livetrading.dto.CreateLiveCredentialRequest;
import com.example.back.livetrading.dto.CreateLiveOrderRequest;
import com.example.back.livetrading.dto.CreateLiveSessionRequest;
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
import com.example.back.livetrading.entity.LiveOrderStatus;
import com.example.back.livetrading.service.LiveTradingService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveTradingController {

    private final LiveTradingService liveTradingService;

    @PostMapping("/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    public LiveCredentialStatusResponse storeCredentials(@Valid @RequestBody CreateLiveCredentialRequest request) {
        return liveTradingService.storeCredentials(request);
    }

    @GetMapping("/credentials/status")
    public List<LiveCredentialStatusResponse> credentialStatus() {
        return liveTradingService.credentialStatus();
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public LiveSessionResponse createSession(@Valid @RequestBody CreateLiveSessionRequest request) {
        return liveTradingService.createSession(request);
    }

    @GetMapping("/sessions")
    public List<LiveSessionResponse> listSessions() {
        return liveTradingService.listSessions();
    }

    @PostMapping("/sessions/{id}/enable")
    public LiveSessionResponse enableSession(@PathVariable Long id) {
        return liveTradingService.enableSession(id);
    }

    @PostMapping("/sessions/{id}/disable")
    public LiveSessionResponse disableSession(@PathVariable Long id) {
        return liveTradingService.disableSession(id);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public LiveOrderResponse placeOrder(@Valid @RequestBody CreateLiveOrderRequest request) {
        return liveTradingService.placeOrder(request);
    }

    @GetMapping("/orders")
    public List<LiveOrderResponse> listOrders() {
        return liveTradingService.listOrders();
    }

    @GetMapping("/orders/{id}")
    public LiveOrderResponse getOrder(@PathVariable Long id) {
        return liveTradingService.getOrder(id);
    }

    @PostMapping("/orders/{id}/cancel")
    public LiveOrderResponse cancelOrder(@PathVariable Long id) {
        return liveTradingService.cancelOrder(id);
    }

    @GetMapping("/positions")
    public List<LivePositionResponse> listPositions() {
        return liveTradingService.listPositions();
    }

    @PostMapping("/positions/sync")
    public List<LivePositionResponse> syncPositions() {
        return liveTradingService.syncPositions();
    }

    @GetMapping("/balances")
    public List<LiveBalanceResponse> balances() {
        return liveTradingService.getBalances();
    }

    @GetMapping("/risk/status")
    public LiveRiskStatusResponse riskStatus() {
        return liveTradingService.riskStatus();
    }

    @GetMapping("/risk/events")
    public List<LiveRiskEventResponse> riskEvents() {
        return liveTradingService.riskEvents();
    }

    @GetMapping("/audit-events")
    public List<LiveAuditEventResponse> auditEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) LiveOrderStatus status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String reason
    ) {
        return liveTradingService.auditEvents(from, to, exchange, symbol, status, orderId, reason);
    }

    @GetMapping("/audit-events/export")
    public ResponseEntity<String> exportAuditEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) LiveOrderStatus status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String reason
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=live-audit-events.csv")
                .body(liveTradingService.auditEventsCsv(from, to, exchange, symbol, status, orderId, reason));
    }

    @GetMapping("/orders/{id}/audit")
    public LiveOrderAuditResponse orderAudit(@PathVariable Long id) {
        return liveTradingService.orderAudit(id);
    }

    @PostMapping("/kill-switch/activate")
    public LiveRiskStatusResponse activateKillSwitch(@RequestBody(required = false) KillSwitchRequest request) {
        KillSwitchRequest safeRequest = request == null ? new KillSwitchRequest(null, false) : request;
        return liveTradingService.activateKillSwitch(safeRequest);
    }

    @PostMapping("/kill-switch/reset")
    public LiveRiskStatusResponse resetKillSwitch() {
        return liveTradingService.resetKillSwitch();
    }

    @PostMapping("/circuit-breakers/reset")
    public LiveRiskStatusResponse resetCircuitBreakers() {
        return liveTradingService.resetCircuitBreakers();
    }

    @GetMapping("/exchange/health")
    public ExchangeHealthResponse exchangeHealth(@RequestParam(defaultValue = "binance") String exchange) {
        return liveTradingService.exchangeHealth(exchange);
    }

    @PostMapping("/exchange/binance/testnet-certification")
    public BinanceTestnetCertificationResponse certifyBinanceTestnet() {
        return liveTradingService.certifyBinanceTestnet();
    }

    @PostMapping("/certification/testnet/run")
    public TestnetCertificationReportResponse runTestnetCertification() {
        return liveTradingService.runBinanceTestnetCertificationReport();
    }

    @GetMapping("/certification/testnet/latest")
    public TestnetCertificationReportResponse latestTestnetCertification() {
        return liveTradingService.latestBinanceTestnetCertificationReport();
    }

    @GetMapping("/certification/testnet/{id}")
    public TestnetCertificationReportResponse getTestnetCertification(@PathVariable Long id) {
        return liveTradingService.getCertificationReport(id);
    }
}
