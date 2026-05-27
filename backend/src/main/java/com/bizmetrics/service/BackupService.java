package com.bizmetrics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.identity.invitation.domain.InvitationTokenDigest;
import com.bizmetrics.platform.audit.application.AuditTrailService;
import com.bizmetrics.service.support.AccessControlService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BackupService {
    private static final String FORMAT = "bizmetrics-structured-backup";
    private static final String LEGACY_FORMAT = "bizmetrics-structured-backup";
    private static final String VERSION = "2.2";
    private static final String PREVIOUS_VERSION = "2.1";
    private static final String LEGACY_VERSION = "2.0";
    private static final Set<String> SUPPORTED_VERSIONS = Set.of(LEGACY_VERSION, PREVIOUS_VERSION, VERSION);
    private static final Set<String> LEGACY_OPTIONAL_TABLES = Set.of(
        "company_memberships",
        "budget_reservations"
    );
    private static final String RESTORE_CONFIRMATION = "RESTORE";
    private static final int MAX_BACKUP_BYTES = 50 * 1024 * 1024;

    /**
     * Business data included in the application-level portable snapshot.
     * Object payloads remain in MinIO and are protected by the production
     * postgres + MinIO backup scripts. Sessions, login throttles, delivery
     * attempts, outbox leases and scheduled-job leases are deliberately
     * excluded because replaying ephemeral control state is unsafe.
     */
    private static final List<String> BACKUP_TABLES = List.of(
        "users",
        "registration_invites",
        "companies",
        "departments",
        "company_memberships",
        "employees",
        "employee_certificates",
        "employee_experiences",
        "employment_events",
        "ledgers",
        "ledger_members",
        "accounts",
        "account_reconciliations",
        "categories",
        "budgets",
        "transactions",
        "budget_reservations",
        "recurring_items",
        "tax_items",
        "entity_transfers",
        "receipt_vouchers",
        "receipt_file_hashes",
        "payroll_runs",
        "payroll_run_items",
        "audit_logs",
        "notifications",
        "notification_preferences",
        "approval_requests",
        "approval_actions"
    );

    private static final List<String> RESET_ONLY_TABLES = List.of(
        "auth_tokens",
        "login_failure_states",
        "notification_deliveries",
        "outbox_events",
        "scheduled_job_leases"
    );

    private final AuditTrailService auditTrail;
    private final AccessControlService accessControl;
    private final BackupMapper mapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate snapshotTransaction;

    public BackupService(
        AuditTrailService auditTrail,
        AccessControlService accessControl,
        BackupMapper mapper,
        PlatformTransactionManager transactionManager
    ) {
        this.auditTrail = auditTrail;
        this.accessControl = accessControl;
        this.mapper = mapper;
        this.snapshotTransaction = new TransactionTemplate(transactionManager);
        this.snapshotTransaction.setReadOnly(true);
        this.snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_LONG_FOR_INTS);
    }

    public Map<String, Integer> status(String authorization) {
        accessControl.requireAdmin(authorization);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("users", count("users"));
        counts.put("accounts", count("accounts"));
        counts.put("categories", count("categories"));
        counts.put("transactions", count("transactions"));
        counts.put("budgets", count("budgets"));
        counts.put("ledgers", count("ledgers"));
        counts.put("employees", count("employees"));
        counts.put("taxItems", count("tax_items"));
        counts.put("receipts", count("receipt_vouchers"));
        counts.put("payrollRuns", count("payroll_runs"));
        counts.put("notifications", count("notifications"));
        counts.put("datasets", BACKUP_TABLES.size());
        return counts;
    }

    public ResponseEntity<Map<String, Object>> export(String authorization) {
        User user = accessControl.requireAdmin(authorization);
        Map<String, Object> data = snapshotTransaction.execute(status -> readDatasets());
        if (data == null) {
            throw new IllegalStateException("Failed to create a consistent backup snapshot");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", FORMAT);
        payload.put("version", VERSION);
        payload.put("exportedAt", OffsetDateTime.now().toString());
        payload.put("scope", "all-business-data");
        payload.put("objectStorage", Map.of(
            "included", false,
            "message", "Attachment metadata is included; MinIO object bytes require the production backup archive."
        ));
        payload.put("counts", datasetCounts(data));
        payload.put("data", data);
        payload.put("checksum", checksum(data));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("bizmetrics-structured-backup-" + java.time.LocalDate.now() + ".json")
            .build());
        auditTrail.record(0, "backup", 0, "export", "导出全量结构化经营数据", user.id, user.nickname);
        return ResponseEntity.ok().headers(headers).body(payload);
    }

    public Map<String, Object> validate(String authorization, MultipartFile file) {
        accessControl.requireAdmin(authorization);
        try {
            ParsedBackup parsed = parseAndValidate(file);
            return validationResponse(parsed, true, "结构化备份完整性校验通过，可进入受控恢复。", false);
        } catch (IllegalArgumentException ex) {
            return Map.of(
                "valid", false,
                "restorable", false,
                "message", ex.getMessage()
            );
        }
    }

    @Transactional
    public Map<String, Object> restore(
        String authorization,
        MultipartFile file,
        String confirmation,
        boolean dryRun
    ) {
        User operator = accessControl.requireAdmin(authorization);
        ParsedBackup parsed;
        try {
            parsed = parseAndValidate(file);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        if (dryRun) {
            return validationResponse(parsed, true, "恢复预检通过，尚未写入任何数据。", true);
        }
        if (!RESTORE_CONFIRMATION.equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type RESTORE to confirm the destructive restore");
        }

        List<String> resetTables = new ArrayList<>(BACKUP_TABLES);
        resetTables.addAll(RESET_ONLY_TABLES);
        mapper.truncate(resetTables);
        for (String table : BACKUP_TABLES) {
            restoreTable(table, parsed.data().get(table));
            if (LEGACY_VERSION.equals(parsed.version()) && "employees".equals(table)) {
                rebuildLegacyMemberships();
            }
            resetSequence(table);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    auditTrail.record(
                        0,
                        "backup",
                        0,
                        "restore",
                        "从结构化备份恢复全部业务数据",
                        operator.id,
                        operator.nickname
                    );
                }
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restored", true);
        result.put("message", "结构化业务数据已恢复；全部登录会话已撤销。附件文件请使用配套 MinIO 备份恢复。" );
        result.put("sourceVersion", parsed.version());
        result.put("targetVersion", VERSION);
        result.put("counts", datasetCounts(parsed.data()));
        result.put("restoredAt", OffsetDateTime.now().toString());
        return result;
    }

    private Map<String, Object> readDatasets() {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String table : BACKUP_TABLES) {
            List<String> columns = List.copyOf(columnTypes(table).keySet());
            List<Map<String, Object>> rows = mapper.selectAll(table, hasColumn(table, "id"));
            List<Map<String, Object>> normalizedRows = rows.stream()
                .map(row -> normalizeRow(row, columns)).toList();
            data.put(table, normalizedRows);
        }
        return data;
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row, List<String> columns) {
        Map<String, Object> byColumn = new LinkedHashMap<>();
        row.forEach((key, value) -> byColumn.put(key.toLowerCase(Locale.ROOT), value));
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (String column : columns) {
            normalized.put(column, normalizeValue(byColumn.get(column)));
        }
        return normalized;
    }

    private Object normalizeValue(Object value) {
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private ParsedBackup parseAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择非空的 JSON 备份文件。" );
        }
        String fileName = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".json")) {
            throw new IllegalArgumentException("仅支持系统导出的 .json 结构化备份。" );
        }
        if (file.getSize() > MAX_BACKUP_BYTES) {
            throw new IllegalArgumentException("结构化备份不能超过 50MB。" );
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(file.getBytes(), new TypeReference<>() {});
            String sourceVersion = Objects.toString(payload.get("version"), "");
            if ((!FORMAT.equals(payload.get("format")) && !LEGACY_FORMAT.equals(payload.get("format")))
                || !SUPPORTED_VERSIONS.contains(sourceVersion)) {
                throw new IllegalArgumentException("备份格式或版本不兼容。" );
            }
            Object rawData = payload.get("data");
            if (!(rawData instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException("备份缺少 data 数据集。" );
            }
            Map<String, Object> data = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> data.put(String.valueOf(key), value));
            for (String table : BACKUP_TABLES) {
                if (data.get(table) instanceof List<?>) {
                    continue;
                }
                if (LEGACY_VERSION.equals(sourceVersion) && LEGACY_OPTIONAL_TABLES.contains(table)) {
                    continue;
                }
                throw new IllegalArgumentException("备份缺少数据集: " + table);
            }
            if (((List<?>) data.get("users")).isEmpty() || ((List<?>) data.get("companies")).isEmpty()) {
                throw new IllegalArgumentException("备份必须至少包含一个用户和一个主体。" );
            }
            String expectedChecksum = Objects.toString(payload.get("checksum"), "");
            String actualChecksum = checksum(data);
            if (expectedChecksum.isBlank() || !MessageDigest.isEqual(
                expectedChecksum.getBytes(StandardCharsets.UTF_8),
                actualChecksum.getBytes(StandardCharsets.UTF_8)
            )) {
                throw new IllegalArgumentException("备份校验和不匹配，文件可能不完整或已被修改。" );
            }
            LEGACY_OPTIONAL_TABLES.forEach(table -> data.putIfAbsent(table, List.of()));
            if (!LEGACY_VERSION.equals(sourceVersion)
                && ((List<?>) data.get("company_memberships")).isEmpty()) {
                throw new IllegalArgumentException("当前版本备份必须至少包含一个公司成员关系。" );
            }
            return new ParsedBackup(data, actualChecksum, sourceVersion);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析备份文件，请确认文件来自当前版本系统。" );
        }
    }

    private Map<String, Object> validationResponse(
        ParsedBackup parsed,
        boolean valid,
        String message,
        boolean dryRun
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("restorable", valid);
        result.put("dryRun", dryRun);
        result.put("format", FORMAT);
        result.put("version", parsed.version());
        result.put("targetVersion", VERSION);
        result.put("message", message);
        result.put("counts", datasetCounts(parsed.data()));
        result.put("checksum", parsed.checksum());
        result.put("attachmentBytesIncluded", false);
        return result;
    }

    private Map<String, Integer> datasetCounts(Map<String, Object> data) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : BACKUP_TABLES) {
            Object rows = data.get(table);
            counts.put(table, rows instanceof List<?> list ? list.size() : 0);
        }
        return counts;
    }

    private String checksum(Map<String, Object> data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(data)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate backup checksum", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreTable(String table, Object rawRows) {
        List<?> rows = (List<?>) rawRows;
        if (rows.isEmpty()) {
            return;
        }
        Map<String, String> columnTypes = columnTypes(table);
        Map<String, Object> first = castRow(rows.getFirst(), table);
        List<String> columns = new ArrayList<>(first.keySet());
        if (columns.isEmpty() || columns.stream().anyMatch(column -> !columnTypes.containsKey(column))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Backup contains unknown columns for " + table);
        }
        List<Map<String, Object>> batch = new ArrayList<>(100);
        for (Object rawRow : rows) {
            Map<String, Object> row = castRow(rawRow, table);
            if (!row.keySet().equals(first.keySet())) {
                throw new IllegalArgumentException("Inconsistent columns in dataset " + table);
            }
            Map<String, Object> converted = new LinkedHashMap<>();
            for (String column : columns) {
                converted.put(column, typedValue(row.get(column), columnTypes.get(column)));
            }
            batch.add(converted);
            if (batch.size() == 100) {
                mapper.insertBatch(table, columns, columnTypes, batch);
                batch = new ArrayList<>(100);
            }
        }
        if (!batch.isEmpty()) {
            mapper.insertBatch(table, columns, columnTypes, batch);
        }
    }

    private Map<String, Object> castRow(Object rawRow, String table) {
        if (!(rawRow instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid row in dataset " + table);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        map.forEach((key, value) -> row.put(String.valueOf(key), value));
        if ("registration_invites".equals(table) && row.get("token") != null) {
            try {
                row.put("token", InvitationTokenDigest.fromStoredOrLegacyToken(
                    String.valueOf(row.get("token"))
                ).value());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid invitation token in structured backup");
            }
        }
        if ("ledger_members".equals(table)
            && !row.containsKey("company_id")
            && row.get("ledger_id") != null) {
            Long companyId = mapper.ledgerCompanyId(Long.parseLong(String.valueOf(row.get("ledger_id"))));
            if (companyId == null) {
                throw new IllegalArgumentException("Legacy backup contains an unscoped ledger membership");
            }
            row.put("company_id", companyId);
        }
        return row;
    }

    private Object typedValue(Object rawValue, String dataType) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue);
        return switch (dataType) {
            case "bigint" -> Long.parseLong(value);
            case "integer", "smallint" -> Integer.parseInt(value);
            case "numeric", "decimal" -> new BigDecimal(value);
            case "real" -> Float.parseFloat(value);
            case "double precision" -> Double.parseDouble(value);
            case "boolean" -> "1".equals(value) || Boolean.parseBoolean(value);
            case "bytea" -> java.util.Base64.getDecoder().decode(value);
            case "date" -> LocalDate.parse(value);
            case "timestamp without time zone" -> parseLocalDateTime(value);
            case "timestamp with time zone" -> parseOffsetDateTime(value);
            default -> value;
        };
    }

    private LocalDateTime parseLocalDateTime(String value) {
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return parseLocalDateTime(value).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
    }

    private Map<String, String> columnTypes(String table) {
        Map<String, String> types = new LinkedHashMap<>();
        for (Map<String, Object> row : mapper.columnTypes(table)) {
            types.put(String.valueOf(row.get("column_name")), String.valueOf(row.get("data_type")));
        }
        return types;
    }

    private boolean hasColumn(String table, String column) {
        return mapper.hasColumn(table, column) > 0;
    }

    private void resetSequence(String table) {
        if (!hasColumn(table, "id")) {
            return;
        }
        String sequence = mapper.serialSequence(table);
        if (sequence != null && !sequence.isBlank()) {
            mapper.resetSequence(table);
        }
    }

    private int count(String table) {
        return mapper.count(table);
    }

    private void rebuildLegacyMemberships() {
        mapper.rebuildFounderMemberships();
        mapper.rebuildEmployeeMemberships();
        if (mapper.count("company_memberships") == 0) {
            throw new IllegalArgumentException(
                "Legacy backup could not rebuild an administrator company membership"
            );
        }
    }

    private record ParsedBackup(Map<String, Object> data, String checksum, String version) {}
}
