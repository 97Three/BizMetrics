package com.bizmetrics.notification.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxEventStatusMapper {
    @Update("""
        UPDATE outbox_events
        SET status = 'processed', processed_at = #{now}, locked_at = NULL, lock_token = NULL,
            next_attempt_at = NULL, last_error = NULL, updated_at = #{now}
        WHERE id = #{id} AND status = 'processing' AND lock_token = #{lockToken}
        """)
    int markProcessed(@Param("id") long id, @Param("lockToken") String lockToken, @Param("now") String now);

    @Update("""
        UPDATE outbox_events
        SET status = #{status}, next_attempt_at = #{nextAttemptAt}, locked_at = NULL, lock_token = NULL,
            last_error = #{lastError}, updated_at = #{now}
        WHERE id = #{id} AND status = 'processing' AND lock_token = #{lockToken}
        """)
    int markFailed(
        @Param("id") long id,
        @Param("lockToken") String lockToken,
        @Param("status") String status,
        @Param("nextAttemptAt") String nextAttemptAt,
        @Param("lastError") String lastError,
        @Param("now") String now
    );
}
