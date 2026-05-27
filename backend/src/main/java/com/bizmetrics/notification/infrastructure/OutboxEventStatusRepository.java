package com.bizmetrics.notification.infrastructure;

import org.springframework.stereotype.Repository;

/** Applies fenced terminal transitions for an actively leased outbox event. */
@Repository
public class OutboxEventStatusRepository {
    private final OutboxEventStatusMapper mapper;

    public OutboxEventStatusRepository(OutboxEventStatusMapper mapper) {
        this.mapper = mapper;
    }

    public boolean markProcessed(long id, String lockToken, String now) {
        return mapper.markProcessed(id, lockToken, now) == 1;
    }

    public boolean markFailed(
        long id,
        String lockToken,
        String status,
        String nextAttemptAt,
        String lastError,
        String now
    ) {
        if (!"failed".equals(status) && !"dead".equals(status)) {
            throw new IllegalArgumentException("Outbox failure transition must target failed or dead status");
        }
        return mapper.markFailed(id, lockToken, status, nextAttemptAt, lastError, now) == 1;
    }
}
