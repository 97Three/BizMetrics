package com.bizmetrics.platform.identity.invitation.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RegistrationInvitationMapper {
    record Row(long id, String token, String email, int role, int permissions,
               OffsetDateTime expiresAt, OffsetDateTime acceptedAt, Long acceptedUserId,
               Long invitedByUserId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    @Select(value = """
        INSERT INTO registration_invites (token, email, role, permissions, expires_at,
            accepted_at, accepted_user_id, invited_by_user_id, created_at, updated_at)
        VALUES (#{token}, #{email}, #{role}, #{permissions}, #{expiresAt}, NULL, NULL,
            #{invitedByUserId}, #{createdAt}, #{updatedAt}) RETURNING id
        """, affectData = true)
    Long insert(@Param("token") String token, @Param("email") String email,
                @Param("role") int role, @Param("permissions") int permissions,
                @Param("expiresAt") OffsetDateTime expiresAt,
                @Param("invitedByUserId") Long invitedByUserId,
                @Param("createdAt") OffsetDateTime createdAt,
                @Param("updatedAt") OffsetDateTime updatedAt);

    @ConstructorArgs({
        @Arg(column="id", javaType=long.class), @Arg(column="token", javaType=String.class),
        @Arg(column="email", javaType=String.class), @Arg(column="role", javaType=int.class),
        @Arg(column="permissions", javaType=int.class),
        @Arg(column="expires_at", javaType=OffsetDateTime.class),
        @Arg(column="accepted_at", javaType=OffsetDateTime.class),
        @Arg(column="accepted_user_id", javaType=Long.class),
        @Arg(column="invited_by_user_id", javaType=Long.class),
        @Arg(column="created_at", javaType=OffsetDateTime.class),
        @Arg(column="updated_at", javaType=OffsetDateTime.class)
    })
    @Select("""
        SELECT id, token, email, role, permissions, expires_at, accepted_at,
               accepted_user_id, invited_by_user_id, created_at, updated_at
        FROM registration_invites ORDER BY created_at DESC, id DESC
        """)
    List<Row> findAll();

    @ConstructorArgs({
        @Arg(column="id", javaType=long.class), @Arg(column="token", javaType=String.class),
        @Arg(column="email", javaType=String.class), @Arg(column="role", javaType=int.class),
        @Arg(column="permissions", javaType=int.class),
        @Arg(column="expires_at", javaType=OffsetDateTime.class),
        @Arg(column="accepted_at", javaType=OffsetDateTime.class),
        @Arg(column="accepted_user_id", javaType=Long.class),
        @Arg(column="invited_by_user_id", javaType=Long.class),
        @Arg(column="created_at", javaType=OffsetDateTime.class),
        @Arg(column="updated_at", javaType=OffsetDateTime.class)
    })
    @Select("""
        SELECT id, token, email, role, permissions, expires_at, accepted_at,
               accepted_user_id, invited_by_user_id, created_at, updated_at
        FROM registration_invites WHERE token = #{token} FOR UPDATE
        """)
    Row findByTokenForUpdate(@Param("token") String token);

    @Update("""
        UPDATE registration_invites SET accepted_at = #{acceptedAt},
            accepted_user_id = #{acceptedUserId}, updated_at = #{updatedAt}
        WHERE id = #{id} AND accepted_at IS NULL
        """)
    int updateAcceptance(@Param("id") long id, @Param("acceptedAt") OffsetDateTime acceptedAt,
                         @Param("acceptedUserId") Long acceptedUserId,
                         @Param("updatedAt") OffsetDateTime updatedAt);
}
