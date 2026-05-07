package com.bizmetrics.platform.identity.security.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LoginFailureMapper {
    record Row(String subjectKey, String subjectType, int failedAttempts,
               OffsetDateTime windowStartedAt, OffsetDateTime lockedUntil, OffsetDateTime updatedAt) {}

    @Insert("""
        INSERT INTO login_failure_states (subject_key, subject_type, failed_attempts,
            window_started_at, locked_until, updated_at)
        VALUES (#{key}, #{type}, 0, #{now}, NULL, #{now})
        ON CONFLICT (subject_key) DO NOTHING
        """)
    int insertIfAbsent(@Param("key") String key, @Param("type") String type,
                       @Param("now") OffsetDateTime now);

    @ConstructorArgs({
        @Arg(column="subject_key", javaType=String.class),
        @Arg(column="subject_type", javaType=String.class),
        @Arg(column="failed_attempts", javaType=int.class),
        @Arg(column="window_started_at", javaType=OffsetDateTime.class),
        @Arg(column="locked_until", javaType=OffsetDateTime.class),
        @Arg(column="updated_at", javaType=OffsetDateTime.class)
    })
    @Select("""
        SELECT subject_key, subject_type, failed_attempts, window_started_at, locked_until, updated_at
        FROM login_failure_states WHERE subject_key = #{key} FOR UPDATE
        """)
    Row lock(@Param("key") String key);

    @Update("""
        UPDATE login_failure_states SET failed_attempts = #{attempts},
            window_started_at = #{startedAt}, locked_until = #{lockedUntil}, updated_at = #{updatedAt}
        WHERE subject_key = #{key} AND subject_type = #{type}
        """)
    int update(@Param("key") String key, @Param("type") String type,
               @Param("attempts") int attempts, @Param("startedAt") OffsetDateTime startedAt,
               @Param("lockedUntil") OffsetDateTime lockedUntil,
               @Param("updatedAt") OffsetDateTime updatedAt);

    @Select({"<script>",
        "SELECT MAX(locked_until) FROM login_failure_states WHERE subject_key IN",
        "<foreach collection='keys' item='key' open='(' separator=',' close=')'>#{key}</foreach>",
        "AND locked_until &gt; #{now}", "</script>"})
    OffsetDateTime findLatestActiveLock(@Param("keys") List<String> keys,
                                        @Param("now") OffsetDateTime now);

    @Delete("DELETE FROM login_failure_states WHERE subject_key = #{key} AND subject_type = #{type}")
    int delete(@Param("key") String key, @Param("type") String type);

    @Delete("""
        DELETE FROM login_failure_states
        WHERE updated_at < #{updatedBefore} AND (locked_until IS NULL OR locked_until <= #{now})
        """)
    int deleteInactiveBefore(@Param("updatedBefore") OffsetDateTime updatedBefore,
                             @Param("now") OffsetDateTime now);
}
