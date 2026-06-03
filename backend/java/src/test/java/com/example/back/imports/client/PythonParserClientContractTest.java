package com.example.back.imports.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.back.common.config.PythonClientConfig;
import com.example.back.runs.dto.PythonRunExecuteRequest;
import com.example.back.runs.dto.PythonRunExecuteResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class PythonParserClientContractTest {

    @Test
    void executeRunSendsSharedSecretAndCorrelationHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PythonParserClient client = new PythonParserClient(config(), builder);

        server.expect(once(), requestTo("http://python-parser/internal/runs/execute"))
                .andExpect(header("X-Internal-Auth", "release-shared-secret"))
                .andExpect(header("X-Correlation-Id", "corr-1"))
                .andExpect(header("X-Run-Id", "run-1"))
                .andExpect(header("X-Job-Id", "job-1"))
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "summary": {"status": "ok"},
                          "metrics": {},
                          "trades": [],
                          "equityCurve": [],
                          "artifacts": null,
                          "diagnostics": {
                            "diagnosticsStatus": "mixed",
                            "diagnosticsSummary": "Backtest produced too few trades to evaluate stability.",
                            "totalPnL": 9.5,
                            "totalReturnPct": 0.1,
                            "risk": {
                              "maxDrawdown": 2.5,
                              "maxDrawdownPct": 1.2,
                              "drawdownStart": "2024-01-01T00:00:00Z",
                              "drawdownEnd": "2024-01-01T01:00:00Z",
                              "recoveryBars": 3
                            },
                            "trades": {
                              "tradeCount": 4,
                              "winningTrades": 2,
                              "losingTrades": 2,
                              "winRate": 50.0,
                              "profitFactor": 1.4,
                              "averageWin": 5.0,
                              "averageLoss": -3.5,
                              "bestTrade": 8.0,
                              "worstTrade": -5.0,
                              "longestWinStreak": 2,
                              "longestLossStreak": 1,
                              "averageTradePnl": 0.75,
                              "medianTradePnl": 1.0
                            },
                            "stability": {
                              "status": "mixed",
                              "segments": [
                                {
                                  "segmentIndex": 1,
                                  "from": "2024-01-01T00:00:00Z",
                                  "to": "2024-01-01T01:00:00Z",
                                  "pnl": 9.5,
                                  "tradeCount": 4,
                                  "maxDrawdown": 2.5,
                                  "status": "mixed"
                                }
                              ]
                            },
                            "warnings": [
                              {
                                "code": "LOW_TRADE_COUNT",
                                "message": "Backtest produced too few trades to evaluate stability.",
                                "severity": "warning"
                              }
                            ]
                          },
                          "engineVersion": "python-execution-engine/0.9.6-alpha.1",
                          "runId": "run-1",
                          "jobId": "job-1",
                          "correlationId": "corr-1",
                          "executionDurationMs": 12
                        }
                        """, MediaType.APPLICATION_JSON));

        PythonRunExecuteResponse response = client.executeRun(request());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getJobId()).isEqualTo("job-1");
        assertThat(response.getCorrelationId()).isEqualTo("corr-1");
        assertThat(response.getDiagnostics()).isNotNull();
        assertThat(response.getDiagnostics().trades().tradeCount()).isEqualTo(4);
        assertThat(response.getDiagnostics().warnings()).extracting("code").contains("LOW_TRADE_COUNT");
        server.verify();
    }

    @Test
    void healthUsesPublicEndpointWithoutInternalSharedSecret() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PythonParserClient client = new PythonParserClient(config(), builder);

        server.expect(once(), requestTo("http://python-parser/health"))
                .andExpect(headerDoesNotExist("X-Internal-Auth"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"service\":\"python-parser\"}", MediaType.APPLICATION_JSON));

        assertThat(client.getHealth().getStatus()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void executeRunSurfacesPythonErrorContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PythonParserClient client = new PythonParserClient(config(), builder);

        server.expect(once(), requestTo("http://python-parser/internal/runs/execute"))
                .andExpect(header("X-Internal-Auth", "release-shared-secret"))
                .andExpect(header("X-Correlation-Id", "corr-1"))
                .andExpect(header("X-Run-Id", "run-1"))
                .andExpect(header("X-Job-Id", "job-1"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"error\",\"message\":\"Unauthorized internal request\"}"));

        assertThatThrownBy(() -> client.executeRun(request()))
                .isInstanceOf(RestClientResponseException.class)
                .hasMessageContaining("400");
        server.verify();
    }

    private PythonClientConfig config() {
        PythonClientConfig config = new PythonClientConfig();
        ReflectionTestUtils.setField(config, "baseUrl", "http://python-parser");
        ReflectionTestUtils.setField(config, "internalSecret", "release-shared-secret");
        return config;
    }

    private PythonRunExecuteRequest request() {
        PythonRunExecuteRequest request = new PythonRunExecuteRequest();
        request.setUserId(1L);
        request.setStrategyId(2L);
        request.setStrategyVersionId(3L);
        request.setStrategyFilePath("/tmp/strategy.py");
        request.setExchange("binance");
        request.setSymbol("BTCUSDT");
        request.setInterval("1h");
        request.setFrom("2024-01-01T00:00:00Z");
        request.setTo("2024-01-02T00:00:00Z");
        request.setParams(Map.of("fast", 10));
        request.setRunId("run-1");
        request.setJobId("job-1");
        request.setCorrelationId("corr-1");
        return request;
    }
}
