package com.bizmetrics.notification.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationDeliveryStatusMapper {
    @Update("""
        UPDATE notification_deliveries
        SET status = 'delivered', delivered_at = #{now}, next_attempt_at = NULL,
            locked_at = NULL, lock_token = NULL, last_error = NULL,
            response_status = 200, updated_at = #{now}
        WHERE id = #{id} AND status = 'processing' AND lock_token = #{lockToken}
        """)
    int markDelivered(@Param("id") long id, @Param("lockToken") String lockToken, @Param("now") String now);

    @Update("""
        UPDATE notification_deliveries
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
