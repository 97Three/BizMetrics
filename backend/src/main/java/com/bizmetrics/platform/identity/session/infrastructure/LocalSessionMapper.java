package com.bizmetrics.platform.identity.session.infrastructure;

import com.bizmetrics.platform.identity.User;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LocalSessionMapper {
    @Insert("""
        INSERT INTO auth_tokens (token, user_id, created_at, expires_at)
        VALUES (#{token}, #{userId}, #{createdAt}, #{expiresAt})
        """)
    int insert(@Param("token") String token, @Param("userId") long userId,
               @Param("createdAt") OffsetDateTime createdAt, @Param("expiresAt") OffsetDateTime expiresAt);

    @Results({
        @Result(column = "id", property = "id", id = true),
        @Result(column = "email", property = "email"),
        @Result(column = "nickname", property = "nickname"),
        @Result(column = "avatar", property = "avatar"),
        @Result(column = "family_id", property = "familyId"),
        @Result(column = "role", property = "role"),
        @Result(column = "permissions", property = "permissions"),
        @Result(column = "password_hash", property = "passwordHash"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "updated_at", property = "updatedAt")
    })
    @Select("""
        SELECT users.* FROM auth_tokens session
        JOIN users ON users.id = session.user_id
        WHERE session.token = #{token} AND session.expires_at > #{now}
        """)
    User findActiveUser(@Param("token") String token, @Param("now") OffsetDateTime now);

    @Delete("DELETE FROM auth_tokens WHERE token = #{token}")
    int delete(@Param("token") String token);

    @Delete("DELETE FROM auth_tokens WHERE expires_at <= #{now}")
    int deleteExpired(@Param("now") OffsetDateTime now);
}
