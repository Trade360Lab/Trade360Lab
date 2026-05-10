package com.example.back.controller;

import com.example.back.livetrading.config.LiveTradingProperties;
import com.example.back.imports.client.PythonParserClient;
import com.example.back.imports.dto.PythonHealthResponse;
import com.example.back.imports.dto.PythonReadinessResponse;
import com.example.back.release.ReleaseInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

@RestController
@Tag(
        name = "Health",
        description = "Проверка состояния сервисов системы"
)
public class HealthController {

    private final PythonParserClient pythonClient;
    private final DataSource dataSource;
    private final LiveTradingProperties liveTradingProperties;

    public HealthController(PythonParserClient pythonClient, DataSource dataSource, LiveTradingProperties liveTradingProperties) {
        this.pythonClient = pythonClient;
        this.dataSource = dataSource;
        this.liveTradingProperties = liveTradingProperties;
    }

    @Operation(
            summary = "Проверка Java API",
            description = "Возвращает статус текущего Java сервиса"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Сервис работает"),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера")
    })
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "java-api"
        );
    }

    @Operation(
            summary = "Проверка Python сервиса",
            description = "Проксирует health-check Python parser сервиса"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Python сервис доступен"),
            @ApiResponse(responseCode = "503", description = "Python сервис недоступен")
    })
    @GetMapping("/api/python/health")
    public PythonHealthResponse pythonHealth() {
        return pythonClient.getHealth();
    }

    @GetMapping("/api/readiness")
    public JavaReadinessResponse readiness() {
        String database = databaseStatus();
        boolean realSubmission = liveTradingProperties.realOrderSubmissionEnabled();
        return new JavaReadinessResponse(
                "ok".equals(database) && !realSubmission ? "ready" : "degraded",
                "java-api",
                ReleaseInfo.VERSION,
                database,
                "flyway-baseline-present",
                realSubmission ? "real-submission-enabled" : "guarded-alpha",
                realSubmission,
                realSubmission ? "real order submission enabled by configuration" : "real order submission disabled by default"
        );
    }

    @GetMapping("/api/python/readiness")
    public PythonReadinessResponse pythonReadiness() {
        return pythonClient.getReadiness();
    }

    private String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "ok" : "unavailable";
        } catch (SQLException exception) {
            return "unavailable";
        }
    }
}
