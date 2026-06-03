package com.example.back.runs.service;

import com.example.back.auth.security.AuthContext;
import com.example.back.artifacts.service.RunArtifactService;
import com.example.back.backtest.dto.BacktestTrade;
import com.example.back.backtest.dto.CreateBacktestRunRequest;
import com.example.back.backtest.dto.EquityPoint;
import com.example.back.backtest.exception.BacktestResourceNotFoundException;
import com.example.back.backtest.exception.BacktestValidationException;
import com.example.back.backtest.model.BacktestEquityPointEntity;
import com.example.back.backtest.model.BacktestStatus;
import com.example.back.backtest.model.BacktestTradeEntity;
import com.example.back.backtest.repository.BacktestEquityPointRepository;
import com.example.back.backtest.repository.BacktestTradeRepository;
import com.example.back.common.logging.LogContext;
import com.example.back.datasets.entity.DatasetEntity;
import com.example.back.datasets.entity.DatasetSnapshotEntity;
import com.example.back.datasets.repository.DatasetRepository;
import com.example.back.datasets.repository.DatasetSnapshotRepository;
import com.example.back.executionjobs.config.ExecutionJobProperties;
import com.example.back.executionjobs.entity.ExecutionJobEntity;
import com.example.back.executionjobs.service.ExecutionJobService;
import com.example.back.imports.client.PythonParserClient;
import com.example.back.runs.dto.PythonRunExecuteRequest;
import com.example.back.runs.dto.PythonRunExecuteResponse;
import com.example.back.runs.dto.RunDiagnosticsResponse;
import com.example.back.runs.entity.RunEntity;
import com.example.back.runs.entity.RunSnapshotEntity;
import com.example.back.runs.repository.RunRepository;
import com.example.back.runs.repository.RunSnapshotRepository;
import com.example.back.strategies.entity.StrategyFileEntity;
import com.example.back.strategies.entity.StrategyParameterPresetEntity;
import com.example.back.strategies.entity.StrategyVersionEntity;
import com.example.back.strategies.repository.StrategyFileRepository;
import com.example.back.strategies.repository.StrategyParameterPresetRepository;
import com.example.back.strategies.repository.StrategyVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunOrchestrationService {

    private static final String DEFAULT_ENGINE_VERSION = "python-execution-engine/0.9.6-alpha.1";
    private static final String PYTHON_EXECUTE_ENDPOINT = "/internal/runs/execute";
    private static final String STRATEGY_REPORT_SAFETY_NOTE = "This report is for alpha research and validation only. "
            + "It is not financial advice and does not certify production trading readiness.";

    private final RunRepository runRepository;
    private final RunSnapshotRepository runSnapshotRepository;
    private final StrategyFileRepository strategyFileRepository;
    private final StrategyVersionRepository strategyVersionRepository;
    private final StrategyParameterPresetRepository presetRepository;
    private final DatasetRepository datasetRepository;
    private final DatasetSnapshotRepository datasetSnapshotRepository;
    private final BacktestTradeRepository backtestTradeRepository;
    private final BacktestEquityPointRepository backtestEquityPointRepository;
    private final PythonParserClient pythonParserClient;
    private final RunFailureStateService runFailureStateService;
    private final RunArtifactService runArtifactService;
    private final ExecutionJobService executionJobService;
    private final ExecutionJobProperties executionJobProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public Long createRun(CreateBacktestRunRequest request) {
        validateTimeRange(request);
        Long userId = AuthContext.requireUserId();
        StrategyFileEntity strategy = getOwnedStrategy(request.getStrategyId(), userId);
        StrategyVersionEntity strategyVersion = resolveExecutableVersion(request, strategy, userId);
        StrategyParameterPresetEntity preset = resolvePreset(request, strategy.getId(), userId).orElse(null);
        Map<String, Object> effectiveParams = resolveEffectiveParams(request, preset);
        RunEntity run = buildRunEntity(request, strategy, strategyVersion, preset, userId, effectiveParams);
        RunEntity savedRun = runRepository.saveAndFlush(run);
        runSnapshotRepository.save(buildSnapshot(
                savedRun,
                request,
                strategyVersion,
                preset,
                userId,
                effectiveParams
        ));
        try (LogContext.BoundContext ignored = LogContext.bind(
                savedRun.getCorrelationId(),
                String.valueOf(savedRun.getId()))
        ) {
            log.info(
                    "Created run entity userId={} strategyId={} strategyVersionId={} parameterPresetId={}",
                    savedRun.getUserId(),
                    savedRun.getStrategyId(),
                    savedRun.getStrategyVersionId(),
                    savedRun.getParameterPresetId()
            );
        }
        markQueued(savedRun.getId());
        executionJobService.createQueuedJob(savedRun);
        return savedRun.getId();
    }

    public void executeJob(Long jobId) {
        ExecutionJobEntity job = executionJobService.findJob(jobId);
        RunEntity run = findRun(job.getRunId());
        StrategyFileEntity strategy = getOwnedStrategy(run.getStrategyId(), run.getUserId());
        StrategyVersionEntity strategyVersion = resolveExecutableVersion(run, strategy);

        try (LogContext.BoundContext ignored = LogContext.bind(
                run.getCorrelationId(),
                String.valueOf(run.getId()),
                String.valueOf(job.getId()))
        ) {
            if (executionJobService.isCancelRequested(job.getId())) {
                executionJobService.markCanceled(job.getId(), "Job was canceled before execution started");
                return;
            }

            markRunning(run.getId());

            long startedNanos = System.nanoTime();
            try {
                PythonRunExecuteResponse response = pythonParserClient.executeRun(
                        buildPythonRequest(run, strategy, strategyVersion, job)
                );
                if (executionJobService.isCancelRequested(job.getId())) {
                    executionJobService.markCanceled(job.getId(), "Job was canceled while Python execution was running");
                    return;
                }
                if (response == null) {
                    executionJobService.markFailed(
                            job.getId(),
                            "EMPTY_PYTHON_RESPONSE",
                            "Python execution returned empty response"
                    );
                    markFailed(
                            run.getId(),
                            "Python execution returned empty response",
                            writeJson(buildEmptyPythonResponseDetails(run, job))
                    );
                    return;
                }
                if (!Boolean.TRUE.equals(response.getSuccess())) {
                    String errorMessage = resolvePythonErrorMessage(response);
                    log.warn("Python execution reported failed run: {}", errorMessage);
                    executionJobService.markFailed(job.getId(), response.getErrorCode(), errorMessage);
                    markFailed(run.getId(), errorMessage, writeJson(buildPythonErrorDetails(run, job, response)));
                    return;
                }
                if (exceededMaxExecutionDuration(startedNanos)) {
                    String errorMessage = "Execution exceeded configured max duration";
                    executionJobService.markFailed(job.getId(), "EXECUTION_TIMEOUT", errorMessage);
                    markFailed(run.getId(), errorMessage, writeJson(buildTimeoutDetails(run, job)));
                    return;
                }
                markSucceeded(run.getId(), response);
                executionJobService.markSucceeded(job.getId());
            } catch (RuntimeException exception) {
                log.error("Run execution failed", exception);
                executionJobService.markFailed(job.getId(), "JAVA_EXECUTION_ERROR", exception.getMessage());
                markFailed(
                        run.getId(),
                        exception.getMessage(),
                        writeJson(buildJavaErrorDetails(run, job, exception))
                );
            }
        }
    }

    private void validateTimeRange(CreateBacktestRunRequest request) {
        if (!request.getFrom().isBefore(request.getTo())) {
            throw new BacktestValidationException("Field 'from' must be before 'to'");
        }
    }

    private StrategyFileEntity getOwnedStrategy(Long strategyId, Long userId) {
        StrategyFileEntity strategy = strategyFileRepository.findByIdAndUserId(strategyId, userId)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Strategy not found: " + strategyId));
        if (strategy.getLifecycleStatus() == StrategyFileEntity.StrategyLifecycleStatus.ARCHIVED) {
            throw new BacktestValidationException("Archived strategies cannot be executed");
        }
        return strategy;
    }

    private StrategyVersionEntity resolveExecutableVersion(
            CreateBacktestRunRequest request,
            StrategyFileEntity strategy,
            Long userId
    ) {
        StrategyVersionEntity version = null;
        if (request.getStrategyVersionId() != null) {
            version = strategyVersionRepository.findOwnedById(request.getStrategyVersionId(), userId)
                    .orElseThrow(() -> new BacktestResourceNotFoundException(
                            "Strategy version not found: " + request.getStrategyVersionId()
                    ));
            if (!strategy.getId().equals(version.getStrategyId())) {
                throw new BacktestValidationException("Strategy version does not belong to strategy");
            }
        } else if (strategy.getLatestVersionId() != null) {
            version = strategyVersionRepository.findOwnedById(strategy.getLatestVersionId(), userId)
                    .orElse(null);
        } else {
            version = strategyVersionRepository.findFirstByStrategyIdOrderByCreatedAtDesc(strategy.getId())
                    .orElse(null);
        }

        if (version == null) {
            validateLegacyStrategyStatus(strategy);
            return null;
        }

        validateVersionStatus(version);
        return version;
    }

    private StrategyVersionEntity resolveExecutableVersion(RunEntity run, StrategyFileEntity strategy) {
        StrategyVersionEntity version = null;
        if (run.getStrategyVersionId() != null) {
            version = strategyVersionRepository.findById(run.getStrategyVersionId())
                    .orElseThrow(() -> new BacktestResourceNotFoundException(
                            "Strategy version not found: " + run.getStrategyVersionId()
                    ));
        } else if (strategy.getLatestVersionId() != null) {
            version = strategyVersionRepository.findById(strategy.getLatestVersionId()).orElse(null);
        }

        if (version == null) {
            validateLegacyStrategyStatus(strategy);
            return null;
        }
        if (!strategy.getId().equals(version.getStrategyId())) {
            throw new BacktestValidationException("Run strategy version does not belong to strategy");
        }
        validateVersionStatus(version);
        return version;
    }

    private void validateVersionStatus(StrategyVersionEntity version) {
        if (version.getValidationStatus() != StrategyVersionEntity.ValidationStatus.VALID
                && version.getValidationStatus() != StrategyVersionEntity.ValidationStatus.WARNING) {
            throw new BacktestValidationException(
                    "Strategy version must be VALID before execution. Current status: "
                            + version.getValidationStatus()
            );
        }
    }

    private void validateLegacyStrategyStatus(StrategyFileEntity strategy) {
        if (strategy.getStatus() != StrategyFileEntity.StrategyStatus.VALID) {
            throw new BacktestValidationException(
                    "Strategy must be VALID before execution. Current status: " + strategy.getStatus()
            );
        }
    }

    private RunEntity buildRunEntity(
            CreateBacktestRunRequest request,
            StrategyFileEntity strategy,
            StrategyVersionEntity strategyVersion,
            StrategyParameterPresetEntity preset,
            Long userId,
            Map<String, Object> effectiveParams
    ) {
        RunEntity run = new RunEntity();
        run.setUserId(userId);
        run.setStrategyId(strategy.getId());
        run.setStrategyVersionId(strategyVersion == null ? null : strategyVersion.getId());
        run.setParameterPresetId(preset == null ? null : preset.getId());
        run.setStrategyName(strategy.getName() == null || strategy.getName().isBlank()
                ? strategy.getFileName()
                : strategy.getName().trim());
        run.setRunName(resolveRunName(request, strategy));
        run.setCorrelationId("run-" + UUID.randomUUID());
        run.setStatus(BacktestStatus.CREATED);
        run.setExchange(request.getExchange().trim().toLowerCase());
        run.setSymbol(request.getSymbol().trim().toUpperCase());
        run.setInterval(request.getInterval().trim());
        run.setDateFrom(request.getFrom());
        run.setDateTo(request.getTo());
        run.setDatasetId(findDataset(request, userId).map(DatasetEntity::getId).orElse(null));
        run.setParamsJson(writeJson(buildStoredConfig(request, strategyVersion, preset, effectiveParams)));
        run.setSummaryJson(null);
        run.setMetricsJson(null);
        run.setArtifactsJson(null);
        run.setErrorMessage(null);
        run.setErrorDetailsJson(null);
        run.setEngineVersion(DEFAULT_ENGINE_VERSION);
        run.setExecutionDurationMs(null);
        run.setStartedAt(null);
        run.setFinishedAt(null);
        return run;
    }

    private RunSnapshotEntity buildSnapshot(
            RunEntity run,
            CreateBacktestRunRequest request,
            StrategyVersionEntity strategyVersion,
            StrategyParameterPresetEntity preset,
            Long userId,
            Map<String, Object> effectiveParams
    ) {
        Optional<DatasetEntity> dataset = findDataset(request, userId);

        RunSnapshotEntity snapshot = new RunSnapshotEntity();
        DatasetEntity datasetEntity = dataset.orElse(null);
        snapshot.setRunId(run.getId());
        snapshot.setStrategyVersion(resolveStrategyVersion(run, strategyVersion));
        snapshot.setStrategyVersionId(strategyVersion == null ? null : strategyVersion.getId());
        snapshot.setDatasetVersion(resolveDatasetVersion(datasetEntity));
        snapshot.setDatasetSnapshotId(resolveDatasetSnapshotId(datasetEntity));
        snapshot.setParamsSnapshotJson(writeJson(effectiveParams));
        snapshot.setParameterPresetId(preset == null ? null : preset.getId());
        snapshot.setParameterPresetSnapshotJson(preset == null ? null : preset.getPresetPayload());
        snapshot.setExecutionConfigSnapshotJson(writeJson(buildExecutionConfigSnapshot(request)));
        snapshot.setMarketAssumptionsSnapshotJson(writeJson(buildMarketAssumptionsSnapshot(request)));
        snapshot.setEngineVersion(DEFAULT_ENGINE_VERSION);
        return snapshot;
    }

    private Long resolveDatasetSnapshotId(DatasetEntity dataset) {
        if (dataset == null) {
            return null;
        }
        return datasetSnapshotRepository
                .findByDatasetIdAndDatasetVersion(dataset.getId(), resolveDatasetVersion(dataset))
                .or(() -> datasetSnapshotRepository.findFirstByDatasetIdOrderByCreatedAtDesc(dataset.getId()))
                .map(DatasetSnapshotEntity::getId)
                .orElse(null);
    }

    private Optional<DatasetEntity> findDataset(CreateBacktestRunRequest request, Long userId) {
        return datasetRepository
                .findFirstByUserIdAndSourceIgnoreCaseAndSymbolIgnoreCaseAndIntervalAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByImportedAtDesc(
                        userId,
                        request.getExchange().trim(),
                        request.getSymbol().trim(),
                        request.getInterval().trim(),
                        request.getFrom(),
                        request.getTo()
                );
    }

    private Optional<StrategyParameterPresetEntity> resolvePreset(
            CreateBacktestRunRequest request,
            Long strategyId,
            Long userId
    ) {
        if (request.getParameterPresetId() == null) {
            return Optional.empty();
        }
        return Optional.of(presetRepository
                .findByIdAndStrategyIdAndUserId(request.getParameterPresetId(), strategyId, userId)
                .orElseThrow(() -> new BacktestResourceNotFoundException(
                        "Strategy parameter preset not found: " + request.getParameterPresetId()
                )));
    }

    private Map<String, Object> resolveEffectiveParams(
            CreateBacktestRunRequest request,
            StrategyParameterPresetEntity preset
    ) {
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            return request.getParams();
        }
        if (preset != null) {
            return readJsonMap(preset.getPresetPayload());
        }
        return Map.of();
    }

    private String resolveRunName(CreateBacktestRunRequest request, StrategyFileEntity strategy) {
        if (request.getRunName() != null && !request.getRunName().isBlank()) {
            return request.getRunName().trim();
        }
        return "%s %s %s %s".formatted(
                strategy.getName() == null || strategy.getName().isBlank() ? strategy.getFileName() : strategy.getName(),
                request.getExchange().trim().toLowerCase(),
                request.getSymbol().trim().toUpperCase(),
                request.getInterval().trim()
        );
    }

    private String resolveStrategyVersion(RunEntity run, StrategyVersionEntity strategyVersion) {
        if (strategyVersion != null) {
            return "strategy-%d/version-%s@sha256:%s".formatted(
                    strategyVersion.getStrategyId(),
                    strategyVersion.getVersion(),
                    strategyVersion.getChecksum()
            );
        }
        Instant createdAt = run.getCreatedAt();
        if (createdAt == null) {
            return "strategy-%d".formatted(run.getStrategyId());
        }
        return "strategy-%d@%s".formatted(run.getStrategyId(), createdAt.toString());
    }

    private String resolveDatasetVersion(DatasetEntity dataset) {
        if (dataset == null) {
            return "untracked";
        }
        if (dataset.getVersion() != null && !dataset.getVersion().isBlank()) {
            return dataset.getVersion();
        }
        if (dataset.getFingerprint() != null && !dataset.getFingerprint().isBlank()) {
            return dataset.getFingerprint();
        }
        return dataset.getId();
    }

    private Map<String, Object> buildStoredConfig(
            CreateBacktestRunRequest request,
            StrategyVersionEntity strategyVersion,
            StrategyParameterPresetEntity preset,
            Map<String, Object> effectiveParams
    ) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("strategyId", request.getStrategyId());
        config.put("strategyVersionId", strategyVersion == null ? null : strategyVersion.getId());
        config.put("strategyVersion", strategyVersion == null ? null : strategyVersion.getVersion());
        config.put("strategyChecksum", strategyVersion == null ? null : strategyVersion.getChecksum());
        config.put("parameterPresetId", preset == null ? null : preset.getId());
        config.put("runName", request.getRunName());
        config.put("exchange", request.getExchange().trim().toLowerCase());
        config.put("symbol", request.getSymbol().trim().toUpperCase());
        config.put("interval", request.getInterval().trim());
        config.put("from", request.getFrom());
        config.put("to", request.getTo());
        config.put("params", effectiveParams);
        config.put("initialCash", request.getInitialCash());
        config.put("feeRate", request.getFeeRate());
        config.put("slippageBps", request.getSlippageBps());
        config.put("strictData", request.getStrictData());
        config.put("positionSizingMode", request.getPositionSizingMode());
        return config;
    }

    private Map<String, Object> buildExecutionConfigSnapshot(CreateBacktestRunRequest request) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("mode", "queued-http");
        config.put("strict_data", request.getStrictData());
        config.put("from", request.getFrom());
        config.put("to", request.getTo());
        config.put("engine_transport", "python-parser-http");
        return config;
    }

    private Map<String, Object> buildMarketAssumptionsSnapshot(CreateBacktestRunRequest request) {
        Map<String, Object> assumptions = new LinkedHashMap<>();
        assumptions.put("commission_bps", request.getFeeRate() == null ? 0.0 : request.getFeeRate() * 10_000.0);
        assumptions.put("slippage_bps", request.getSlippageBps());
        assumptions.put("initial_balance", request.getInitialCash());
        assumptions.put("position_sizing_mode", request.getPositionSizingMode());
        assumptions.put("exchange", request.getExchange().trim().toLowerCase());
        assumptions.put("symbol", request.getSymbol().trim().toUpperCase());
        assumptions.put("timeframe", request.getInterval().trim());
        return assumptions;
    }

    private PythonRunExecuteRequest buildPythonRequest(
            RunEntity run,
            StrategyFileEntity strategy,
            StrategyVersionEntity strategyVersion,
            ExecutionJobEntity job
    ) {
        Map<String, Object> config = readJsonMap(run.getParamsJson());
        Map<String, Object> params = Map.of();
        Object rawParams = config.get("params");
        if (rawParams instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedParams = (Map<String, Object>) map;
            params = typedParams;
        }

        PythonRunExecuteRequest request = new PythonRunExecuteRequest();
        request.setStrategyFilePath(strategyVersion == null ? strategy.getStoragePath() : strategyVersion.getFilePath());
        request.setUserId(run.getUserId());
        request.setStrategyId(run.getStrategyId());
        request.setStrategyVersionId(strategyVersion == null ? null : strategyVersion.getId());
        request.setExchange(run.getExchange());
        request.setSymbol(run.getSymbol());
        request.setInterval(run.getInterval());
        request.setFrom(run.getDateFrom().toString());
        request.setTo(run.getDateTo().toString());
        request.setParams(params);
        request.setRunId(String.valueOf(run.getId()));
        request.setJobId(String.valueOf(job.getId()));
        request.setCorrelationId(run.getCorrelationId());
        return request;
    }

    @Transactional
    protected void markQueued(Long runId) {
        RunEntity run = findRun(runId);
        transitionStatus(run, BacktestStatus.QUEUED);
        runRepository.save(run);
    }

    @Transactional
    protected void markRunning(Long runId) {
        RunEntity run = findRun(runId);
        transitionStatus(run, BacktestStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(null);
        run.setExecutionDurationMs(null);
        run.setSummaryJson(null);
        run.setMetricsJson(null);
        run.setArtifactsJson(null);
        run.setErrorMessage(null);
        run.setErrorDetailsJson(null);
        runArtifactService.deleteArtifacts(runId);
        backtestTradeRepository.deleteByRunId(runId);
        backtestEquityPointRepository.deleteByRunId(runId);
        runRepository.save(run);
    }

    @Transactional
    protected void markSucceeded(Long runId, PythonRunExecuteResponse response) {
        RunEntity run = findRun(runId);
        transitionStatus(run, BacktestStatus.SUCCEEDED);
        validateResponseCorrelation(run, response);
        run.setFinishedAt(Instant.now());
        run.setExecutionDurationMs(resolveExecutionDurationMs(run.getStartedAt(), run.getFinishedAt()));
        run.setSummaryJson(writeJson(defaultMap(response.getSummary())));
        run.setMetricsJson(writeJson(defaultMap(response.getMetrics())));
        run.setArtifactsJson(writeJson(buildStoredArtifacts(runId, response)));
        run.setErrorMessage(null);
        run.setErrorDetailsJson(null);
        run.setEngineVersion(resolveEngineVersion(response.getEngineVersion()));
        runRepository.save(run);

        RunSnapshotEntity snapshot = runSnapshotRepository.findById(runId).orElse(null);
        if (snapshot != null) {
            snapshot.setEngineVersion(run.getEngineVersion());
            runSnapshotRepository.save(snapshot);
        }

        backtestTradeRepository.saveAll(defaultList(response.getTrades()).stream()
                .map(trade -> toTradeEntity(runId, trade))
                .toList());
        backtestEquityPointRepository.saveAll(defaultList(response.getEquityCurve()).stream()
                .map(point -> toEquityPointEntity(runId, point))
                .toList());
        runArtifactService.replaceArtifacts(
                runId,
                defaultMap(response.getSummary()),
                defaultMap(response.getMetrics()),
                defaultList(response.getTrades()),
                defaultList(response.getEquityCurve()),
                response.getDiagnostics(),
                buildRunReport(run, response)
        );

        log.info("Run execution completed successfully in {} ms", run.getExecutionDurationMs());
    }

    private Map<String, Object> buildRunReport(RunEntity run, PythonRunExecuteResponse response) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runId", run.getId());
        report.put("correlationId", run.getCorrelationId());
        report.put("status", run.getStatus());
        report.put("strategyId", run.getStrategyId());
        report.put("strategyVersionId", run.getStrategyVersionId());
        report.put("parameterPresetId", run.getParameterPresetId());
        report.put("strategyName", run.getStrategyName());
        report.put("datasetId", run.getDatasetId());
        report.put("exchange", run.getExchange());
        report.put("symbol", run.getSymbol());
        report.put("interval", run.getInterval());
        report.put("from", run.getDateFrom());
        report.put("to", run.getDateTo());
        report.put("engineVersion", resolveEngineVersion(response.getEngineVersion()));
        report.put("executionDurationMs", run.getExecutionDurationMs());
        report.put("generatedAt", Instant.now());
        report.put("summary", defaultMap(response.getSummary()));
        report.put("metrics", defaultMap(response.getMetrics()));
        report.put("diagnostics", response.getDiagnostics());
        report.put("warnings", response.getDiagnostics() == null ? List.of() : response.getDiagnostics().warnings());
        report.put("artifacts", defaultMap(response.getArtifacts()));
        report.put("safetyNote", STRATEGY_REPORT_SAFETY_NOTE);
        return report;
    }

    private Map<String, Object> buildStoredArtifacts(Long runId, PythonRunExecuteResponse response) {
        Map<String, Object> artifacts = new LinkedHashMap<>(defaultMap(response.getArtifacts()));
        RunDiagnosticsResponse diagnostics = response.getDiagnostics();
        if (diagnostics != null) {
            artifacts.put("diagnostics", diagnostics);
            artifacts.put("strategyReportArtifact", "strategy-report-%d.json".formatted(runId));
        }
        return artifacts;
    }

    private void markFailed(Long runId, String message, String errorDetailsJson) {
        try {
            runFailureStateService.markFailedInNewTransaction(runId, message, errorDetailsJson);
        } catch (RuntimeException ex) {
            log.error("Failed to persist FAILED status for run {}", runId, ex);
        }
    }

    private void transitionStatus(RunEntity run, BacktestStatus nextStatus) {
        try (LogContext.BoundContext ignored = LogContext.bind(run.getCorrelationId(), String.valueOf(run.getId()))) {
            BacktestStatus previousStatus = run.getStatus();
            run.setStatus(nextStatus);
            log.info("Run status transition {} -> {}", previousStatus, nextStatus);
        }
    }

    private void validateResponseCorrelation(RunEntity run, PythonRunExecuteResponse response) {
        String expectedRunId = String.valueOf(run.getId());
        if (response.getRunId() != null && !expectedRunId.equals(response.getRunId())) {
            log.warn("Python response returned mismatched run id: expected={}, actual={}", expectedRunId, response.getRunId());
        }
        if (response.getCorrelationId() != null && !run.getCorrelationId().equals(response.getCorrelationId())) {
            log.warn(
                    "Python response returned mismatched correlation id: expected={}, actual={}",
                    run.getCorrelationId(),
                    response.getCorrelationId()
            );
        }
    }

    private Map<String, Object> buildEmptyPythonResponseDetails(RunEntity run, ExecutionJobEntity job) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("source", "java");
        details.put("stage", "python-client");
        details.put("endpoint", PYTHON_EXECUTE_ENDPOINT);
        details.put("runId", run.getId());
        details.put("jobId", job.getId());
        details.put("attemptCount", job.getAttemptCount());
        details.put("correlationId", run.getCorrelationId());
        details.put("errorCode", "EMPTY_PYTHON_RESPONSE");
        details.put("errorMessage", "Python execution returned empty response");
        return details;
    }

    private Map<String, Object> buildPythonErrorDetails(
            RunEntity run,
            ExecutionJobEntity job,
            PythonRunExecuteResponse response
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("source", "python");
        details.put("endpoint", PYTHON_EXECUTE_ENDPOINT);
        details.put("runId", firstNonBlank(response.getRunId(), String.valueOf(run.getId())));
        details.put("jobId", firstNonBlank(response.getJobId(), String.valueOf(job.getId())));
        details.put("attemptCount", job.getAttemptCount());
        details.put("correlationId", firstNonBlank(response.getCorrelationId(), run.getCorrelationId()));
        details.put("errorCode", response.getErrorCode());
        details.put("errorMessage", resolvePythonErrorMessage(response));
        details.put("stacktrace", response.getStacktrace());
        details.put("startedAt", response.getStartedAt());
        details.put("finishedAt", response.getFinishedAt());
        details.put("executionDurationMs", response.getExecutionDurationMs());
        details.put("engineVersion", resolveEngineVersion(response.getEngineVersion()));
        return details;
    }

    private Map<String, Object> buildJavaErrorDetails(
            RunEntity run,
            ExecutionJobEntity job,
            RuntimeException exception
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("source", "java");
        details.put("stage", "python-client");
        details.put("endpoint", PYTHON_EXECUTE_ENDPOINT);
        details.put("runId", run.getId());
        details.put("jobId", job.getId());
        details.put("attemptCount", job.getAttemptCount());
        details.put("correlationId", run.getCorrelationId());
        details.put("errorCode", "JAVA_EXECUTION_ERROR");
        details.put("errorMessage", firstNonBlank(exception.getMessage(), "Run execution failed"));
        details.put("exceptionClass", exception.getClass().getName());
        details.put("stacktrace", stackTrace(exception));
        return details;
    }

    private Map<String, Object> buildTimeoutDetails(RunEntity run, ExecutionJobEntity job) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("source", "java");
        details.put("stage", "execution-guardrail");
        details.put("endpoint", PYTHON_EXECUTE_ENDPOINT);
        details.put("runId", run.getId());
        details.put("jobId", job.getId());
        details.put("attemptCount", job.getAttemptCount());
        details.put("correlationId", run.getCorrelationId());
        details.put("errorCode", "EXECUTION_TIMEOUT");
        details.put("errorMessage", "Execution exceeded configured max duration");
        details.put("maxExecutionDurationMs", executionJobProperties.getMaxExecutionDurationMs());
        return details;
    }

    private String resolvePythonErrorMessage(PythonRunExecuteResponse response) {
        return firstNonBlank(response.getErrorMessage(), response.getError());
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private Long resolveExecutionDurationMs(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    private boolean exceededMaxExecutionDuration(long startedNanos) {
        long maxDurationMs = executionJobProperties.getMaxExecutionDurationMs();
        if (maxDurationMs <= 0) {
            return false;
        }
        long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
        return durationMs > maxDurationMs;
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String resolveEngineVersion(String engineVersion) {
        if (engineVersion == null || engineVersion.isBlank()) {
            return DEFAULT_ENGINE_VERSION;
        }
        return engineVersion;
    }

    private RunEntity findRun(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Run not found: " + runId));
    }

    private BacktestTradeEntity toTradeEntity(Long runId, BacktestTrade trade) {
        BacktestTradeEntity entity = new BacktestTradeEntity();
        entity.setRunId(runId);
        entity.setEntryTime(trade.getEntryTime());
        entity.setExitTime(trade.getExitTime());
        entity.setEntryPrice(trade.getEntryPrice());
        entity.setExitPrice(trade.getExitPrice());
        entity.setQuantity(trade.getQuantity());
        entity.setPnl(trade.getPnl());
        entity.setFee(trade.getFee());
        return entity;
    }

    private BacktestEquityPointEntity toEquityPointEntity(Long runId, EquityPoint point) {
        BacktestEquityPointEntity entity = new BacktestEquityPointEntity();
        entity.setRunId(runId);
        entity.setTimestamp(point.getTimestamp());
        entity.setEquity(point.getEquity());
        return entity;
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse JSON payload", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON payload", exception);
        }
    }

    private Map<String, Object> defaultMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private <T> List<T> defaultList(List<T> value) {
        return value == null ? List.of() : value;
    }
}
