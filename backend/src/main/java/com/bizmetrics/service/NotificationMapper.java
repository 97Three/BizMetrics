package com.bizmetrics.service;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper {
    @Select("SELECT count(*) FROM notifications WHERE user_id = #{userId} AND (NOT #{unreadOnly} OR read_at IS NULL)")
    long count(@Param("userId") long userId, @Param("unreadOnly") boolean unreadOnly);

    @Select("""
        SELECT * FROM notifications WHERE user_id = #{userId}
        AND (NOT #{unreadOnly} OR read_at IS NULL)
        ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
        """)
    List<NotificationRow> list(@Param("userId") long userId, @Param("unreadOnly") boolean unreadOnly,
                               @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
        SELECT (SELECT COUNT(*) FROM notifications WHERE user_id = #{userId} AND read_at IS NULL) AS unread_count,
            (SELECT COUNT(*) FROM notification_deliveries WHERE user_id = #{userId}
                AND status IN ('pending', 'failed', 'processing')) AS pending_delivery_count,
            (SELECT COUNT(*) FROM notification_deliveries WHERE user_id = #{userId}
                AND status = 'dead') AS failed_delivery_count
        """)
    SummaryRow summary(@Param("userId") long userId);

    @Insert("""
        INSERT INTO notification_preferences (
            user_id, enabled, webhook_enabled, webhook_provider, webhook_url,
            min_severity, muted_types, created_at, updated_at
        ) VALUES (#{userId}, #{enabled}, #{webhookEnabled}, #{webhookProvider}, #{webhookUrl},
            #{minSeverity}, #{mutedTypes}, #{now}, #{now})
        ON CONFLICT(user_id) DO UPDATE SET
            enabled = excluded.enabled, webhook_enabled = excluded.webhook_enabled,
            webhook_provider = excluded.webhook_provider, webhook_url = excluded.webhook_url,
            min_severity = excluded.min_severity, muted_types = excluded.muted_types,
            updated_at = excluded.updated_at
        """)
    int upsertPreference(@Param("userId") long userId, @Param("enabled") boolean enabled,
                         @Param("webhookEnabled") boolean webhookEnabled,
                         @Param("webhookProvider") String webhookProvider,
                         @Param("webhookUrl") String webhookUrl,
                         @Param("minSeverity") String minSeverity,
                         @Param("mutedTypes") String mutedTypes, @Param("now") String now);

    @Update("UPDATE notifications SET read_at = #{now}, updated_at = #{now} WHERE id = #{id} AND user_id = #{userId}")
    int markRead(@Param("id") long id, @Param("userId") long userId, @Param("now") String now);

    @Update("UPDATE notifications SET read_at = #{now}, updated_at = #{now} WHERE user_id = #{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") long userId, @Param("now") String now);

    @Select("SELECT * FROM notifications WHERE id = #{id} AND user_id = #{userId}")
    NotificationRow findForUser(@Param("id") long id, @Param("userId") long userId);

    @Select(value = """
        INSERT INTO notifications (
            user_id, company_id, type, severity, title, content, target_url,
            source_type, source_id, dedupe_key, read_at, created_at, updated_at
        ) VALUES (#{userId}, #{companyId}, #{type}, #{severity}, #{title}, #{content}, #{targetUrl},
            #{sourceType}, #{sourceId}, #{dedupeKey}, NULL, #{now}, #{now})
        ON CONFLICT DO NOTHING RETURNING id
        """, affectData = true)
    Long insertNotification(@Param("userId") long userId, @Param("companyId") long companyId,
                            @Param("type") String type, @Param("severity") String severity,
                            @Param("title") String title, @Param("content") String content,
                            @Param("targetUrl") String targetUrl, @Param("sourceType") String sourceType,
                            @Param("sourceId") Long sourceId, @Param("dedupeKey") String dedupeKey,
                            @Param("now") String now);

    @Select("SELECT * FROM notification_preferences WHERE user_id = #{userId}")
    PreferenceRow preference(@Param("userId") long userId);

    @Insert("""
        INSERT INTO notification_preferences (user_id, enabled, webhook_enabled, webhook_provider,
            webhook_url, min_severity, muted_types, created_at, updated_at)
        VALUES (#{userId}, true, false, 'generic', NULL, 'info', '', #{now}, #{now})
        ON CONFLICT DO NOTHING
        """)
    int createDefaultPreference(@Param("userId") long userId, @Param("now") String now);

    class NotificationRow {
        public long id;
        public long userId;
        public long companyId;
        public String type;
        public String severity;
        public String title;
        public String content;
        public String targetUrl;
        public String sourceType;
        public Long sourceId;
        public String readAt;
        public String createdAt;
        public String updatedAt;
    }

    class SummaryRow {
        public long unreadCount;
        public long pendingDeliveryCount;
        public long failedDeliveryCount;
    }

    class PreferenceRow {
        public long userId;
        public boolean enabled;
        public boolean webhookEnabled;
        public String webhookProvider;
        public String webhookUrl;
        public String minSeverity;
        public String mutedTypes;
        public String createdAt;
        public String updatedAt;
    }
}
