package com.bizmetrics.service.support;

import com.bizmetrics.notification.domain.OutboxEvent;

public interface OutboxEventHandler {
    void handle(OutboxEvent event);
}
