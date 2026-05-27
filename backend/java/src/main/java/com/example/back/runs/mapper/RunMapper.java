package com.example.back.runs.mapper;

import com.example.back.backtest.model.BacktestStatus;
import com.example.back.runs.dto.RunDiagnosticsResponse;
import com.example.back.runs.dto.RunResponse;
import com.example.back.runs.dto.RunStatusResponse;
import com.example.back.runs.entity.RunEntity;
import com.example.back.runs.entity.RunSnapshotEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public RunResponse toResponse(RunEntity entity, RunSnapshotEntity snapshotEntity) {
        Map<String, Object> config = readJsonMap(entity.getParamsJson());
        Map<String, Object> artifacts = readNullableJsonMap(entity.getArtifactsJson());
        return RunResponse.builder()
                .id(entity.getId())
                .runName(entity.getRunName())
                .strategyId(entity.getStrategyId())
                .strategyVersionId(entity.getStrategyVersionId())
                .parameterPresetId(entity.getParameterPresetId())
                .strategyName(entity.getStrategyName())
                .datasetId(entity.getDatasetId())
                .correlationId(entity.getCorrelationId())
                .status(toExternalStatus(entity.getStatus()))
                .exchange(entity.getExchange())
                .symbol(entity.getSymbol())
                .interval(entity.getInterval())
                .from(entity.getDateFrom())
                .to(entity.getDateTo())
                .createdAt(entity.getCreatedAt())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .executionDurationMs(resolveExecutionDurationMs(entity))
                .engineVersion(entity.getEngineVersion())
                .config(config)
                .snapshot(toSnapshot(snapshotEntity))
                .parameters(readParameters(config))
                .summary(readNullableJsonMap(entity.getSummaryJson()))
                .metrics(readNullableJsonMap(entity.getMetricsJson()))
                .artifacts(artifacts)
                .diagnostics(readDiagnostics(artifacts))
                .errorMessage(entity.getErrorMessage())
                .errorDetails(readNullableJsonMap(entity.getErrorDetailsJson()))
                .build();
    }

    private RunStatusResponse toExternalStatus(BacktestStatus status) {
        if (status == null) {
            return RunStatusResponse.FAILED;
        }

        return switch (status) {
            case CREATED -> RunStatusResponse.CREATED;
            case QUEUED -> RunStatusResponse.QUEUED;
            case RUNNING -> RunStatusResponse.RUNNING;
            case SUCCEEDED -> RunStatusResponse.SUCCEEDED;
            case FAILED -> RunStatusResponse.FAILED;
            case CANCELED -> RunStatusResponse.CANCELED;
        };
    }

    private Map<String, Object> toSnapshot(RunSnapshotEntity entity) {
        if (entity == null) {
            return null;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("strategyVersion", entity.getStrategyVersion());
        snapshot.put("strategyVersionId", entity.getStrategyVersionId());
        snapshot.put("datasetVersion", entity.getDatasetVersion());
        snapshot.put("datasetSnapshotId", entity.getDatasetSnapshotId());
        snapshot.put("paramsSnapshot", readJsonMap(entity.getParamsSnapshotJson()));
        snapshot.put("parameterPresetId", entity.getParameterPresetId());
        snapshot.put("parameterPresetSnapshot", readNullableJsonMap(entity.getParameterPresetSnapshotJson()));
        snapshot.put("executionConfigSnapshot", readJsonMap(entity.getExecutionConfigSnapshotJson()));
        snapshot.put("marketAssumptionsSnapshot", readJsonMap(entity.getMarketAssumptionsSnapshotJson()));
        snapshot.put("engineVersion", entity.getEngineVersion());
        return snapshot;
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            log.warn("Failed to parse run JSON payload: {}", json, ex);
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> readNullableJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return readJsonMap(json);
    }

    private RunDiagnosticsResponse readDiagnostics(Map<String, Object> artifacts) {
        if (artifacts == null) {
            return null;
        }
        Object diagnostics = artifacts.get("diagnostics");
        if (diagnostics == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(diagnostics, RunDiagnosticsResponse.class);
        } catch (IllegalArgumentException ex) {
            log.warn("Failed to parse run diagnostics payload: {}", diagnostics, ex);
            return null;
        }
    }

    private Map<String, Object> readParameters(Map<String, Object> payload) {
        Object nestedParams = payload.get("params");
        if (nestedParams instanceof Map<?, ?> params) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedParams = (Map<String, Object>) params;
            return typedParams;
        }
        return payload;
    }

    private Long resolveExecutionDurationMs(RunEntity entity) {
        if (entity.getExecutionDurationMs() != null) {
            return entity.getExecutionDurationMs();
        }
        if (entity.getStartedAt() == null || entity.getFinishedAt() == null) {
            return null;
        }
        return entity.getFinishedAt().toEpochMilli() - entity.getStartedAt().toEpochMilli();
    }
}
