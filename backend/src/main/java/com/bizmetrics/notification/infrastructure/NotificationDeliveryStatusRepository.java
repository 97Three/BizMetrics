package com.bizmetrics.notification.infrastructure;

import org.springframework.stereotype.Repository;

/** Applies fenced terminal transitions for an actively leased external notification delivery. */
@Repository
public class NotificationDeliveryStatusRepository {
    private final NotificationDeliveryStatusMapper mapper;

    public NotificationDeliveryStatusRepository(NotificationDeliveryStatusMapper mapper) {
        this.mapper = mapper;
    }

    public boolean markDelivered(long id, String lockToken, String now) {
        return mapper.markDelivered(id, lockToken, now) == 1;
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
            throw new IllegalArgumentException("Notification delivery failure must target failed or dead status");
        }
        return mapper.markFailed(id, lockToken, status, nextAttemptAt, lastError, now) == 1;
    }
}
