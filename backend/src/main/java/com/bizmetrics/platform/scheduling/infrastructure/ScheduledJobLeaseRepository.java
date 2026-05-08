package com.bizmetrics.platform.scheduling.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Atomically claims and fences scheduled work using the PostgreSQL clock. */
@Repository
public class ScheduledJobLeaseRepository {
    private final ScheduledJobLeaseMapper mapper;

    public ScheduledJobLeaseRepository(ScheduledJobLeaseMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<String> tryAcquire(String jobName, long leaseMillis) {
        String normalizedName = requireJobName(jobName);
        String lockToken = UUID.randomUUID().toString();
        return Optional.ofNullable(mapper.tryAcquire(normalizedName, lockToken, positive(leaseMillis)));
    }

    public boolean markCompleted(String jobName, String lockToken, long cadenceMillis) {
        return mapper.markCompleted(requireJobName(jobName), requireLockToken(lockToken), positive(cadenceMillis)) == 1;
    }

    public boolean markFailed(String jobName, String lockToken, long retryMillis, String lastError) {
        return mapper.markFailed(requireJobName(jobName), requireLockToken(lockToken), positive(retryMillis), lastError) == 1;
    }

    private String requireJobName(String jobName) {
        String normalized = jobName == null ? "" : jobName.strip();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw new IllegalArgumentException("Scheduled job name must contain 1 to 120 characters");
        }
        return normalized;
    }

    private String requireLockToken(String lockToken) {
        if (lockToken == null || lockToken.isBlank()) {
            throw new IllegalArgumentException("Scheduled job lock token is required");
        }
        return lockToken;
    }

    private long positive(long milliseconds) {
        return Math.max(1, milliseconds);
    }
}
