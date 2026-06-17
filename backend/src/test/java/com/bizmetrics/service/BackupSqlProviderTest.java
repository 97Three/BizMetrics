package com.bizmetrics.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BackupSqlProviderTest {
    @Test
    void rejectsAnUntrustedIdentifierBeforeBuildingRestoreSql() {
        assertThrows(IllegalArgumentException.class, () -> BackupSqlProvider.count(
            Map.of("table", "users; DROP TABLE companies")));
    }

    @Test
    void bindsRowValuesAndCastsJsonWithoutInterpolatingPayload() {
        String sql = BackupSqlProvider.insertBatch(Map.of(
            "table", "audit_logs",
            "columns", List.of("id", "details"),
            "columnTypes", Map.of("id", "bigint", "details", "jsonb"),
            "rows", List.of(Map.of("id", 1L, "details", "{\"private\":true}"))));

        assertTrue(sql.contains("#{rows[0].id}"));
        assertTrue(sql.contains("CAST(#{rows[0].details} AS jsonb)"));
        assertTrue(!sql.contains("private"));
    }
}
