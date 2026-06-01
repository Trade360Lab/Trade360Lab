package com.example.back.runs.service;

import com.example.back.auth.security.AuthContext;
import com.example.back.backtest.dto.BacktestTrade;
import com.example.back.backtest.dto.CreateBacktestRunRequest;
import com.example.back.backtest.dto.EquityPoint;
import com.example.back.backtest.exception.BacktestResourceNotFoundException;
import com.example.back.backtest.model.BacktestEquityPointEntity;
import com.example.back.backtest.model.BacktestTradeEntity;
import com.example.back.backtest.repository.BacktestEquityPointRepository;
import com.example.back.backtest.repository.BacktestTradeRepository;
import com.example.back.executionjobs.dto.ExecutionJobResponse;
import com.example.back.executionjobs.service.ExecutionJobService;
import com.example.back.runs.dto.RunResultResponse;
import com.example.back.runs.dto.RunResponse;
import com.example.back.runs.entity.RunEntity;
import com.example.back.runs.entity.RunSnapshotEntity;
import com.example.back.runs.mapper.RunMapper;
import com.example.back.runs.repository.RunRepository;
import com.example.back.runs.repository.RunSnapshotRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunService {

    private final RunRepository runRepository;
    private final RunSnapshotRepository runSnapshotRepository;
    private final BacktestTradeRepository backtestTradeRepository;
    private final BacktestEquityPointRepository backtestEquityPointRepository;
    private final RunMapper runMapper;
    private final RunOrchestrationService runOrchestrationService;
    private final ExecutionJobService executionJobService;

    public List<RunResponse> listRuns() {
        Long userId = AuthContext.requireUserId();
        return runRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public RunResponse getRun(Long runId) {
        return toResponse(findRunEntity(runId));
    }

    public RunResponse createRun(CreateBacktestRunRequest request) {
        Long runId = runOrchestrationService.createRun(request);
        return getRun(runId);
    }

    public RunResponse rerun(Long runId) {
        RunEntity sourceRun = findRunEntity(runId);
        Map<String, Object> config = runMapper.toResponse(
                sourceRun,
                runSnapshotRepository.findById(sourceRun.getId()).orElse(null)
        ).config();

        CreateBacktestRunRequest request = new CreateBacktestRunRequest();
        request.setStrategyId(sourceRun.getStrategyId());
        request.setStrategyVersionId(sourceRun.getStrategyVersionId());
        request.setParameterPresetId(sourceRun.getParameterPresetId());
        request.setRunName(sourceRun.getRunName());
        request.setExchange(sourceRun.getExchange());
        request.setSymbol(sourceRun.getSymbol());
        request.setInterval(sourceRun.getInterval());
        request.setFrom(sourceRun.getDateFrom());
        request.setTo(sourceRun.getDateTo());
        request.setInitialCash(asDouble(config.get("initialCash"), 10_000.0));
        request.setFeeRate(asDouble(config.get("feeRate"), 0.0));
        request.setSlippageBps(asDouble(config.get("slippageBps"), 0.0));
        request.setStrictData(asBoolean(config.get("strictData"), true));
        request.setPositionSizingMode(asString(config.get("positionSizingMode"), "UNSPECIFIED"));
        request.setParams(getParameters(config));

        Long rerunId = runOrchestrationService.createRun(request);
        return getRun(rerunId);
    }

    public ExecutionJobResponse getRunExecution(Long runId) {
        return executionJobService.getLatestOwnedRunJob(runId);
    }

    public ExecutionJobResponse retryRun(Long runId) {
        findRunEntity(runId);
        return executionJobService.retryRun(runId);
    }

    public ExecutionJobResponse cancelRun(Long runId) {
        findRunEntity(runId);
        return executionJobService.cancelRun(runId);
    }

    public RunResultResponse getRunResult(Long runId) {
        RunEntity run = findRunEntity(runId);
        List<BacktestTrade> trades = backtestTradeRepository.findByRunIdOrderByEntryTimeAsc(runId).stream()
                .map(this::toTrade)
                .toList();
        List<EquityPoint> equityCurve = backtestEquityPointRepository.findByRunIdOrderByTimestampAsc(runId).stream()
                .map(this::toEquityPoint)
                .toList();

        RunResponse response = toResponse(run);
        return RunResultResponse.builder()
                .runId(runId)
                .status(response.status())
                .engineVersion(response.engineVersion())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .executionDurationMs(response.executionDurationMs())
                .summary(response.summary())
                .metrics(response.metrics())
                .artifacts(response.artifacts())
                .diagnostics(response.diagnostics())
                .trades(trades)
                .equityCurve(equityCurve)
                .errorMessage(run.getErrorMessage())
                .errorDetails(response.errorDetails())
                .build();
    }

    private RunEntity findRunEntity(Long runId) {
        Long userId = AuthContext.requireUserId();
        return runRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Run not found: " + runId));
    }

    private RunResponse toResponse(RunEntity runEntity) {
        RunSnapshotEntity snapshot = runSnapshotRepository.findById(runEntity.getId()).orElse(null);
        return runMapper.toResponse(runEntity, snapshot);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getParameters(Map<String, Object> config) {
        Object params = config.get("params");
        if (params instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Double asDouble(Object value, Double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private Boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return fallback;
    }

    private String asString(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private BacktestTrade toTrade(BacktestTradeEntity entity) {
        BacktestTrade trade = new BacktestTrade();
        trade.setEntryTime(entity.getEntryTime());
        trade.setExitTime(entity.getExitTime());
        trade.setEntryPrice(entity.getEntryPrice());
        trade.setExitPrice(entity.getExitPrice());
        trade.setQuantity(entity.getQuantity());
        trade.setPnl(entity.getPnl());
        trade.setFee(entity.getFee());
        return trade;
    }

    private EquityPoint toEquityPoint(BacktestEquityPointEntity entity) {
        EquityPoint point = new EquityPoint();
        point.setTimestamp(entity.getTimestamp());
        point.setEquity(entity.getEquity());
        return point;
    }
}
