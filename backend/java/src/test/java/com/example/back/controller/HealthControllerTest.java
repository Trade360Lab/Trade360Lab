package com.example.back.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.back.livetrading.config.LiveTradingProperties;
import com.example.back.imports.client.PythonParserClient;
import com.example.back.imports.dto.PythonHealthResponse;
import com.example.back.imports.dto.PythonReadinessResponse;
import java.math.BigDecimal;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private PythonParserClient pythonParserClient;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LiveTradingProperties liveTradingProperties = new LiveTradingProperties(
                false,
                "change-me-live-credential-encryption-key",
                new BigDecimal("100.00000000"),
                new BigDecimal("500.00000000"),
                new BigDecimal("1000.00000000"),
                3,
                10,
                null
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(
                pythonParserClient, dataSource, liveTradingProperties)).build();
    }

    @Test
    void healthReturnsJavaServiceStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ok")))
            .andExpect(jsonPath("$.service", is("java-api")));
    }

    @Test
    void pythonHealthReturnsDelegatedResponse() throws Exception {
        PythonHealthResponse response = new PythonHealthResponse();
        response.setStatus("ok");
        response.setService("python-parser");
        when(pythonParserClient.getHealth()).thenReturn(response);

        mockMvc.perform(get("/api/python/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service", is("python-parser")));
    }

    @Test
    void readinessReturnsDatabaseAndSafetyStatus() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        mockMvc.perform(get("/api/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ready")))
            .andExpect(jsonPath("$.apiVersion", is("0.9.5-alpha.1")))
            .andExpect(jsonPath("$.database", is("ok")))
            .andExpect(jsonPath("$.realOrderSubmissionEnabled", is(false)));
    }

    @Test
    void pythonReadinessReturnsDelegatedResponse() throws Exception {
        PythonReadinessResponse response = new PythonReadinessResponse();
        response.setStatus("ready");
        response.setService("python-parser");
        response.setParserVersion("0.9.5-alpha.1");
        response.setDatabase("ok");
        response.setEngineVersion("python-execution-engine/0.9.5-alpha.1");
        response.setInternalAuthConfigured("configured");
        when(pythonParserClient.getReadiness()).thenReturn(response);

        mockMvc.perform(get("/api/python/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service", is("python-parser")))
            .andExpect(jsonPath("$.database", is("ok")));
    }
}
