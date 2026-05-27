package com.bizmetrics.service;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationDeliveryMapper {
    @Insert("""
        INSERT INTO notification_deliveries (
            notification_id, user_id, channel, provider, status, attempts, next_attempt_at,
            locked_at, delivered_at, last_error, response_status, created_at, updated_at
        ) VALUES (#{notificationId}, #{userId}, 'webhook', #{provider}, 'pending', 0,
            NULL, NULL, NULL, NULL, NULL, #{now}, #{now}) ON CONFLICT DO NOTHING
        """)
    int enqueue(@Param("notificationId") long notificationId, @Param("userId") long userId,
                @Param("provider") String provider, @Param("now") String now);

    @Select("""
        SELECT id, notification_id, user_id, channel, provider, attempts, lock_token
        FROM notification_deliveries
        WHERE status IN ('pending', 'failed') AND channel = 'webhook'
          AND (next_attempt_at IS NULL OR next_attempt_at <= #{now})
        ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED
        """)
    List<DeliveryRow> claimDue(@Param("now") String now, @Param("limit") int limit);

    @Update("""
        UPDATE notification_deliveries
        SET status = 'processing', attempts = #{attempts}, locked_at = #{now},
            lock_token = #{lockToken}, updated_at = #{now} WHERE id = #{id}
        """)
    int markProcessing(@Param("id") long id, @Param("attempts") int attempts,
                       @Param("lockToken") String lockToken, @Param("now") String now);

    @Update("""
        UPDATE notification_deliveries
        SET status = 'failed', next_attempt_at = #{now}, locked_at = NULL, lock_token = NULL,
            updated_at = #{now}, last_error = 'Recovered stale delivery lock'
        WHERE status = 'processing' AND locked_at IS NOT NULL AND locked_at < #{staleBefore}
        """)
    int recoverStale(@Param("now") String now, @Param("staleBefore") String staleBefore);

    @Select("""
        SELECT id, user_id, company_id, type, severity, title, content, target_url, created_at
        FROM notifications WHERE id = #{id}
        """)
    NotificationRow notification(@Param("id") long id);

    @Select("""
        SELECT user_id, webhook_enabled, webhook_provider, webhook_url
        FROM notification_preferences WHERE user_id = #{userId}
        """)
    WebhookRow webhook(@Param("userId") long userId);

    class DeliveryRow {
        public long id;
        public long notificationId;
        public long userId;
        public String channel;
        public String provider;
        public int attempts;
        public String lockToken;
    }

    class NotificationRow {
        public long id;
        public long userId;
        public long companyId;
        public String type;
        public String severity;
        public String title;
        public String content;
        public String targetUrl;
        public String createdAt;
    }

    class WebhookRow {
        public long userId;
        public boolean webhookEnabled;
        public String webhookProvider;
        public String webhookUrl;
    }
}
