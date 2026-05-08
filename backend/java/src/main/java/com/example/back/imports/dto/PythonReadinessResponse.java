package com.example.back.imports.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PythonReadinessResponse {
    private String status;
    private String service;
    private String parserVersion;
    private String database;
    private String engineVersion;
    private String internalAuthConfigured;
}
