package com.example.back.imports.client;

import static net.logstash.logback.argument.StructuredArguments.entries;

import com.example.back.common.config.PythonClientConfig;
import com.example.back.common.logging.LogContext;
import com.example.back.imports.dto.ImportCandlesRequest;
import com.example.back.imports.dto.ImportCandlesResponse;
import com.example.back.imports.dto.PythonHealthResponse;
import com.example.back.imports.dto.PythonReadinessResponse;
import com.example.back.runs.dto.PythonRunExecuteRequest;
import com.example.back.runs.dto.PythonRunExecuteResponse;
import com.example.back.strategies.dto.StrategyValidationRequest;
import com.example.back.strategies.dto.StrategyValidationResponse;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class PythonParserClient {

    private static final String RUN_EXECUTE_URI = "/internal/runs/execute";
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private final RestClient restClient;
    private final String internalSecret;

    @Autowired
    public PythonParserClient(PythonClientConfig config) {
        this(config, RestClient.builder());
    }

    PythonParserClient(PythonClientConfig config, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(config.getBaseUrl())
                .build();
        this.internalSecret = config.getInternalSecret();
    }

    public ImportCandlesResponse importCandles(ImportCandlesRequest request) {
        return restClient.post()
                .uri("/internal/import/candles")
                .headers(this::applyInternalHeaders)
                .body(request)
                .retrieve()
                .body(ImportCandlesResponse.class);
    }

    public PythonHealthResponse getHealth() {
        return restClient.get()
                .uri("/health")
                .retrieve()
                .body(PythonHealthResponse.class);
    }

    public PythonReadinessResponse getReadiness() {
        return restClient.get()
                .uri("/readiness")
                .retrieve()
                .body(PythonReadinessResponse.class);
    }

    public StrategyValidationResponse validateStrategy(StrategyValidationRequest request) {
        return restClient.post()
                .uri("/internal/strategies/validate")
                .headers(this::applyInternalHeaders)
                .body(request)
                .retrieve()
                .body(StrategyValidationResponse.class);
    }

    public PythonRunExecuteResponse executeRun(PythonRunExecuteRequest request) {
        long startedNanos = System.nanoTime();
        try (LogContext.BoundContext ignored = LogContext.bind(
                request.getCorrelationId(),
                request.getRunId(),
                request.getJobId())
        ) {
            log.info("Dispatching python run request {}", entries(buildRequestLogPayload(request)));
            ResponseEntity<PythonRunExecuteResponse> responseEntity = restClient.post()
                    .uri(RUN_EXECUTE_URI)
                    .headers(headers -> applyHeaders(headers, request))
                    .body(request)
                    .retrieve()
                    .toEntity(PythonRunExecuteResponse.class);

            PythonRunExecuteResponse body = responseEntity.getBody();
            log.info("Received python run response {}", entries(buildResponseLogPayload(
                    responseEntity.getStatusCode().value(),
                    startedNanos,
                    body
            )));
            return body;
        } catch (RestClientResponseException exception) {
            log.error("Python run request failed {}", entries(buildErrorLogPayload(
                    startedNanos,
                    exception.getRawStatusCode(),
                    exception.getResponseBodyAsString()
            )), exception);
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Python run request failed {}", entries(buildErrorLogPayload(
                    startedNanos,
                    null,
                    exception.getMessage()
            )), exception);
            throw exception;
        }
    }

    private void applyHeaders(HttpHeaders headers, PythonRunExecuteRequest request) {
        applyInternalHeaders(headers);
        if (request.getCorrelationId() != null && !request.getCorrelationId().isBlank()) {
            headers.set(LogContext.CORRELATION_ID_HEADER, request.getCorrelationId());
        }
        if (request.getRunId() != null && !request.getRunId().isBlank()) {
            headers.set(LogContext.RUN_ID_HEADER, request.getRunId());
        }
        if (request.getJobId() != null && !request.getJobId().isBlank()) {
            headers.set(LogContext.JOB_ID_HEADER, request.getJobId());
        }
    }

    private void applyInternalHeaders(HttpHeaders headers) {
        headers.set(INTERNAL_AUTH_HEADER, internalSecret);
    }

    private Map<String, Object> buildRequestLogPayload(PythonRunExecuteRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "python_run_request");
        payload.put("endpoint", RUN_EXECUTE_URI);
        payload.put("user_id", request.getUserId());
        payload.put("strategy_id", request.getStrategyId());
        payload.put("strategy_version_id", request.getStrategyVersionId());
        payload.put("run_id", request.getRunId());
        payload.put("job_id", request.getJobId());
        payload.put("correlation_id", request.getCorrelationId());
        payload.put("strategy_file", safeFileName(request.getStrategyFilePath()));
        payload.put("exchange", request.getExchange());
        payload.put("symbol", request.getSymbol());
        payload.put("interval", request.getInterval());
        payload.put("from_time", request.getFrom());
        payload.put("to_time", request.getTo());
        payload.put("parameter_keys", request.getParams() == null ? List.of() : request.getParams().keySet().stream().sorted().toList());
        return payload;
    }

    private Map<String, Object> buildResponseLogPayload(
            int httpStatus,
            long startedNanos,
            PythonRunExecuteResponse response
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "python_run_response");
        payload.put("endpoint", RUN_EXECUTE_URI);
        payload.put("http_status", httpStatus);
        payload.put("execution_duration_ms", durationMs(startedNanos));
        if (response != null) {
            payload.put("job_id", response.getJobId());
            payload.put("success", response.getSuccess());
            payload.put("error_code", response.getErrorCode());
            payload.put("error_message", firstNonBlank(response.getErrorMessage(), response.getError()));
            payload.put("python_execution_duration_ms", response.getExecutionDurationMs());
        }
        return payload;
    }

    private Map<String, Object> buildErrorLogPayload(
            long startedNanos,
            Integer httpStatus,
            String responseBody
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "python_run_request_failed");
        payload.put("endpoint", RUN_EXECUTE_URI);
        payload.put("http_status", httpStatus);
        payload.put("execution_duration_ms", durationMs(startedNanos));
        payload.put("response_body", responseBody);
        return payload;
    }

    private long durationMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String safeFileName(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(filePath);
            Path fileName = path.getFileName();
            return fileName == null ? filePath : fileName.toString();
        } catch (InvalidPathException exception) {
            return filePath;
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
