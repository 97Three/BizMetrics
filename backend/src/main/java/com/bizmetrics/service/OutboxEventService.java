package com.bizmetrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bizmetrics.notification.domain.OutboxEvent;
import com.bizmetrics.notification.infrastructure.OutboxEventStatusRepository;
import com.bizmetrics.service.support.OutboxEventHandler;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxEventService {
    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final OutboxEventHandler handler;
    private final OutboxEventStatusRepository statusRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;
    private final boolean consumerEnabled;
    private final int batchSize;
    private final int maxAttempts;
    private final int staleLockMinutes;

    public OutboxEventService(
        OutboxEventMapper mapper,
        TransactionTemplate transactionTemplate,
        OutboxEventHandler handler,
        OutboxEventStatusRepository statusRepository,
        @Value("${bizmetrics.outbox.enabled:true}") boolean enabled,
        @Value("${bizmetrics.outbox.consumer.enabled:true}") boolean consumerEnabled,
        @Value("${bizmetrics.outbox.consumer.batch-size:20}") int batchSize,
        @Value("${bizmetrics.outbox.consumer.max-attempts:8}") int maxAttempts,
        @Value("${bizmetrics.outbox.consumer.stale-lock-minutes:10}") int staleLockMinutes
    ) {
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
        this.handler = handler;
        this.statusRepository = statusRepository;
        this.enabled = enabled;
        this.consumerEnabled = consumerEnabled;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.staleLockMinutes = Math.max(1, staleLockMinutes);
    }

    public void publish(
        String eventType,
        long companyId,
        String aggregateType,
        long aggregateId,
        long actorUserId,
        Map<String, Object> payload
    ) {
        if (!enabled) {
            return;
        }
        String now = OffsetDateTime.now().toString();
        mapper.publish(
            UUID.randomUUID().toString(),
            normalize(eventType, "unknown.event"),
            normalize(aggregateType, "unknown"),
            aggregateId,
            companyId,
            actorUserId,
            payloadJson(payload),
            now
        );
    }

    @Scheduled(fixedDelayString = "${bizmetrics.outbox.consumer.fixed-delay-ms:5000}")
    public void dispatchDueEvents() {
        if (!enabled || !consumerEnabled) {
            return;
        }
        List<OutboxEvent> events = claimDueEvents();
        for (OutboxEvent event : events) {
            try {
                handler.handle(event);
                markProcessed(event);
            } catch (RuntimeException ex) {
                markFailed(event, ex);
            }
        }
    }

    private List<OutboxEvent> claimDueEvents() {
        return transactionTemplate.execute(status -> {
            recoverStaleProcessingEvents();
            String now = OffsetDateTime.now().toString();
            List<OutboxEvent> events = mapper.claimDue(now, batchSize);
            for (OutboxEvent event : events) {
                event.status = "processing";
                event.attempts = event.attempts + 1;
                event.lockedAt = now;
                event.lockToken = UUID.randomUUID().toString();
                event.updatedAt = now;
                mapper.markProcessing(event.id, event.attempts, event.lockedAt, event.lockToken, event.updatedAt);
            }
            return events;
        });
    }

    private void recoverStaleProcessingEvents() {
        String now = OffsetDateTime.now().toString();
        String staleBefore = OffsetDateTime.now().minusMinutes(staleLockMinutes).toString();
        int recovered = mapper.recoverStale(now, staleBefore);
        if (recovered > 0) {
            log.warn("Recovered {} stale outbox events", recovered);
        }
    }

    private void markProcessed(OutboxEvent event) {
        String now = OffsetDateTime.now().toString();
        if (!statusRepository.markProcessed(event.id, event.lockToken, now)) {
            log.warn(
                "Ignored processed transition for outbox event id={} because lease {} is no longer current",
                event.id,
                event.lockToken
            );
        }
    }

    private void markFailed(OutboxEvent event, RuntimeException ex) {
        String now = OffsetDateTime.now().toString();
        boolean exhausted = event.attempts >= maxAttempts;
        String nextAttemptAt = exhausted ? null : OffsetDateTime.now().plusSeconds(backoffSeconds(event.attempts)).toString();
        String status = exhausted ? "dead" : "failed";
        boolean updated = statusRepository.markFailed(
            event.id,
            event.lockToken,
            status,
            nextAttemptAt,
            truncate(errorMessage(ex), 1000),
            now
        );
        if (!updated) {
            log.warn(
                "Ignored failed transition for outbox event id={} because lease {} is no longer current",
                event.id,
                event.lockToken
            );
            return;
        }
        if (exhausted) {
            log.error("Outbox event id={} type={} moved to dead after {} attempts", event.id, event.eventType, event.attempts, ex);
        } else {
            log.warn("Outbox event id={} type={} failed, will retry at {}", event.id, event.eventType, nextAttemptAt, ex);
        }
    }

    private long backoffSeconds(int attempts) {
        int exponent = Math.min(8, Math.max(0, attempts - 1));
        return Math.min(3600, 30L * (1L << exponent));
    }

    private String payloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Outbox event payload is not serializable", ex);
        }
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
