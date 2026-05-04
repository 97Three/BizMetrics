package com.bizmetrics.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionReadinessGuard {
    private final boolean production;
    private final String bootstrapMode;
    private final String bootstrapAdminEmail;
    private final String bootstrapAdminPassword;
    private final String registrationMode;
    private final String allowedOrigins;
    private final boolean passwordRequireComplexity;
    private final int passwordMinLength;
    private final boolean flywayEnabled;
    private final boolean outboxEnabled;
    private final boolean outboxConsumerEnabled;
    private final boolean objectStorageEnabled;
    private final String datasourcePassword;
    private final String minioAccessKey;
    private final String minioSecretKey;
    private final String minioExternalUrl;
    private final long receiptStorageMaxBytesPerCompany;
    private final int receiptStorageWarningPercent;
    private final boolean receiptStorageAuditEnabled;

    public ProductionReadinessGuard(
        Environment environment,
        @Value("${bizmetrics.runtime.environment:local}") String runtimeEnvironment,
        @Value("${bizmetrics.bootstrap.mode:demo}") String bootstrapMode,
        @Value("${bizmetrics.bootstrap.admin-email:test@bizmetrics.com}") String bootstrapAdminEmail,
        @Value("${bizmetrics.bootstrap.admin-password:123456}") String bootstrapAdminPassword,
        @Value("${bizmetrics.registration.mode:open}") String registrationMode,
        @Value("${bizmetrics.security.cors.allowed-origins:}") String allowedOrigins,
        @Value("${bizmetrics.security.password.require-complexity:false}") boolean passwordRequireComplexity,
        @Value("${bizmetrics.security.password.min-length:12}") int passwordMinLength,
        @Value("${spring.flyway.enabled:true}") boolean flywayEnabled,
        @Value("${bizmetrics.outbox.enabled:true}") boolean outboxEnabled,
        @Value("${bizmetrics.outbox.consumer.enabled:true}") boolean outboxConsumerEnabled,
        @Value("${bizmetrics.object-storage.enabled:false}") boolean objectStorageEnabled,
        @Value("${spring.datasource.password:}") String datasourcePassword,
        @Value("${bizmetrics.object-storage.access-key:minioadmin}") String minioAccessKey,
        @Value("${bizmetrics.object-storage.secret-key:minioadmin}") String minioSecretKey,
        @Value("${bizmetrics.object-storage.external-url:}") String minioExternalUrl,
        @Value("${bizmetrics.object-storage.max-bytes-per-company:10737418240}") long receiptStorageMaxBytesPerCompany,
        @Value("${bizmetrics.object-storage.warning-percent:80}") int receiptStorageWarningPercent,
        @Value("${bizmetrics.object-storage.integrity-audit.enabled:false}") boolean receiptStorageAuditEnabled
    ) {
        this.production = isProduction(runtimeEnvironment, environment);
        this.bootstrapMode = value(bootstrapMode);
        this.bootstrapAdminEmail = value(bootstrapAdminEmail);
        this.bootstrapAdminPassword = value(bootstrapAdminPassword);
        this.registrationMode = value(registrationMode);
        this.allowedOrigins = value(allowedOrigins);
        this.passwordRequireComplexity = passwordRequireComplexity;
        this.passwordMinLength = passwordMinLength;
        this.flywayEnabled = flywayEnabled;
        this.outboxEnabled = outboxEnabled;
        this.outboxConsumerEnabled = outboxConsumerEnabled;
        this.objectStorageEnabled = objectStorageEnabled;
        this.datasourcePassword = value(datasourcePassword);
        this.minioAccessKey = value(minioAccessKey);
        this.minioSecretKey = value(minioSecretKey);
        this.minioExternalUrl = value(minioExternalUrl);
        this.receiptStorageMaxBytesPerCompany = receiptStorageMaxBytesPerCompany;
        this.receiptStorageWarningPercent = receiptStorageWarningPercent;
        this.receiptStorageAuditEnabled = receiptStorageAuditEnabled;
    }

    @PostConstruct
    public void validate() {
        if (!production) {
            return;
        }
        List<String> problems = new ArrayList<>();
        requireEquals(problems, "BIZMETRICS_BOOTSTRAP_MODE", bootstrapMode, "bootstrap");
        requireEmail(problems, "BIZMETRICS_BOOTSTRAP_ADMIN_EMAIL", bootstrapAdminEmail);
        requireStrongSecret(problems, "BIZMETRICS_BOOTSTRAP_ADMIN_PASSWORD", bootstrapAdminPassword, 12);
        requireEquals(problems, "BIZMETRICS_REGISTRATION_MODE", registrationMode, "invite");
        requireProductionOrigins(problems, allowedOrigins);
        if (!passwordRequireComplexity) {
            problems.add("BIZMETRICS_PASSWORD_REQUIRE_COMPLEXITY must be true");
        }
        if (passwordMinLength < 12) {
            problems.add("BIZMETRICS_PASSWORD_MIN_LENGTH must be at least 12");
        }
        if (!flywayEnabled) {
            problems.add("BIZMETRICS_FLYWAY_ENABLED must be true");
        }
        if (!outboxEnabled || !outboxConsumerEnabled) {
            problems.add("BIZMETRICS_OUTBOX_ENABLED and BIZMETRICS_OUTBOX_CONSUMER_ENABLED must both be true");
        }
        if (!objectStorageEnabled) {
            problems.add("BIZMETRICS_OBJECT_STORAGE_ENABLED must be true");
        }
        requireStrongSecret(problems, "BIZMETRICS_POSTGRES_PASSWORD", datasourcePassword, 16);
        requireStrongSecret(problems, "BIZMETRICS_MINIO_ACCESS_KEY", minioAccessKey, 12);
        requireStrongSecret(problems, "BIZMETRICS_MINIO_SECRET_KEY", minioSecretKey, 16);
        requireHttpsOrigin(problems, "BIZMETRICS_MINIO_EXTERNAL_URL", minioExternalUrl);
        if (receiptStorageMaxBytesPerCompany <= 0) {
            problems.add("BIZMETRICS_RECEIPT_STORAGE_MAX_BYTES_PER_COMPANY must be a positive integer");
        }
        if (receiptStorageWarningPercent <= 0 || receiptStorageWarningPercent >= 100) {
            problems.add("BIZMETRICS_RECEIPT_STORAGE_WARNING_PERCENT must be between 1 and 99");
        }
        if (!receiptStorageAuditEnabled) {
            problems.add("BIZMETRICS_RECEIPT_STORAGE_AUDIT_ENABLED must be true");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Production readiness check failed: " + String.join("; ", problems));
        }
    }

    private boolean isProduction(String runtimeEnvironment, Environment environment) {
        if ("production".equalsIgnoreCase(value(runtimeEnvironment)) || "prod".equalsIgnoreCase(value(runtimeEnvironment))) {
            return true;
        }
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    private void requireEquals(List<String> problems, String name, String actual, String expected) {
        if (!expected.equalsIgnoreCase(actual)) {
            problems.add(name + " must be " + expected);
        }
    }

    private void requireEmail(List<String> problems, String name, String value) {
        if (value.isBlank() || !value.contains("@") || unsafePlaceholder(value)) {
            problems.add(name + " must be a real email address");
        }
    }

    private void requireStrongSecret(List<String> problems, String name, String value, int minLength) {
        if (value.length() < minLength || unsafePlaceholder(value)) {
            problems.add(name + " must be replaced with a strong secret of at least " + minLength + " characters");
        }
    }

    private void requireHttpsOrigin(List<String> problems, String name, String value) {
        if (unsafePlaceholder(value) || !isHttpsOrigin(value)) {
            problems.add(name + " must be a production https:// origin without a path, query, or fragment");
        }
    }

    private boolean isHttpsOrigin(String value) {
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getRawUserInfo() == null
                && (path == null || path.isEmpty() || "/".equals(path))
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return false;
        }
    }

    private void requireProductionOrigins(List<String> problems, String origins) {
        if (origins.isBlank()) {
            problems.add("BIZMETRICS_ALLOWED_ORIGINS must not be empty");
            return;
        }
        for (String origin : origins.split(",")) {
            String normalized = value(origin).toLowerCase(Locale.ROOT);
            if (normalized.isBlank() || "*".equals(normalized) || normalized.contains("localhost")
                || normalized.contains("127.0.0.1") || normalized.contains("example.com")
                || !normalized.startsWith("https://")) {
                problems.add("BIZMETRICS_ALLOWED_ORIGINS contains a non-production origin: " + origin.trim());
            }
        }
    }

    private boolean unsafePlaceholder(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.isBlank()
            || normalized.contains("replace-with")
            || normalized.contains("example.com")
            || "123456".equals(normalized)
            || "bizmetrics".equals(normalized)
            || "minioadmin".equals(normalized)
            || "admin@example.com".equals(normalized);
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
