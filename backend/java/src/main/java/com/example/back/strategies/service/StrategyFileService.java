package com.example.back.strategies.service;

import static net.logstash.logback.argument.StructuredArguments.entries;

import com.example.back.auth.security.AuthContext;
import com.example.back.imports.client.PythonParserClient;
import com.example.back.strategies.dto.CreateStrategyPresetRequest;
import com.example.back.strategies.dto.CreateStrategyRequest;
import com.example.back.strategies.dto.StrategyPresetResponse;
import com.example.back.strategies.dto.StrategyResponse;
import com.example.back.strategies.dto.StrategyValidationRequest;
import com.example.back.strategies.dto.StrategyValidationResponse;
import com.example.back.strategies.dto.StrategyVersionResponse;
import com.example.back.strategies.dto.UpdateStrategyPresetRequest;
import com.example.back.strategies.dto.UpdateStrategyRequest;
import com.example.back.strategies.entity.StrategyFileEntity;
import com.example.back.strategies.entity.StrategyParameterPresetEntity;
import com.example.back.strategies.entity.StrategyVersionEntity;
import com.example.back.strategies.repository.StrategyFileRepository;
import com.example.back.strategies.repository.StrategyParameterPresetRepository;
import com.example.back.strategies.repository.StrategyVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyFileService {

    private static final String DEFAULT_STRATEGY_TYPE = "BACKTEST";
    private static final String DEFAULT_ENGINE_VERSION = "python-execution-engine/0.9.6-alpha.1";
    private static final Pattern STRATEGY_KEY_PATTERN = Pattern.compile("[^a-z0-9-]+");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final StrategyFileRepository strategyFileRepository;
    private final StrategyVersionRepository strategyVersionRepository;
    private final StrategyParameterPresetRepository presetRepository;
    private final PythonParserClient pythonParserClient;
    private final ObjectMapper objectMapper;

    @Value("${app.strategy-storage-path:./storage/strategies}")
    private String storagePath;

    @Transactional
    public StrategyResponse createStrategy(CreateStrategyRequest request) {
        Long userId = AuthContext.requireUserId();
        String strategyKey = resolveStrategyKey(userId, request.strategyKey(), request.name());

        StrategyFileEntity entity = new StrategyFileEntity();
        entity.setUserId(userId);
        entity.setStrategyKey(strategyKey);
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setStrategyType(firstNonBlank(request.strategyType(), DEFAULT_STRATEGY_TYPE));
        entity.setLifecycleStatus(StrategyFileEntity.StrategyLifecycleStatus.DRAFT);
        entity.setFileName(strategyKey + ".py");
        entity.setStoragePath("");
        entity.setStatus(StrategyFileEntity.StrategyStatus.PENDING);
        entity.setMetadataJson(writeJson(defaultMap(request.metadata())));
        entity.setTagsJson(writeJson(defaultList(request.tags())));
        StrategyFileEntity saved = strategyFileRepository.save(entity);
        log.info("Created strategy registry record {}", entries(strategyLogPayload(saved)));
        return StrategyResponse.fromEntity(saved);
    }

    @Transactional
    public StrategyResponse uploadStrategy(MultipartFile file) {
        validateFile(file);
        CreateStrategyRequest request = new CreateStrategyRequest(
                displayNameFromFile(file),
                null,
                null,
                DEFAULT_STRATEGY_TYPE,
                Map.of(),
                List.of(),
                null
        );
        StrategyFileEntity strategy = createStrategyEntity(AuthContext.requireUserId(), request);
        StrategyFileEntity savedStrategy = strategyFileRepository.saveAndFlush(strategy);
        createVersion(savedStrategy, file, true);
        return StrategyResponse.fromEntity(strategyFileRepository.findById(savedStrategy.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<StrategyResponse> getAllStrategies() {
        Long userId = AuthContext.requireUserId();
        return strategyFileRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(StrategyResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public StrategyResponse getStrategyById(Long id) {
        return StrategyResponse.fromEntity(findOwnedStrategy(id));
    }

    @Transactional
    public StrategyResponse updateStrategy(Long id, UpdateStrategyRequest request) {
        StrategyFileEntity strategy = findOwnedStrategy(id);
        if (request.name() != null && !request.name().isBlank()) {
            strategy.setName(request.name().trim());
        }
        if (request.description() != null) {
            strategy.setDescription(trimToNull(request.description()));
        }
        if (request.strategyType() != null && !request.strategyType().isBlank()) {
            strategy.setStrategyType(request.strategyType().trim());
        }
        if (request.lifecycleStatus() != null) {
            strategy.setLifecycleStatus(request.lifecycleStatus());
        }
        if (request.metadata() != null) {
            strategy.setMetadataJson(writeJson(request.metadata()));
        }
        if (request.tags() != null) {
            strategy.setTagsJson(writeJson(request.tags()));
        }
        StrategyFileEntity saved = strategyFileRepository.save(strategy);
        log.info("Updated strategy metadata {}", entries(strategyLogPayload(saved)));
        return StrategyResponse.fromEntity(saved);
    }

    @Transactional
    public StrategyResponse archiveStrategy(Long id) {
        StrategyFileEntity strategy = findOwnedStrategy(id);
        strategy.setLifecycleStatus(StrategyFileEntity.StrategyLifecycleStatus.ARCHIVED);
        StrategyFileEntity saved = strategyFileRepository.save(strategy);
        log.info("Archived strategy {}", entries(strategyLogPayload(saved)));
        return StrategyResponse.fromEntity(saved);
    }

    @Transactional
    public StrategyVersionResponse createStrategyVersion(Long strategyId, MultipartFile file) {
        StrategyFileEntity strategy = findOwnedStrategy(strategyId);
        if (strategy.getLifecycleStatus() == StrategyFileEntity.StrategyLifecycleStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archived strategies cannot receive versions");
        }
        StrategyVersionEntity version = createVersion(strategy, file, true);
        return StrategyVersionResponse.fromEntity(version);
    }

    @Transactional(readOnly = true)
    public List<StrategyVersionResponse> getStrategyVersions(Long strategyId) {
        Long userId = AuthContext.requireUserId();
        ensureOwnedStrategy(strategyId, userId);
        return strategyVersionRepository.findAllOwnedByStrategyId(strategyId, userId).stream()
                .map(StrategyVersionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public StrategyVersionResponse getStrategyVersion(Long versionId) {
        Long userId = AuthContext.requireUserId();
        return StrategyVersionResponse.fromEntity(findOwnedVersion(versionId, userId));
    }

    @Transactional
    public StrategyVersionResponse validateStrategyVersion(Long versionId) {
        Long userId = AuthContext.requireUserId();
        StrategyVersionEntity version = findOwnedVersion(versionId, userId);
        applyValidationResult(version, validateWithPython(version.getFilePath()));
        StrategyVersionEntity savedVersion = strategyVersionRepository.save(version);
        StrategyFileEntity strategy = strategyFileRepository.findById(version.getStrategyId()).orElseThrow();
        applyLatestValidationState(strategy, savedVersion);
        strategyFileRepository.save(strategy);
        log.info("Validated strategy version {}", entries(versionLogPayload(savedVersion, userId)));
        return StrategyVersionResponse.fromEntity(savedVersion);
    }

    @Transactional
    public StrategyVersionResponse activateStrategyVersion(Long versionId) {
        Long userId = AuthContext.requireUserId();
        StrategyVersionEntity version = findOwnedVersion(versionId, userId);
        if (version.getValidationStatus() == StrategyVersionEntity.ValidationStatus.INVALID
                || version.getValidationStatus() == StrategyVersionEntity.ValidationStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only VALID or WARNING strategy versions can be activated"
            );
        }

        StrategyFileEntity strategy = strategyFileRepository.findByIdAndUserId(version.getStrategyId(), userId)
                .orElseThrow(() -> notFound("Strategy not found: " + version.getStrategyId()));
        applyActiveVersion(strategy, version);
        strategy.setLifecycleStatus(StrategyFileEntity.StrategyLifecycleStatus.ACTIVE);
        strategyFileRepository.save(strategy);
        log.info("Activated strategy version {}", entries(versionLogPayload(version, userId)));
        return StrategyVersionResponse.fromEntity(version);
    }

    @Transactional
    public StrategyPresetResponse createPreset(Long strategyId, CreateStrategyPresetRequest request) {
        Long userId = AuthContext.requireUserId();
        ensureOwnedStrategy(strategyId, userId);
        validatePresetPayload(request.presetPayload());

        StrategyParameterPresetEntity preset = new StrategyParameterPresetEntity();
        preset.setStrategyId(strategyId);
        preset.setUserId(userId);
        preset.setName(request.name().trim());
        preset.setPresetPayload(writeJson(request.presetPayload()));
        StrategyParameterPresetEntity saved = presetRepository.save(preset);
        log.info("Created strategy parameter preset {}", entries(presetLogPayload(saved)));
        return StrategyPresetResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<StrategyPresetResponse> getPresets(Long strategyId) {
        Long userId = AuthContext.requireUserId();
        ensureOwnedStrategy(strategyId, userId);
        return presetRepository.findAllByStrategyIdAndUserIdOrderByCreatedAtDesc(strategyId, userId).stream()
                .map(StrategyPresetResponse::fromEntity)
                .toList();
    }

    @Transactional
    public StrategyPresetResponse updatePreset(Long presetId, UpdateStrategyPresetRequest request) {
        Long userId = AuthContext.requireUserId();
        StrategyParameterPresetEntity preset = presetRepository.findByIdAndUserId(presetId, userId)
                .orElseThrow(() -> notFound("Strategy parameter preset not found: " + presetId));
        ensureOwnedStrategy(preset.getStrategyId(), userId);
        if (request.name() != null && !request.name().isBlank()) {
            preset.setName(request.name().trim());
        }
        if (request.presetPayload() != null) {
            validatePresetPayload(request.presetPayload());
            preset.setPresetPayload(writeJson(request.presetPayload()));
        }
        StrategyParameterPresetEntity saved = presetRepository.save(preset);
        log.info("Updated strategy parameter preset {}", entries(presetLogPayload(saved)));
        return StrategyPresetResponse.fromEntity(saved);
    }

    @Transactional
    public void deletePreset(Long presetId) {
        Long userId = AuthContext.requireUserId();
        StrategyParameterPresetEntity preset = presetRepository.findByIdAndUserId(presetId, userId)
                .orElseThrow(() -> notFound("Strategy parameter preset not found: " + presetId));
        ensureOwnedStrategy(preset.getStrategyId(), userId);
        presetRepository.delete(preset);
        log.info("Deleted strategy parameter preset {}", entries(presetLogPayload(preset)));
    }

    public Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse strategy JSON payload", exception);
        }
    }

    private StrategyFileEntity createStrategyEntity(Long userId, CreateStrategyRequest request) {
        String strategyKey = resolveStrategyKey(userId, request.strategyKey(), request.name());
        StrategyFileEntity entity = new StrategyFileEntity();
        entity.setUserId(userId);
        entity.setStrategyKey(strategyKey);
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setStrategyType(firstNonBlank(request.strategyType(), DEFAULT_STRATEGY_TYPE));
        entity.setLifecycleStatus(StrategyFileEntity.StrategyLifecycleStatus.DRAFT);
        entity.setFileName(strategyKey + ".py");
        entity.setStoragePath("");
        entity.setStatus(StrategyFileEntity.StrategyStatus.PENDING);
        entity.setMetadataJson(writeJson(defaultMap(request.metadata())));
        entity.setTagsJson(writeJson(defaultList(request.tags())));
        return entity;
    }

    private StrategyVersionEntity createVersion(
            StrategyFileEntity strategy,
            MultipartFile file,
            boolean validateImmediately
    ) {
        StoredStrategyFile storedFile = storeFile(strategy.getUserId(), strategy.getId(), file);
        rejectDuplicateChecksum(strategy.getId(), storedFile.checksum());

        StrategyVersionEntity version = new StrategyVersionEntity();
        version.setStrategyId(strategy.getId());
        version.setVersion(nextVersion(strategy.getId()));
        version.setFilePath(storedFile.path().toString());
        version.setFileName(storedFile.fileName());
        version.setContentType(storedFile.contentType());
        version.setSizeBytes(storedFile.sizeBytes());
        version.setChecksum(storedFile.checksum());
        version.setValidationStatus(StrategyVersionEntity.ValidationStatus.PENDING);
        version.setValidationReport(writeJson(Map.of("status", "PENDING")));
        version.setExecutionEngineVersion(DEFAULT_ENGINE_VERSION);
        version.setCreatedBy(strategy.getUserId());

        StrategyVersionEntity savedVersion = strategyVersionRepository.saveAndFlush(version);
        if (validateImmediately) {
            applyValidationResult(savedVersion, validateWithPython(savedVersion.getFilePath()));
            savedVersion = strategyVersionRepository.saveAndFlush(savedVersion);
        }

        applyLatestValidationState(strategy, savedVersion);
        strategyFileRepository.save(strategy);
        log.info("Created strategy version {}", entries(versionLogPayload(savedVersion, strategy.getUserId())));
        return savedVersion;
    }

    private void rejectDuplicateChecksum(Long strategyId, String checksum) {
        Optional<StrategyVersionEntity> duplicate = strategyVersionRepository
                .findAllByStrategyIdOrderByCreatedAtDesc(strategyId)
                .stream()
                .filter(version -> checksum.equals(version.getChecksum()))
                .findFirst();
        if (duplicate.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Strategy version with the same checksum already exists: " + duplicate.get().getVersion()
            );
        }
    }

    private void applyValidationResult(
            StrategyVersionEntity version,
            StrategyValidationResponse validationResponse
    ) {
        StrategyVersionEntity.ValidationStatus status = resolveValidationStatus(validationResponse);
        version.setValidationStatus(status);
        version.setValidationReport(writeJson(buildValidationReport(validationResponse, status)));
        version.setParametersSchemaJson(writeJson(defaultMap(validationResponse.getParametersSchema())));
        version.setMetadataJson(writeJson(defaultMap(validationResponse.getMetadata())));
        version.setExecutionEngineVersion(firstNonBlank(validationResponse.getEngineVersion(), DEFAULT_ENGINE_VERSION));
    }

    private void applyLatestValidationState(StrategyFileEntity strategy, StrategyVersionEntity version) {
        strategy.setLatestVersion(version.getVersion());
        strategy.setLatestVersionId(version.getId());
        strategy.setFileName(version.getFileName());
        strategy.setStoragePath(version.getFilePath());
        String validatedName = resolveValidationName(version.getValidationReport());
        if (validatedName != null) {
            strategy.setName(validatedName);
        }
        strategy.setContentType(version.getContentType());
        strategy.setSizeBytes(version.getSizeBytes());
        strategy.setChecksum(version.getChecksum());
        strategy.setUploadedAt(version.getCreatedAt());
        strategy.setParametersSchemaJson(version.getParametersSchemaJson());
        if (version.getMetadataJson() != null && !version.getMetadataJson().isBlank()) {
            strategy.setMetadataJson(version.getMetadataJson());
        }

        if (version.getValidationStatus() == StrategyVersionEntity.ValidationStatus.VALID
                || version.getValidationStatus() == StrategyVersionEntity.ValidationStatus.WARNING) {
            strategy.setStatus(StrategyFileEntity.StrategyStatus.VALID);
            strategy.setValidationError(null);
            if (strategy.getLifecycleStatus() == StrategyFileEntity.StrategyLifecycleStatus.DRAFT) {
                strategy.setLifecycleStatus(StrategyFileEntity.StrategyLifecycleStatus.ACTIVE);
            }
            return;
        }

        strategy.setStatus(StrategyFileEntity.StrategyStatus.INVALID);
        strategy.setValidationError(resolveValidationError(version.getValidationReport()));
    }

    private void applyActiveVersion(StrategyFileEntity strategy, StrategyVersionEntity version) {
        applyLatestValidationState(strategy, version);
        strategy.setStatus(StrategyFileEntity.StrategyStatus.VALID);
        strategy.setValidationError(null);
    }

    private StrategyValidationResponse validateWithPython(String filePath) {
        try {
            StrategyValidationResponse response = pythonParserClient.validateStrategy(new StrategyValidationRequest(filePath));
            if (response == null) {
                return new StrategyValidationResponse(false, null, null, "Python validation returned empty response");
            }
            return response;
        } catch (RestClientException exception) {
            log.error("Python validation request failed for {}", filePath, exception);
            return new StrategyValidationResponse(false, null, null, "Python validation request failed");
        }
    }

    private StrategyVersionEntity.ValidationStatus resolveValidationStatus(StrategyValidationResponse response) {
        if (response.getValidationStatus() != null && !response.getValidationStatus().isBlank()) {
            try {
                return StrategyVersionEntity.ValidationStatus.valueOf(
                        response.getValidationStatus().trim().toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                log.warn("Unknown validation status from Python: {}", response.getValidationStatus());
            }
        }
        return Boolean.TRUE.equals(response.getValid())
                ? StrategyVersionEntity.ValidationStatus.VALID
                : StrategyVersionEntity.ValidationStatus.INVALID;
    }

    private Map<String, Object> buildValidationReport(
            StrategyValidationResponse response,
            StrategyVersionEntity.ValidationStatus status
    ) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", status.name());
        report.put("valid", Boolean.TRUE.equals(response.getValid()));
        report.put("name", response.getName());
        report.put("error", response.getError());
        report.put("engineVersion", firstNonBlank(response.getEngineVersion(), DEFAULT_ENGINE_VERSION));
        report.put("parametersSchema", defaultMap(response.getParametersSchema()));
        if (response.getValidationReport() != null) {
            report.put("details", response.getValidationReport());
        }
        return report;
    }

    private StoredStrategyFile storeFile(Long userId, Long strategyId, MultipartFile file) {
        validateFile(file);
        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String fileName = generateUniqueFileName(originalFileName);
        Path directory = Paths.get(storagePath, "user-" + userId, "strategy-" + strategyId).toAbsolutePath().normalize();
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid strategy filename");
        }

        try {
            Files.createDirectories(directory);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (InputStream inputStream = file.getInputStream();
                    DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                size = Files.copy(digestInputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String checksum = HexFormat.of().formatHex(digest.digest());
            return new StoredStrategyFile(
                    target,
                    fileName,
                    firstNonBlank(file.getContentType(), "text/x-python"),
                    size,
                    checksum
            );
        } catch (IOException exception) {
            log.error("Error while saving strategy file", exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".py")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must have .py extension");
        }
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required");
        }
        Path fileName = Path.of(originalFilename).getFileName();
        if (fileName == null || fileName.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required");
        }
        return fileName.toString().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String generateUniqueFileName(String originalFilename) {
        return Instant.now().toEpochMilli() + "_" + UUID.randomUUID() + "_" + originalFilename;
    }

    private String nextVersion(Long strategyId) {
        long next = strategyVersionRepository.findAllByStrategyIdOrderByCreatedAtDesc(strategyId).size() + 1L;
        return String.valueOf(next);
    }

    private StrategyFileEntity findOwnedStrategy(Long id) {
        Long userId = AuthContext.requireUserId();
        return strategyFileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> notFound("Strategy not found: " + id));
    }

    private void ensureOwnedStrategy(Long strategyId, Long userId) {
        strategyFileRepository.findByIdAndUserId(strategyId, userId)
                .orElseThrow(() -> notFound("Strategy not found: " + strategyId));
    }

    private StrategyVersionEntity findOwnedVersion(Long versionId, Long userId) {
        return strategyVersionRepository.findOwnedById(versionId, userId)
                .orElseThrow(() -> notFound("Strategy version not found: " + versionId));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private String resolveStrategyKey(Long userId, String requestedKey, String name) {
        String base = firstNonBlank(requestedKey, name);
        String candidate = STRATEGY_KEY_PATTERN.matcher(base.toLowerCase(Locale.ROOT).trim()).replaceAll("-");
        candidate = candidate.replaceAll("^-+", "").replaceAll("-+$", "");
        if (candidate.isBlank()) {
            candidate = "strategy";
        }
        String unique = candidate;
        int suffix = 2;
        while (strategyFileRepository.findByUserIdAndStrategyKey(userId, unique).isPresent()) {
            unique = candidate + "-" + suffix;
            suffix++;
        }
        return unique;
    }

    private String displayNameFromFile(MultipartFile file) {
        String fileName = file == null ? "Strategy" : sanitizeFileName(file.getOriginalFilename());
        String withoutExtension = fileName.replaceFirst("(?i)\\.py$", "");
        String spaced = withoutExtension.replace('_', ' ').replace('-', ' ').trim();
        return spaced.isBlank() ? "Strategy" : spaced;
    }

    private void validatePresetPayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preset payload is required");
        }
        try {
            objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preset payload must be valid JSON", exception);
        }
    }

    private String resolveValidationError(String validationReportJson) {
        Map<String, Object> report = readJsonMap(validationReportJson);
        Object error = report.get("error");
        if (error instanceof String message && !message.isBlank()) {
            return message;
        }
        return "Strategy validation failed";
    }

    private String resolveValidationName(String validationReportJson) {
        Map<String, Object> report = readJsonMap(validationReportJson);
        Object name = report.get("name");
        if (name instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize strategy JSON payload", exception);
        }
    }

    private Map<String, Object> strategyLogPayload(StrategyFileEntity strategy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "strategy_registry_event");
        payload.put("user_id", strategy.getUserId());
        payload.put("strategy_id", strategy.getId());
        payload.put("strategy_key", strategy.getStrategyKey());
        payload.put("lifecycle_status", strategy.getLifecycleStatus());
        payload.put("latest_version_id", strategy.getLatestVersionId());
        return payload;
    }

    private Map<String, Object> versionLogPayload(StrategyVersionEntity version, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "strategy_version_event");
        payload.put("user_id", userId);
        payload.put("strategy_id", version.getStrategyId());
        payload.put("strategy_version_id", version.getId());
        payload.put("version", version.getVersion());
        payload.put("validation_status", version.getValidationStatus());
        payload.put("checksum", version.getChecksum());
        return payload;
    }

    private Map<String, Object> presetLogPayload(StrategyParameterPresetEntity preset) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "strategy_parameter_preset_event");
        payload.put("user_id", preset.getUserId());
        payload.put("strategy_id", preset.getStrategyId());
        payload.put("preset_id", preset.getId());
        return payload;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Map<String, Object> defaultMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private List<String> defaultList(List<String> value) {
        return value == null ? List.of() : value;
    }

    private record StoredStrategyFile(
            Path path,
            String fileName,
            String contentType,
            Long sizeBytes,
            String checksum
    ) {
    }
}
