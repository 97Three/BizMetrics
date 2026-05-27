package com.bizmetrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bizmetrics.notification.infrastructure.NotificationDeliveryStatusRepository;
import com.bizmetrics.service.support.WebhookUrlValidator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final NotificationDeliveryMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebhookUrlValidator webhookUrlValidator;
    private final NotificationDeliveryStatusRepository statusRepository;
    private final OkHttpClient client;
    private final boolean enabled;
    private final int batchSize;
    private final int maxAttempts;
    private final int staleLockMinutes;

    public NotificationDeliveryService(
        NotificationDeliveryMapper mapper,
        TransactionTemplate transactionTemplate,
        WebhookUrlValidator webhookUrlValidator,
        NotificationDeliveryStatusRepository statusRepository,
        @Value("${bizmetrics.notifications.delivery.enabled:true}") boolean enabled,
        @Value("${bizmetrics.notifications.delivery.batch-size:20}") int batchSize,
        @Value("${bizmetrics.notifications.delivery.max-attempts:6}") int maxAttempts,
        @Value("${bizmetrics.notifications.delivery.stale-lock-minutes:10}") int staleLockMinutes,
        @Value("${bizmetrics.notifications.delivery.timeout-seconds:5}") int timeoutSeconds
    ) {
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
        this.webhookUrlValidator = webhookUrlValidator;
        this.statusRepository = statusRepository;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.staleLockMinutes = Math.max(1, staleLockMinutes);
        int timeout = Math.max(2, timeoutSeconds);
        this.client = new OkHttpClient.Builder()
            .dns(webhookUrlValidator.validatingDns())
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .callTimeout(timeout + 2L, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
    }

    public void enqueueWebhook(long notificationId, long userId, String provider) {
        if (!enabled) {
            return;
        }
        String now = OffsetDateTime.now().toString();
        mapper.enqueue(notificationId, userId, normalizeProvider(provider), now);
    }

    public void sendTestWebhook(NotificationService.NotificationPreference preference) {
        WebhookTarget target = requireTarget(preference.userId());
        NotificationPayload payload = new NotificationPayload(
            0,
            preference.userId(),
            0,
            "system",
            "info",
            "BizMetrics 通知测试",
            "这是一条测试消息，用于确认外部通知 Webhook 可以正常接收。",
            "/settings",
            OffsetDateTime.now().toString()
        );
        postWebhook(target, payload, "bizmetrics:webhook-test:" + UUID.randomUUID());
    }

    @Scheduled(fixedDelayString = "${bizmetrics.notifications.delivery.fixed-delay-ms:10000}")
    public void dispatchDueDeliveries() {
        if (!enabled) {
            return;
        }
        List<DeliveryTask> tasks = claimDueDeliveries();
        for (DeliveryTask task : tasks) {
            try {
                WebhookTarget target = requireTarget(task.userId());
                NotificationPayload payload = notificationPayload(task.notificationId());
                postWebhook(target, payload, "bizmetrics:notification-delivery:" + task.id());
                markDelivered(task);
            } catch (RuntimeException ex) {
                markFailed(task, ex);
            }
        }
    }

    private List<DeliveryTask> claimDueDeliveries() {
        return transactionTemplate.execute(status -> {
            recoverStaleDeliveries();
            String now = OffsetDateTime.now().toString();
            List<DeliveryTask> tasks = mapper.claimDue(now, batchSize).stream()
                .map(row -> new DeliveryTask(row.id, row.notificationId, row.userId,
                    row.channel, row.provider, row.attempts, row.lockToken)).toList();
            List<DeliveryTask> claimed = new ArrayList<>(tasks.size());
            for (DeliveryTask task : tasks) {
                String lockToken = UUID.randomUUID().toString();
                mapper.markProcessing(task.id(), task.attempts() + 1, lockToken, now);
                claimed.add(new DeliveryTask(
                    task.id(),
                    task.notificationId(),
                    task.userId(),
                    task.channel(),
                    task.provider(),
                    task.attempts() + 1,
                    lockToken
                ));
            }
            return claimed;
        });
    }

    private void recoverStaleDeliveries() {
        String now = OffsetDateTime.now().toString();
        String staleBefore = OffsetDateTime.now().minusMinutes(staleLockMinutes).toString();
        int recovered = mapper.recoverStale(now, staleBefore);
        if (recovered > 0) {
            log.warn("Recovered {} stale notification deliveries", recovered);
        }
    }

    private void markDelivered(DeliveryTask task) {
        String now = OffsetDateTime.now().toString();
        if (!statusRepository.markDelivered(task.id(), task.lockToken(), now)) {
            log.warn(
                "Ignored delivered transition for notification delivery id={} because lease {} is no longer current",
                task.id(),
                task.lockToken()
            );
        }
    }

    private void markFailed(DeliveryTask task, RuntimeException ex) {
        String now = OffsetDateTime.now().toString();
        boolean exhausted = task.attempts() >= maxAttempts;
        String nextAttemptAt = exhausted ? null : OffsetDateTime.now().plusSeconds(backoffSeconds(task.attempts())).toString();
        String status = exhausted ? "dead" : "failed";
        boolean updated = statusRepository.markFailed(
            task.id(),
            task.lockToken(),
            status,
            nextAttemptAt,
            truncate(errorMessage(ex), 1000),
            now
        );
        if (!updated) {
            log.warn(
                "Ignored failed transition for notification delivery id={} because lease {} is no longer current",
                task.id(),
                task.lockToken()
            );
            return;
        }
        if (exhausted) {
            log.error("Notification delivery id={} moved to dead after {} attempts", task.id(), task.attempts(), ex);
        } else {
            log.warn("Notification delivery id={} failed, will retry at {}", task.id(), nextAttemptAt, ex);
        }
    }

    private NotificationPayload notificationPayload(long notificationId) {
        NotificationDeliveryMapper.NotificationRow row = mapper.notification(notificationId);
        if (row == null) {
            throw new IllegalStateException("Notification not found: " + notificationId);
        }
        return new NotificationPayload(row.id, row.userId, row.companyId, row.type,
            row.severity, row.title, row.content, row.targetUrl, row.createdAt);
    }

    private WebhookTarget requireTarget(long userId) {
        NotificationDeliveryMapper.WebhookRow row = mapper.webhook(userId);
        if (row == null || !row.webhookEnabled || isBlank(row.webhookUrl)) {
            throw new IllegalStateException("Webhook is not configured for user " + userId);
        }
        String url = row.webhookUrl.strip();
        try {
            url = webhookUrlValidator.requireSafeUrl(url).toASCIIString();
        } catch (WebhookUrlValidator.UnsafeWebhookUrlException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
        return new WebhookTarget(userId, true, normalizeProvider(row.webhookProvider), url);
    }

    private void postWebhook(WebhookTarget target, NotificationPayload payload, String idempotencyKey) {
        String body = webhookBody(target.provider(), payload);
        Request request = new Request.Builder()
            .url(target.url())
            .post(RequestBody.create(body, JSON))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey)
            .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Webhook HTTP " + response.code());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Webhook delivery failed: " + ex.getMessage(), ex);
        }
    }

    private String webhookBody(String provider, NotificationPayload payload) {
        String text = "[BizMetrics] " + payload.title() + "\n" + payload.content();
        if (!isBlank(payload.targetUrl())) {
            text += "\n" + payload.targetUrl();
        }
        Map<String, Object> body = switch (provider) {
            case "feishu" -> Map.of(
                "msg_type", "text",
                "content", Map.of("text", text)
            );
            case "wecom" -> Map.of(
                "msgtype", "text",
                "text", Map.of("content", text)
            );
            default -> Map.of(
                "source", "bizmetrics",
                "id", payload.id(),
                "userId", payload.userId(),
                "companyId", payload.companyId(),
                "type", payload.type(),
                "severity", payload.severity(),
                "title", payload.title(),
                "content", payload.content(),
                "targetUrl", payload.targetUrl() == null ? "" : payload.targetUrl(),
                "createdAt", payload.createdAt()
            );
        };
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Webhook payload serialization failed", ex);
        }
    }

    private long backoffSeconds(int attempts) {
        int exponent = Math.min(8, Math.max(0, attempts - 1));
        return Math.min(1800, 20L * (1L << exponent));
    }

    private String normalizeProvider(String provider) {
        return switch (provider == null ? "" : provider) {
            case "feishu", "wecom" -> provider;
            default -> "generic";
        };
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DeliveryTask(
        long id,
        long notificationId,
        long userId,
        String channel,
        String provider,
        int attempts,
        String lockToken
    ) {
    }

    private record WebhookTarget(long userId, boolean enabled, String provider, String url) {
    }

    private record NotificationPayload(
        long id,
        long userId,
        long companyId,
        String type,
        String severity,
        String title,
        String content,
        String targetUrl,
        String createdAt
    ) {
    }
}
