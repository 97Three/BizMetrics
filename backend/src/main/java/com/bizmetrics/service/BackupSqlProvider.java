package com.bizmetrics.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Identifiers come from BackupService's fixed table list and verified database metadata. */
public final class BackupSqlProvider {
    private BackupSqlProvider() {}

    public static String selectAll(Map<String, Object> params) {
        String table = identifier((String) params.get("table"));
        return "SELECT * FROM " + table + (Boolean.TRUE.equals(params.get("orderById")) ? " ORDER BY id" : " ORDER BY 1");
    }

    public static String count(Map<String, Object> params) {
        return "SELECT COUNT(*) FROM " + identifier((String) params.get("table"));
    }

    @SuppressWarnings("unchecked")
    public static String truncate(Map<String, Object> params) {
        List<String> tables = (List<String>) params.get("tables");
        return "TRUNCATE TABLE " + tables.stream().map(BackupSqlProvider::identifier)
            .collect(Collectors.joining(", ")) + " RESTART IDENTITY";
    }

    @SuppressWarnings("unchecked")
    public static String insertBatch(Map<String, Object> params) {
        String table = identifier((String) params.get("table"));
        List<String> columns = (List<String>) params.get("columns");
        Map<String, String> types = (Map<String, String>) params.get("columnTypes");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) params.get("rows");
        String names = columns.stream().map(BackupSqlProvider::identifier).collect(Collectors.joining(", "));
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (").append(names).append(") VALUES ");
        for (int row = 0; row < rows.size(); row++) {
            if (row > 0) sql.append(", ");
            sql.append('(');
            for (int col = 0; col < columns.size(); col++) {
                if (col > 0) sql.append(", ");
                String column = identifier(columns.get(col));
                String binding = "#{rows[" + row + "]." + column + "}";
                String type = types.get(column);
                if ("json".equals(type) || "jsonb".equals(type)) {
                    binding = "CAST(" + binding + " AS " + type + ")";
                }
                sql.append(binding);
            }
            sql.append(')');
        }
        return sql.toString();
    }

    public static String resetSequence(Map<String, Object> params) {
        String table = identifier((String) params.get("table"));
        return "SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), "
            + "COALESCE(MAX(id), 1), COUNT(*) > 0) FROM " + table;
    }

    private static String identifier(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid backup SQL identifier");
        }
        return value;
    }
}
