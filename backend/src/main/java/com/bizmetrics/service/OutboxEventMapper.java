package com.bizmetrics.service;

import com.bizmetrics.notification.domain.OutboxEvent;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxEventMapper {
    @Insert("""
        INSERT INTO outbox_events (
            event_id, event_type, aggregate_type, aggregate_id, company_id, actor_user_id,
            payload_json, status, attempts, next_attempt_at, locked_at, processed_at, last_error, created_at, updated_at
        ) VALUES (#{eventId}, #{eventType}, #{aggregateType}, #{aggregateId}, #{companyId}, #{actorUserId},
            #{payloadJson}, 'pending', 0, NULL, NULL, NULL, NULL, #{now}, #{now})
        """)
    int publish(@Param("eventId") String eventId, @Param("eventType") String eventType,
                @Param("aggregateType") String aggregateType, @Param("aggregateId") long aggregateId,
                @Param("companyId") long companyId, @Param("actorUserId") long actorUserId,
                @Param("payloadJson") String payloadJson, @Param("now") String now);

    @Results({
        @Result(column = "id", property = "id", id = true),
        @Result(column = "event_id", property = "eventId"),
        @Result(column = "event_type", property = "eventType"),
        @Result(column = "aggregate_type", property = "aggregateType"),
        @Result(column = "aggregate_id", property = "aggregateId"),
        @Result(column = "company_id", property = "companyId"),
        @Result(column = "actor_user_id", property = "actorUserId"),
        @Result(column = "payload_json", property = "payloadJson"),
        @Result(column = "status", property = "status"),
        @Result(column = "attempts", property = "attempts"),
        @Result(column = "next_attempt_at", property = "nextAttemptAt"),
        @Result(column = "locked_at", property = "lockedAt"),
        @Result(column = "lock_token", property = "lockToken"),
        @Result(column = "processed_at", property = "processedAt"),
        @Result(column = "last_error", property = "lastError"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "updated_at", property = "updatedAt")
    })
    @Select("""
        SELECT * FROM outbox_events
        WHERE status IN ('pending', 'failed') AND (next_attempt_at IS NULL OR next_attempt_at <= #{now})
        ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED
        """)
    List<OutboxEvent> claimDue(@Param("now") String now, @Param("limit") int limit);

    @Update("""
        UPDATE outbox_events SET status = 'processing', attempts = #{attempts}, locked_at = #{lockedAt},
            lock_token = #{lockToken}, updated_at = #{updatedAt} WHERE id = #{id}
        """)
    int markProcessing(@Param("id") long id, @Param("attempts") int attempts,
                       @Param("lockedAt") String lockedAt, @Param("lockToken") String lockToken,
                       @Param("updatedAt") String updatedAt);

    @Update("""
        UPDATE outbox_events SET status = 'failed', next_attempt_at = #{now}, locked_at = NULL,
            lock_token = NULL, updated_at = #{now}, last_error = 'Recovered stale processing lock'
        WHERE status = 'processing' AND locked_at IS NOT NULL AND locked_at < #{staleBefore}
        """)
    int recoverStale(@Param("now") String now, @Param("staleBefore") String staleBefore);
}
