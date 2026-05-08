package com.bizmetrics.platform.scheduling.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScheduledJobLeaseMapper {
    @Select(value = """
        INSERT INTO scheduled_job_leases (
            job_name, lock_token, locked_until, next_run_at, last_started_at,
            last_completed_at, last_failed_at, last_error, created_at, updated_at
        ) VALUES (
            #{jobName}, #{lockToken}, CURRENT_TIMESTAMP + (#{leaseMillis} * INTERVAL '1 millisecond'),
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
            NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        )
        ON CONFLICT (job_name) DO UPDATE SET
            lock_token = excluded.lock_token,
            locked_until = excluded.locked_until,
            last_started_at = excluded.last_started_at,
            updated_at = excluded.updated_at
        WHERE scheduled_job_leases.next_run_at <= CURRENT_TIMESTAMP
          AND (scheduled_job_leases.lock_token IS NULL OR scheduled_job_leases.locked_until <= CURRENT_TIMESTAMP)
        RETURNING lock_token
        """, affectData = true)
    String tryAcquire(@Param("jobName") String jobName, @Param("lockToken") String lockToken,
                      @Param("leaseMillis") long leaseMillis);

    @Update("""
        UPDATE scheduled_job_leases SET lock_token = NULL, locked_until = NULL,
            next_run_at = CURRENT_TIMESTAMP + (#{cadenceMillis} * INTERVAL '1 millisecond'),
            last_completed_at = CURRENT_TIMESTAMP, last_error = NULL, updated_at = CURRENT_TIMESTAMP
        WHERE job_name = #{jobName} AND lock_token = #{lockToken}
        """)
    int markCompleted(@Param("jobName") String jobName, @Param("lockToken") String lockToken,
                      @Param("cadenceMillis") long cadenceMillis);

    @Update("""
        UPDATE scheduled_job_leases SET lock_token = NULL, locked_until = NULL,
            next_run_at = CURRENT_TIMESTAMP + (#{retryMillis} * INTERVAL '1 millisecond'),
            last_failed_at = CURRENT_TIMESTAMP, last_error = #{lastError}, updated_at = CURRENT_TIMESTAMP
        WHERE job_name = #{jobName} AND lock_token = #{lockToken}
        """)
    int markFailed(@Param("jobName") String jobName, @Param("lockToken") String lockToken,
                   @Param("retryMillis") long retryMillis, @Param("lastError") String lastError);
}
