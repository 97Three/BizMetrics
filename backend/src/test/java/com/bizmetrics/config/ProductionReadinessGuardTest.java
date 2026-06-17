package com.bizmetrics.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionReadinessGuardTest {

    @Test
    void rejectsProductionWithDemoDefaults() {
        ProductionReadinessGuard guard = new ProductionReadinessGuard(
            new MockEnvironment(),
            "production",
            "demo",
            "admin@example.com",
            "123456",
            "open",
            "http://localhost:33000",
            false,
            8,
            false,
            false,
            false,
            false,
            "bizmetrics",
            "minioadmin",
            "minioadmin",
            "http://localhost:9000",
            0,
            100,
            false
        );

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void acceptsHardenedProductionSettings() {
        assertDoesNotThrow(() -> hardenedProductionGuard("https://bizmetrics.company.test").validate());
        assertDoesNotThrow(() -> hardenedProductionGuard("https://bizmetrics.company.test/").validate());
    }

    @Test
    void rejectsMinioExternalUrlThatIsNotAnOrigin() {
        List<String> invalidUrls = List.of(
            "https://bizmetrics.company.test/minio",
            "https://bizmetrics.company.test?download=true",
            "https://bizmetrics.company.test#receipts"
        );

        for (String invalidUrl : invalidUrls) {
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> hardenedProductionGuard(invalidUrl).validate(),
                invalidUrl
            );
            assertTrue(
                exception.getMessage().contains(
                    "BIZMETRICS_MINIO_EXTERNAL_URL must be a production https:// origin without a path, query, or fragment"
                ),
                invalidUrl
            );
        }
    }

    @Test
    void rejectsInvalidReceiptStorageCapacityPolicy() {
        ProductionReadinessGuard guard = hardenedProductionGuard(
            "https://bizmetrics.company.test",
            0,
            100
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::validate);
        assertTrue(exception.getMessage().contains(
            "BIZMETRICS_RECEIPT_STORAGE_MAX_BYTES_PER_COMPANY must be a positive integer"
        ));
        assertTrue(exception.getMessage().contains(
            "BIZMETRICS_RECEIPT_STORAGE_WARNING_PERCENT must be between 1 and 99"
        ));
    }

    @Test
    void productionRequiresTheReadOnlyStorageAudit() {
        ProductionReadinessGuard guard = new ProductionReadinessGuard(
            new MockEnvironment(),
            "production",
            "bootstrap",
            "ops@company.test",
            "Admin-Password-123!",
            "invite",
            "https://bizmetrics.company.test",
            true,
            12,
            true,
            true,
            true,
            true,
            "postgres-password-123!",
            "minio-access-123",
            "minio-secret-password-123!",
            "https://bizmetrics.company.test",
            10737418240L,
            80,
            false
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::validate);
        assertTrue(exception.getMessage().contains("BIZMETRICS_RECEIPT_STORAGE_AUDIT_ENABLED must be true"));
    }

    private ProductionReadinessGuard hardenedProductionGuard(String minioExternalUrl) {
        return hardenedProductionGuard(minioExternalUrl, 10737418240L, 80);
    }

    private ProductionReadinessGuard hardenedProductionGuard(
        String minioExternalUrl,
        long receiptStorageMaxBytesPerCompany,
        int receiptStorageWarningPercent
    ) {
        return new ProductionReadinessGuard(
            new MockEnvironment(),
            "production",
            "bootstrap",
            "ops@company.test",
            "Admin-Password-123!",
            "invite",
            "https://bizmetrics.company.test",
            true,
            12,
            true,
            true,
            true,
            true,
            "postgres-password-123!",
            "minio-access-123",
            "minio-secret-password-123!",
            minioExternalUrl,
            receiptStorageMaxBytesPerCompany,
            receiptStorageWarningPercent,
            true
        );
    }
}
