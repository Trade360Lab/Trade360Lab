package com.example.back.runs.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.back.artifacts.entity.RunArtifactEntity;
import com.example.back.artifacts.repository.RunArtifactRepository;
import com.example.back.backtest.dto.BacktestResult;
import com.example.back.backtest.dto.BacktestTrade;
import com.example.back.backtest.dto.EquityPoint;
import com.example.back.backtest.executor.PythonBacktestExecutor;
import com.example.back.backtest.model.BacktestStatus;
import com.example.back.candles.entity.CandleEntity;
import com.example.back.candles.repository.CandleRepository;
import com.example.back.datasets.entity.DatasetEntity;
import com.example.back.datasets.repository.DatasetRepository;
import com.example.back.executionjobs.entity.ExecutionJobEntity;
import com.example.back.executionjobs.entity.ExecutionJobStatus;
import com.example.back.executionjobs.repository.ExecutionJobRepository;
import com.example.back.executionjobs.service.ExecutionJobWorker;
import com.example.back.imports.client.PythonParserClient;
import com.example.back.runs.dto.RunDiagnosticsResponse;
import com.example.back.runs.dto.RunDiagnosticsRiskResponse;
import com.example.back.runs.dto.RunDiagnosticsStabilityResponse;
import com.example.back.runs.dto.RunDiagnosticsStabilitySegmentResponse;
import com.example.back.runs.dto.RunDiagnosticsTradesResponse;
import com.example.back.runs.dto.RunDiagnosticsWarningResponse;
import com.example.back.runs.dto.PythonRunExecuteResponse;
import com.example.back.runs.entity.RunEntity;
import com.example.back.runs.repository.RunRepository;
import com.example.back.strategies.entity.StrategyFileEntity;
import com.example.back.strategies.entity.StrategyVersionEntity;
import com.example.back.strategies.repository.StrategyFileRepository;
import com.example.back.strategies.repository.StrategyVersionRepository;
import com.example.back.support.TestAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:runs-controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=INTERVAL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "execution.jobs.worker-enabled=false"
})
class RunControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private RunArtifactRepository runArtifactRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutionJobRepository executionJobRepository;

    @Autowired
    private ExecutionJobWorker executionJobWorker;

    @Autowired
    private StrategyFileRepository strategyFileRepository;

    @Autowired
    private StrategyVersionRepository strategyVersionRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @MockBean
    private PythonBacktestExecutor pythonBacktestExecutor;

    @MockBean
    private PythonParserClient pythonParserClient;

    @MockBean
    private CandleRepository candleRepository;

    private Long strategyId;
    private Long strategyVersionId;

    @BeforeEach
    void setUp() {
        executionJobRepository.deleteAll();
        runArtifactRepository.deleteAll();
        runRepository.deleteAll();
        strategyVersionRepository.deleteAll();
        strategyFileRepository.deleteAll();
        datasetRepository.deleteAll();

        StrategyFileEntity strategy = new StrategyFileEntity();
        strategy.setUserId(TestAuth.USER_ID);
        strategy.setStrategyKey("ema");
        strategy.setName("EMA");
        strategy.setFileName("ema.py");
        strategy.setStoragePath("/tmp/ema.py");
        strategy.setStatus(StrategyFileEntity.StrategyStatus.VALID);
        strategy.setLifecycleStatus(StrategyFileEntity.StrategyLifecycleStatus.ACTIVE);
        StrategyFileEntity savedStrategy = strategyFileRepository.saveAndFlush(strategy);
        strategyId = savedStrategy.getId();

        StrategyVersionEntity version = new StrategyVersionEntity();
        version.setStrategyId(strategyId);
        version.setVersion("1");
        version.setFilePath("/tmp/ema.py");
        version.setFileName("ema.py");
        version.setContentType("text/x-python");
        version.setSizeBytes(100L);
        version.setChecksum("abc123");
        version.setValidationStatus(StrategyVersionEntity.ValidationStatus.VALID);
        version.setValidationReport("{\"status\":\"VALID\"}");
        version.setParametersSchemaJson("{\"fastPeriod\":{\"type\":\"integer\"}}");
        version.setExecutionEngineVersion("python-execution-engine/0.3.0-alpha.1");
        version.setCreatedBy(TestAuth.USER_ID);
        strategyVersionId = strategyVersionRepository.saveAndFlush(version).getId();
        savedStrategy.setLatestVersion("1");
        savedStrategy.setLatestVersionId(strategyVersionId);
        strategyFileRepository.saveAndFlush(savedStrategy);

        DatasetEntity dataset = new DatasetEntity();
        dataset.setId("dataset-1");
        dataset.setUserId(TestAuth.USER_ID);
        dataset.setName("Binance BTCUSDT 1h");
        dataset.setSource("binance");
        dataset.setSymbol("BTCUSDT");
        dataset.setInterval("1h");
        dataset.setImportedAt(Instant.parse("2024-01-05T00:00:00Z"));
        dataset.setRowsCount(100);
        dataset.setStartAt(Instant.parse("2024-01-01T00:00:00Z"));
        dataset.setEndAt(Instant.parse("2024-01-03T00:00:00Z"));
        dataset.setVersion("abc123");
        dataset.setFingerprint("abc123");
        dataset.setQualityFlagsJson("[]");
        dataset.setLineageJson("{\"rawRows\":100}");
        dataset.setPayload("""
                {"id":"dataset-1","name":"Binance BTCUSDT 1h","source":"binance","symbol":"BTCUSDT","timeframe":"1h"}
                """);
        datasetRepository.saveAndFlush(dataset);
    }

    @Test
    void getRunsReturnsSortedListWithFrontendContract() throws Exception {
        RunEntity completedRun = saveRun(
                BacktestStatus.SUCCEEDED,
                Instant.parse("2024-01-01T00:00:00Z"),
                "{\"fastPeriod\":10}",
                "{\"profit\":9.5}",
                null
        );
        RunEntity failedRun = saveRun(
                BacktestStatus.FAILED,
                Instant.parse("2024-01-02T00:00:00Z"),
                "{\"slowPeriod\":21}",
                null,
                "boom"
        );

        mockMvc.perform(get("/api/runs").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(failedRun.getId()))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].strategyName").value("EMA"))
                .andExpect(jsonPath("$[0].correlationId").isString())
                .andExpect(jsonPath("$[0].executionDurationMs").value(5000))
                .andExpect(jsonPath("$[1].id").value(completedRun.getId()))
                .andExpect(jsonPath("$[1].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[1].metrics.profit").value(9.5))
                .andExpect(jsonPath("$[1].parameters.fastPeriod").value(10))
                .andExpect(jsonPath("$[1].config.fastPeriod").value(10))
                .andExpect(jsonPath("$[1].runId").doesNotExist())
                .andExpect(jsonPath("$[1].summary").doesNotExist());
    }

    @Test
    void getRunByIdReturnsFrontendCompatibleFields() throws Exception {
        RunEntity run = saveRun(
                BacktestStatus.SUCCEEDED,
                Instant.parse("2024-01-01T00:00:00Z"),
                "{\"fastPeriod\":10}",
                "{\"profit\":9.5}",
                null
        );

        mockMvc.perform(get("/api/runs/" + run.getId()).with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(run.getId()))
                .andExpect(jsonPath("$.strategyId").value(strategyId))
                .andExpect(jsonPath("$.strategyVersionId").value(strategyVersionId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.strategyName").value("EMA"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(jsonPath("$.executionDurationMs").value(5000))
                .andExpect(jsonPath("$.metrics.profit").value(9.5))
                .andExpect(jsonPath("$.parameters.fastPeriod").value(10))
                .andExpect(jsonPath("$.config.fastPeriod").value(10))
                .andExpect(jsonPath("$.runId").doesNotExist())
                .andExpect(jsonPath("$.summary").doesNotExist());
    }

    @Test
    void getRunResultReturnsPersistedArtifacts() throws Exception {
        RunEntity run = saveRun(
                BacktestStatus.SUCCEEDED,
                Instant.parse("2024-01-01T00:00:00Z"),
                "{\"fastPeriod\":10}",
                "{\"profit\":9.5}",
                null
        );

        mockMvc.perform(get("/api/runs/" + run.getId() + "/result").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(run.getId()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.executionDurationMs").value(5000))
                .andExpect(jsonPath("$.metrics.profit").value(9.5));
    }

    @Test
    void getRunResultReturnsOptionalDiagnosticsAndSupportsOldResults() throws Exception {
        RunEntity oldRun = saveRun(
                BacktestStatus.SUCCEEDED,
                Instant.parse("2024-01-01T00:00:00Z"),
                "{\"fastPeriod\":10}",
                "{\"profit\":9.5}",
                null
        );
        mockMvc.perform(get("/api/runs/" + oldRun.getId() + "/result").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostics").doesNotExist());

        RunEntity run = saveRun(
                BacktestStatus.SUCCEEDED,
                Instant.parse("2024-01-02T00:00:00Z"),
                "{\"fastPeriod\":10}",
                "{\"profit\":9.5}",
                null
        );
        run.setArtifactsJson("""
                {
                  "tradesCount": 4,
                  "diagnostics": {
                    "diagnosticsStatus": "mixed",
                    "diagnosticsSummary": "Backtest produced too few trades to evaluate stability.",
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
                  }
                }
                """);
        runRepository.saveAndFlush(run);

        mockMvc.perform(get("/api/runs/" + run.getId() + "/result").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostics.diagnosticsStatus").value("mixed"))
                .andExpect(jsonPath("$.diagnostics.trades.tradeCount").value(4))
                .andExpect(jsonPath("$.diagnostics.risk.maxDrawdown").value(2.5))
                .andExpect(jsonPath("$.diagnostics.stability.segments[0].status").value("mixed"))
                .andExpect(jsonPath("$.diagnostics.warnings[0].code").value("LOW_TRADE_COUNT"));
    }

    @Test
    void getRunArtifactsReturnsOwnedArtifactMetadataAndPayload() throws Exception {
        RunEntity run = saveRun(
                BacktestStatus.SUCCEEDED,
                Instant.parse("2024-01-01T00:00:00Z"),
                "{\"fastPeriod\":10}",
                "{\"profit\":9.5}",
                null
        );
        RunArtifactEntity artifact = new RunArtifactEntity();
        artifact.setRunId(run.getId());
        artifact.setArtifactType("METRICS_JSON");
        artifact.setArtifactName("metrics.json");
        artifact.setContentType("application/json");
        artifact.setPayloadJson("{\"profit\":9.5}");
        artifact.setSizeBytes(14L);
        RunArtifactEntity savedArtifact = runArtifactRepository.saveAndFlush(artifact);

        mockMvc.perform(get("/api/runs/" + run.getId() + "/artifacts").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(savedArtifact.getId()))
                .andExpect(jsonPath("$[0].artifactType").value("METRICS_JSON"))
                .andExpect(jsonPath("$[0].artifactName").value("metrics.json"));

        mockMvc.perform(get("/api/runs/" + run.getId() + "/artifacts/" + savedArtifact.getId())
                        .with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.profit").value(9.5));
    }

    @Test
    void getRunByIdReturnsNotFoundForMissingRun() throws Exception {
        mockMvc.perform(get("/api/runs/999999").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isNotFound());
    }

    @Test
    void postRunCreatesAndReturnsFullRunObject() throws Exception {
        when(pythonParserClient.executeRun(any())).thenReturn(pythonRunExecuteResponse());

        String responseBody = mockMvc.perform(post("/api/runs")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "strategyId": %d,
                                  "exchange": "binance",
                                  "symbol": "BTCUSDT",
                                  "interval": "1h",
                                  "from": "2024-01-01T00:00:00Z",
                                  "to": "2024-01-01T01:00:00Z",
                                  "params": {
                                    "fastPeriod": 10
                                  },
                                  "initialCash": 10000.0,
                                  "feeRate": 0.001,
                                  "slippageBps": 1.0,
                                  "strictData": true
                                }
                                """.formatted(strategyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.strategyId").value(strategyId))
                .andExpect(jsonPath("$.strategyVersionId").value(strategyVersionId))
                .andExpect(jsonPath("$.snapshot.strategyVersionId").value(strategyVersionId))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.strategyName").value("EMA"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(jsonPath("$.parameters.fastPeriod").value(10))
                .andExpect(jsonPath("$.config.strategyId").value(strategyId))
                .andExpect(jsonPath("$.config.strategyVersionId").value(strategyVersionId))
                .andExpect(jsonPath("$.runId").doesNotExist())
                .andExpect(jsonPath("$.result").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long runId = objectMapper.readTree(responseBody).get("id").asLong();
        ExecutionJobEntity job = executionJobRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(job.getRunId()).isEqualTo(runId);
        org.assertj.core.api.Assertions.assertThat(job.getStatus()).isEqualTo(ExecutionJobStatus.QUEUED);
    }

    @Test
    void workerClaimsQueuedJobAndPersistsSuccessfulResult() throws Exception {
        when(pythonParserClient.executeRun(any())).thenReturn(pythonRunExecuteResponse());

        String responseBody = mockMvc.perform(post("/api/runs")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "strategyId": %d,
                                  "exchange": "binance",
                                  "symbol": "BTCUSDT",
                                  "interval": "1h",
                                  "from": "2024-01-01T00:00:00Z",
                                  "to": "2024-01-01T01:00:00Z",
                                  "params": {
                                    "fastPeriod": 10
                                  },
                                  "initialCash": 10000.0,
                                  "feeRate": 0.001,
                                  "slippageBps": 1.0,
                                  "strictData": true
                                }
                                """.formatted(strategyId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long runId = objectMapper.readTree(responseBody).get("id").asLong();

        org.assertj.core.api.Assertions.assertThat(executionJobWorker.processOneJobSync()).isTrue();

        mockMvc.perform(get("/api/runs/" + runId).with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.executionDurationMs").isNumber())
                .andExpect(jsonPath("$.metrics.profit").value(9.5))
                .andExpect(jsonPath("$.summary.profit").value(9.5))
                .andExpect(jsonPath("$.artifacts.tradesCount").value(1))
                .andExpect(jsonPath("$.diagnostics.diagnosticsStatus").value("mixed"))
                .andExpect(jsonPath("$.diagnostics.trades.tradeCount").value(1));

        org.assertj.core.api.Assertions.assertThat(runArtifactRepository.findAllByRunIdOrderByCreatedAtAsc(runId))
                .anySatisfy(artifact -> {
                    org.assertj.core.api.Assertions.assertThat(artifact.getArtifactType())
                            .isEqualTo("STRATEGY_REPORT_JSON");
                    org.assertj.core.api.Assertions.assertThat(artifact.getArtifactName())
                            .isEqualTo("strategy-report-%d.json".formatted(runId));
                    org.assertj.core.api.Assertions.assertThat(artifact.getPayloadJson())
                            .contains("This report is for alpha research and validation only");
                });

        ExecutionJobEntity job = executionJobRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(job.getStatus()).isEqualTo(ExecutionJobStatus.SUCCEEDED);
        org.assertj.core.api.Assertions.assertThat(job.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void postRunPersistsStructuredPythonFailure() throws Exception {
        PythonRunExecuteResponse response = new PythonRunExecuteResponse();
        response.setSuccess(false);
        response.setRunId("1");
        response.setCorrelationId("run-1");
        response.setStartedAt("2024-01-01T00:00:00Z");
        response.setFinishedAt("2024-01-01T00:00:01Z");
        response.setExecutionDurationMs(1000L);
        response.setEngineVersion("python-execution-engine/0.3.0-alpha.1");
        response.setErrorCode("STRATEGY_RUNTIME_ERROR");
        response.setErrorMessage("Strategy.run raised exception: boom");
        response.setStacktrace("ValueError: boom");
        response.setError("Strategy.run raised exception: boom");

        when(pythonParserClient.executeRun(any())).thenReturn(response);

        String responseBody = mockMvc.perform(post("/api/runs")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "strategyId": %d,
                                  "exchange": "binance",
                                  "symbol": "BTCUSDT",
                                  "interval": "1h",
                                  "from": "2024-01-01T00:00:00Z",
                                  "to": "2024-01-01T01:00:00Z",
                                  "params": {
                                    "fastPeriod": 10
                                  },
                                  "initialCash": 10000.0,
                                  "feeRate": 0.001,
                                  "slippageBps": 1.0,
                                  "strictData": true
                                }
                                """.formatted(strategyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long runId = objectMapper.readTree(responseBody).get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(executionJobWorker.processOneJobSync()).isTrue();

        mockMvc.perform(get("/api/runs/" + runId).with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Strategy.run raised exception: boom"))
                .andExpect(jsonPath("$.errorDetails.source").value("python"))
                .andExpect(jsonPath("$.errorDetails.jobId").isString())
                .andExpect(jsonPath("$.errorDetails.errorCode").value("STRATEGY_RUNTIME_ERROR"))
                .andExpect(jsonPath("$.errorDetails.stacktrace").value("ValueError: boom"))
                .andExpect(jsonPath("$.executionDurationMs").isNumber());
    }

    @Test
    void retryFailedRunRequeuesExistingJobAndIncrementsAttemptOnClaim() throws Exception {
        when(pythonParserClient.executeRun(any())).thenReturn(failedPythonRunExecuteResponse());

        String responseBody = createQueuedRun();
        Long runId = objectMapper.readTree(responseBody).get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(executionJobWorker.processOneJobSync()).isTrue();

        mockMvc.perform(post("/api/runs/" + runId + "/retry").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.attemptCount").value(1));

        when(pythonParserClient.executeRun(any())).thenReturn(pythonRunExecuteResponse());
        org.assertj.core.api.Assertions.assertThat(executionJobWorker.processOneJobSync()).isTrue();

        ExecutionJobEntity job = executionJobRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(job.getStatus()).isEqualTo(ExecutionJobStatus.SUCCEEDED);
        org.assertj.core.api.Assertions.assertThat(job.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void cancelQueuedRunMarksRunAndJobCanceled() throws Exception {
        String responseBody = createQueuedRun();
        Long runId = objectMapper.readTree(responseBody).get("id").asLong();

        mockMvc.perform(post("/api/runs/" + runId + "/cancel").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelRequested").value(true));

        mockMvc.perform(get("/api/runs/" + runId).with(TestAuth.authenticatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void executionJobDetailsRespectOwnership() throws Exception {
        RunEntity otherRun = saveRun(
                BacktestStatus.QUEUED,
                Instant.parse("2024-01-01T00:00:00Z"),
                "{\"fastPeriod\":10}",
                null,
                null
        );
        otherRun.setUserId(999L);
        runRepository.saveAndFlush(otherRun);

        ExecutionJobEntity job = new ExecutionJobEntity();
        job.setRunId(otherRun.getId());
        job.setUserId(999L);
        job.setStatus(ExecutionJobStatus.QUEUED);
        job.setPriority(0);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        ExecutionJobEntity savedJob = executionJobRepository.saveAndFlush(job);

        mockMvc.perform(get("/api/execution-jobs/" + savedJob.getId()).with(TestAuth.authenticatedRequest()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rerunReusesStoredConfiguration() throws Exception {
        RunEntity run = saveRun(
                BacktestStatus.SUCCEEDED,
                Instant.parse("2024-01-01T00:00:00Z"),
                """
                        {
                          "strategyId": %d,
                          "exchange": "binance",
                          "symbol": "BTCUSDT",
                          "interval": "1h",
                          "from": "2024-01-01T00:00:00Z",
                          "to": "2024-01-01T02:00:00Z",
                          "params": {
                            "fastPeriod": 10
                          },
                          "initialCash": 10000.0,
                          "feeRate": 0.001,
                          "slippageBps": 1.0,
                          "strictData": true
                        }
                        """.formatted(strategyId),
                "{\"profit\":9.5}",
                null
        );

        when(pythonParserClient.executeRun(any())).thenReturn(pythonRunExecuteResponse());

        mockMvc.perform(post("/api/runs/" + run.getId() + "/rerun").with(TestAuth.authenticatedRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.config.strategyId").value(strategyId))
                .andExpect(jsonPath("$.config.strategyVersionId").value(strategyVersionId))
                .andExpect(jsonPath("$.parameters.fastPeriod").value(10))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    private String createQueuedRun() throws Exception {
        return mockMvc.perform(post("/api/runs")
                        .with(TestAuth.authenticatedRequest())
                        .contentType("application/json")
                        .content("""
                                {
                                  "strategyId": %d,
                                  "exchange": "binance",
                                  "symbol": "BTCUSDT",
                                  "interval": "1h",
                                  "from": "2024-01-01T00:00:00Z",
                                  "to": "2024-01-01T01:00:00Z",
                                  "params": {
                                    "fastPeriod": 10
                                  },
                                  "initialCash": 10000.0,
                                  "feeRate": 0.001,
                                  "slippageBps": 1.0,
                                  "strictData": true
                                }
                                """.formatted(strategyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private RunEntity saveRun(
            BacktestStatus status,
            Instant createdAt,
            String paramsJson,
            String metricsJson,
            String errorMessage
    ) {
        RunEntity entity = new RunEntity();
        entity.setUserId(TestAuth.USER_ID);
        entity.setStrategyId(strategyId);
        entity.setStrategyVersionId(strategyVersionId);
        entity.setStrategyName("EMA");
        entity.setCorrelationId("run-" + (runRepository.count() + 1));
        entity.setStatus(status);
        entity.setExchange("binance");
        entity.setSymbol("BTCUSDT");
        entity.setInterval("1h");
        entity.setDateFrom(Instant.parse("2024-01-01T00:00:00Z"));
        entity.setDateTo(Instant.parse("2024-01-01T02:00:00Z"));
        entity.setParamsJson(paramsJson);
        entity.setMetricsJson(metricsJson);
        entity.setErrorMessage(errorMessage);
        entity.setErrorDetailsJson(errorMessage == null ? null : "{\"source\":\"python\"}");
        entity.setCreatedAt(createdAt);
        entity.setStartedAt(createdAt.plusSeconds(5));
        entity.setFinishedAt(createdAt.plusSeconds(10));
        entity.setExecutionDurationMs(5000L);
        return runRepository.saveAndFlush(entity);
    }

    private CandleEntity candle(String openTime) {
        Instant open = Instant.parse(openTime);
        return new CandleEntity(
                null,
                "binance",
                "BTCUSDT",
                "1h",
                open,
                open.plusSeconds(3600),
                new BigDecimal("1.0"),
                new BigDecimal("2.0"),
                new BigDecimal("0.5"),
                new BigDecimal("1.5"),
                new BigDecimal("10.0")
        );
    }

    private BacktestResult backtestResult() {
        BacktestTrade trade = new BacktestTrade();
        trade.setEntryTime(Instant.parse("2024-01-01T00:00:00Z"));
        trade.setExitTime(Instant.parse("2024-01-01T01:00:00Z"));
        trade.setEntryPrice(100.0);
        trade.setExitPrice(109.5);
        trade.setQuantity(1.0);
        trade.setPnl(9.5);
        trade.setFee(0.2);

        EquityPoint point = new EquityPoint();
        point.setTimestamp(Instant.parse("2024-01-01T01:00:00Z"));
        point.setEquity(10_009.5);

        BacktestResult result = new BacktestResult();
        result.setSummary(Map.of("profit", 9.5));
        result.setTrades(List.of(trade));
        result.setEquityCurve(List.of(point));
        result.setLogs(List.of("done"));
        result.setWarnings(List.of());
        return result;
    }

    private PythonRunExecuteResponse pythonRunExecuteResponse() {
        BacktestResult result = backtestResult();
        PythonRunExecuteResponse response = new PythonRunExecuteResponse();
        response.setSuccess(true);
        response.setSummary(Map.of("profit", 9.5));
        response.setMetrics(Map.of("profit", 9.5));
        response.setTrades(result.getTrades());
        response.setEquityCurve(result.getEquityCurve());
        response.setArtifacts(Map.of("tradesCount", 1, "equityPointCount", 1));
        response.setDiagnostics(diagnosticsResponse());
        response.setEngineVersion("python-execution-engine/0.3.0-alpha.1");
        response.setRunId("1");
        response.setCorrelationId("run-1");
        response.setStartedAt("2024-01-01T00:00:00Z");
        response.setFinishedAt("2024-01-01T00:00:01Z");
        response.setExecutionDurationMs(1000L);
        response.setError(null);
        return response;
    }

    private RunDiagnosticsResponse diagnosticsResponse() {
        return new RunDiagnosticsResponse(
                "mixed",
                "Backtest produced too few trades to evaluate stability.",
                9.5,
                0.1,
                new RunDiagnosticsRiskResponse(
                        2.5,
                        1.2,
                        "2024-01-01T00:00:00Z",
                        "2024-01-01T01:00:00Z",
                        3
                ),
                new RunDiagnosticsTradesResponse(
                        1,
                        1,
                        0,
                        100.0,
                        null,
                        9.5,
                        null,
                        9.5,
                        9.5,
                        1,
                        0,
                        9.5,
                        9.5,
                        0
                ),
                new RunDiagnosticsStabilityResponse(
                        List.of(new RunDiagnosticsStabilitySegmentResponse(
                                1,
                                "2024-01-01T00:00:00Z",
                                "2024-01-01T01:00:00Z",
                                9.5,
                                1,
                                2.5,
                                "mixed"
                        )),
                        "mixed"
                ),
                List.of(new RunDiagnosticsWarningResponse(
                        "LOW_TRADE_COUNT",
                        "Backtest produced too few trades to evaluate stability.",
                        "warning"
                ))
        );
    }

    private PythonRunExecuteResponse failedPythonRunExecuteResponse() {
        PythonRunExecuteResponse response = new PythonRunExecuteResponse();
        response.setSuccess(false);
        response.setRunId("1");
        response.setJobId("1");
        response.setCorrelationId("run-1");
        response.setStartedAt("2024-01-01T00:00:00Z");
        response.setFinishedAt("2024-01-01T00:00:01Z");
        response.setExecutionDurationMs(1000L);
        response.setEngineVersion("python-execution-engine/0.3.0-alpha.1");
        response.setErrorCode("STRATEGY_RUNTIME_ERROR");
        response.setErrorMessage("Strategy.run raised exception: boom");
        response.setStacktrace("ValueError: boom");
        response.setError("Strategy.run raised exception: boom");
        return response;
    }
}
