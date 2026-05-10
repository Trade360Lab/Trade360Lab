package com.example.back.livetrading.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.back.backtest.executor.PythonBacktestExecutor;
import com.example.back.imports.client.PythonParserClient;
import com.example.back.livetrading.entity.LiveOrderStatus;
import com.example.back.livetrading.repository.CircuitBreakerStateRepository;
import com.example.back.livetrading.repository.KillSwitchStateRepository;
import com.example.back.livetrading.repository.LiveExchangeCredentialRepository;
import com.example.back.livetrading.repository.LiveOrderRepository;
import com.example.back.livetrading.repository.LivePositionRepository;
import com.example.back.livetrading.repository.LiveRiskEventRepository;
import com.example.back.livetrading.repository.LiveTradingSessionRepository;
import com.example.back.livetrading.repository.TestnetCertificationReportRepository;
import com.example.back.livetrading.service.ExchangeBalanceSnapshot;
import com.example.back.livetrading.service.ExchangeCredentials;
import com.example.back.livetrading.service.ExchangePositionSnapshot;
import com.example.back.livetrading.service.LiveExchangeAdapter;
import com.example.back.livetrading.service.LiveOrderRequest;
import com.example.back.livetrading.service.LiveOrderResult;
import com.example.back.support.TestAuth;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
@AutoConfigureMockMvc
@Import(LiveTradingControllerIntegrationTest.LiveAdapterTestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:live-trading;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=INTERVAL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "execution.jobs.worker-enabled=false",
        "live.trading.max-rejected-orders-before-circuit-breaker=1"
})
class LiveTradingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiveOrderRepository orderRepository;

    @Autowired
    private LiveTradingSessionRepository sessionRepository;

    @Autowired
    private LiveExchangeCredentialRepository credentialRepository;

    @Autowired
    private LivePositionRepository positionRepository;

    @Autowired
    private LiveRiskEventRepository riskEventRepository;

    @Autowired
    private TestnetCertificationReportRepository certificationReportRepository;

    @Autowired
    private CircuitBreakerStateRepository circuitBreakerRepository;

    @Autowired
    private KillSwitchStateRepository killSwitchRepository;

    @MockBean
    private PythonBacktestExecutor pythonBacktestExecutor;

    @MockBean
    private PythonParserClient pythonParserClient;

    @BeforeEach
    void setUp() {
        riskEventRepository.deleteAll();
        certificationReportRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
        sessionRepository.deleteAll();
        credentialRepository.deleteAll();
        circuitBreakerRepository.deleteAll();
        killSwitchRepository.deleteAll();
    }

    @Test
    void storesCredentialsWithoutExposingSecrets() throws Exception {
        mockMvc.perform(post("/api/live/credentials")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content(credentialsJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchange").value("testx"))
                .andExpect(jsonPath("$.keyReference").value("abcd...wxyz"))
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.apiSecret").doesNotExist());
    }

    @Test
    void successfulLiveOrderFlowRequiresEnabledSession() throws Exception {
        Long sessionId = createEnabledSession();

        mockMvc.perform(post("/api/live/orders")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "sessionId": %d,
                                  "side": "BUY",
                                  "type": "MARKET",
                                  "quantity": 0.01
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.exchangeOrderId").value("testx-accepted"));

        mockMvc.perform(get("/api/live/orders").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    @Test
    void killSwitchBlocksNewLiveOrders() throws Exception {
        Long sessionId = createEnabledSession();

        mockMvc.perform(post("/api/live/kill-switch/activate")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("{\"reason\":\"test stop\",\"cancelOpenOrders\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.killSwitchActive").value(true));

        mockMvc.perform(post("/api/live/orders")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "sessionId": %d,
                                  "side": "BUY",
                                  "type": "MARKET",
                                  "quantity": 0.01
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectedReason").value("Kill switch is active"));
    }

    @Test
    void riskRejectionTriggersCircuitBreakerAndBlocksNextOrder() throws Exception {
        Long sessionId = createEnabledSession();

        mockMvc.perform(post("/api/live/orders")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "sessionId": %d,
                                  "side": "BUY",
                                  "type": "MARKET",
                                  "quantity": 100
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/live/orders")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "sessionId": %d,
                                  "side": "BUY",
                                  "type": "MARKET",
                                  "quantity": 0.01
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectedReason").value("Circuit breaker is active for exchange testx"));
    }

    @Test
    void enforcesSessionOwnership() throws Exception {
        Long sessionId = createEnabledSession();
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setUserId(999L);
            sessionRepository.save(session);
        });

        mockMvc.perform(post("/api/live/orders")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "sessionId": %d,
                                  "side": "BUY",
                                  "type": "MARKET",
                                  "quantity": 0.01
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void binanceTestnetCertificationRequiresActiveCredentialsAndStaysTestnetOnly() throws Exception {
        mockMvc.perform(post("/api/live/exchange/binance/testnet-certification")
                        .with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchange").value("binance"))
                .andExpect(jsonPath("$.testnetOnly").value(true))
                .andExpect(jsonPath("$.realOrderSubmissionEnabled").value(false))
                .andExpect(jsonPath("$.credentialsPresent").value(false))
                .andExpect(jsonPath("$.certified").value(false))
                .andExpect(jsonPath("$.message").value("Active Binance testnet credentials are required"));
    }

    @Test
    void auditExportFiltersRejectedOrders() throws Exception {
        Long sessionId = createEnabledSession();

        mockMvc.perform(post("/api/live/orders")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "sessionId": %d,
                                  "side": "BUY",
                                  "type": "MARKET",
                                  "quantity": 100
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/live/audit-events")
                        .param("exchange", "testx")
                        .param("symbol", "BTCUSDT")
                        .param("status", "REJECTED")
                        .param("reason", "notional")
                        .with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ORDER_REJECTED")))
                .andExpect(content().string(containsString("REJECTED")));

        mockMvc.perform(get("/api/live/audit-events/export")
                        .param("status", "REJECTED")
                        .with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ORDER_REJECTED")));
    }

    @Test
    void orderAuditReturnsOrderHistory() throws Exception {
        Long sessionId = createEnabledSession();
        String response = mockMvc.perform(post("/api/live/orders")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "sessionId": %d,
                                  "side": "BUY",
                                  "type": "MARKET",
                                  "quantity": 100
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long orderId = Long.valueOf(response.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/live/orders/" + orderId + "/audit").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value(orderId))
                .andExpect(content().string(containsString("ORDER_REJECTED")));
    }

    @Test
    void persistsTestnetCertificationReport() throws Exception {
        mockMvc.perform(post("/api/live/certification/testnet/run")
                        .with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchange").value("binance"))
                .andExpect(jsonPath("$.environment").value("testnet"))
                .andExpect(jsonPath("$.finalResult").value("FAIL"))
                .andExpect(jsonPath("$.realOrderSubmissionEnabled").value(false));

        mockMvc.perform(get("/api/live/certification/testnet/latest")
                        .with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchange").value("binance"))
                .andExpect(jsonPath("$.riskChecksStatus").value("PASS_SAFE_DEFAULT"));
    }

    private Long createEnabledSession() throws Exception {
        mockMvc.perform(post("/api/live/credentials")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content(credentialsJson()))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/live/sessions")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Guarded test",
                                  "exchange": "testx",
                                  "symbol": "BTCUSDT",
                                  "baseCurrency": "BTC",
                                  "quoteCurrency": "USDT",
                                  "maxOrderNotional": 100,
                                  "maxPositionNotional": 500,
                                  "maxDailyNotional": 1000,
                                  "symbolWhitelist": "BTCUSDT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long sessionId = Long.valueOf(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(post("/api/live/sessions/" + sessionId + "/enable").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk());
        return sessionId;
    }

    private String credentialsJson() {
        return """
                {
                  "exchange": "testx",
                  "apiKey": "abcdefghwxyz",
                  "apiSecret": "secretabcdefgh",
                  "active": true
                }
                """;
    }

    @TestConfiguration
    static class LiveAdapterTestConfig {

        @Bean
        LiveExchangeAdapter testLiveExchangeAdapter() {
            return new LiveExchangeAdapter() {
                @Override
                public String exchange() {
                    return "testx";
                }

                @Override
                public Optional<BigDecimal> getLatestPrice(String symbol) {
                    return Optional.of(new BigDecimal("100.00000000"));
                }

                @Override
                public LiveOrderResult placeOrder(LiveOrderRequest orderRequest, ExchangeCredentials credentials) {
                    return new LiveOrderResult("testx-accepted", LiveOrderStatus.ACCEPTED, null, null);
                }

                @Override
                public void cancelOrder(String orderId, String symbol, ExchangeCredentials credentials) {
                }

                @Override
                public LiveOrderResult getOrder(String orderId, String symbol, ExchangeCredentials credentials) {
                    return new LiveOrderResult(orderId, LiveOrderStatus.ACCEPTED, null, null);
                }

                @Override
                public List<LiveOrderResult> getOpenOrders(String symbol, ExchangeCredentials credentials) {
                    return List.of();
                }

                @Override
                public List<ExchangePositionSnapshot> getPositions(ExchangeCredentials credentials) {
                    return List.of();
                }

                @Override
                public List<ExchangeBalanceSnapshot> getBalances(ExchangeCredentials credentials) {
                    return List.of();
                }

                @Override
                public boolean pingConnection() {
                    return true;
                }

                @Override
                public boolean validateCredentials(ExchangeCredentials credentials) {
                    return true;
                }
            };
        }
    }
}
