package com.example.back.artifacts.service;

import com.example.back.artifacts.dto.RunArtifactContentResponse;
import com.example.back.artifacts.dto.RunArtifactDownload;
import com.example.back.artifacts.dto.RunArtifactMetadataResponse;
import com.example.back.artifacts.entity.RunArtifactEntity;
import com.example.back.artifacts.repository.RunArtifactRepository;
import com.example.back.auth.security.AuthContext;
import com.example.back.backtest.exception.BacktestResourceNotFoundException;
import com.example.back.runs.repository.RunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RunArtifactService {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final RunArtifactRepository runArtifactRepository;
    private final RunRepository runRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<RunArtifactMetadataResponse> listArtifacts(Long runId) {
        requireOwnedRun(runId);
        return runArtifactRepository.findAllByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(this::toMetadataResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RunArtifactContentResponse getArtifact(Long runId, Long artifactId) {
        requireOwnedRun(runId);
        RunArtifactEntity artifact = findArtifact(runId, artifactId);
        return toContentResponse(artifact);
    }

    @Transactional(readOnly = true)
    public RunArtifactDownload downloadArtifact(Long runId, Long artifactId) {
        requireOwnedRun(runId);
        RunArtifactEntity artifact = findArtifact(runId, artifactId);
        String payload = artifact.getPayloadJson() == null ? "" : artifact.getPayloadJson();
        return new RunArtifactDownload(
                resolveFileName(artifact),
                artifact.getContentType(),
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Transactional
    public List<RunArtifactEntity> replaceArtifacts(
            Long runId,
            Object summary,
            Object metrics,
            Object trades,
            Object equityCurve,
            Object report
    ) {
        runArtifactRepository.deleteByRunId(runId);
        return runArtifactRepository.saveAll(List.of(
                buildArtifact(runId, "SUMMARY_JSON", "result_summary.json", summary),
                buildArtifact(runId, "METRICS_JSON", "metrics.json", metrics),
                buildArtifact(runId, "TRADES", "trades.json", trades),
                buildArtifact(runId, "EQUITY_CURVE", "equity_curve.json", equityCurve),
                buildArtifact(runId, "REPORT_JSON", "run_report.json", report),
                buildArtifact(runId, "STRATEGY_REPORT_JSON", "strategy-report-%d.json".formatted(runId), report)
        ));
    }

    @Transactional
    public void deleteArtifacts(Long runId) {
        runArtifactRepository.deleteByRunId(runId);
    }

    private void requireOwnedRun(Long runId) {
        Long userId = AuthContext.requireUserId();
        if (!runRepository.existsByIdAndUserId(runId, userId)) {
            throw new BacktestResourceNotFoundException("Run not found: " + runId);
        }
    }

    private RunArtifactEntity findArtifact(Long runId, Long artifactId) {
        return runArtifactRepository.findByIdAndRunId(artifactId, runId)
                .orElseThrow(() -> new BacktestResourceNotFoundException("Artifact not found: " + artifactId));
    }

    private RunArtifactEntity buildArtifact(Long runId, String type, String name, Object payload) {
        String json = writeJson(payload);
        RunArtifactEntity artifact = new RunArtifactEntity();
        artifact.setRunId(runId);
        artifact.setArtifactType(type);
        artifact.setArtifactName(name);
        artifact.setContentType(JSON_CONTENT_TYPE);
        artifact.setStoragePath(null);
        artifact.setPayloadJson(json);
        artifact.setSizeBytes((long) json.getBytes(StandardCharsets.UTF_8).length);
        return artifact;
    }

    private RunArtifactMetadataResponse toMetadataResponse(RunArtifactEntity artifact) {
        return RunArtifactMetadataResponse.builder()
                .id(artifact.getId())
                .runId(artifact.getRunId())
                .artifactType(artifact.getArtifactType())
                .artifactName(artifact.getArtifactName())
                .contentType(artifact.getContentType())
                .storagePath(artifact.getStoragePath())
                .sizeBytes(artifact.getSizeBytes())
                .createdAt(artifact.getCreatedAt())
                .build();
    }

    private RunArtifactContentResponse toContentResponse(RunArtifactEntity artifact) {
        return RunArtifactContentResponse.builder()
                .id(artifact.getId())
                .runId(artifact.getRunId())
                .artifactType(artifact.getArtifactType())
                .artifactName(artifact.getArtifactName())
                .contentType(artifact.getContentType())
                .storagePath(artifact.getStoragePath())
                .sizeBytes(artifact.getSizeBytes())
                .createdAt(artifact.getCreatedAt())
                .payload(readPayload(artifact.getPayloadJson()))
                .build();
    }

    private Object readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payloadJson, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse artifact payload", exception);
        }
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? List.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize artifact payload", exception);
        }
    }

    private String resolveFileName(RunArtifactEntity artifact) {
        String name = artifact.getArtifactName();
        if (name == null || name.isBlank()) {
            return "run-artifact-%d.json".formatted(artifact.getId());
        }
        return name;
    }
}
