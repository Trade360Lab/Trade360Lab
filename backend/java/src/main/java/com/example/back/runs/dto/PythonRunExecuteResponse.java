package com.example.back.runs.dto;

import com.example.back.backtest.dto.BacktestTrade;
import com.example.back.backtest.dto.EquityPoint;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Python execution response payload")
public class PythonRunExecuteResponse {

    @Schema(description = "Whether execution completed successfully", example = "true")
    private Boolean success;

    private Map<String, Object> metrics;

    private Map<String, Object> summary;

    @JsonProperty("equityCurve")
    private List<EquityPoint> equityCurve;

    private List<BacktestTrade> trades;

    private Map<String, Object> artifacts;

    private RunDiagnosticsResponse diagnostics;

    private String engineVersion;

    private String runId;

    private String jobId;

    private String correlationId;

    private String startedAt;

    private String finishedAt;

    private Long executionDurationMs;

    private String errorCode;

    private String errorMessage;

    private String stacktrace;

    @Schema(description = "Legacy error field", example = "Strategy execution failed")
    private String error;
}
