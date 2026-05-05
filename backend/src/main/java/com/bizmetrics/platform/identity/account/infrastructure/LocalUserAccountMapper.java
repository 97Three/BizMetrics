package com.bizmetrics.platform.identity.account.infrastructure;

import com.bizmetrics.platform.identity.User;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LocalUserAccountMapper {
    @Select("SELECT COUNT(*) FROM users")
    long count();

    @Select(value = """
        INSERT INTO users (email, nickname, avatar, family_id, role, permissions,
            password_hash, created_at, updated_at)
        VALUES (#{user.email}, #{user.nickname}, #{user.avatar}, #{user.familyId},
            #{user.role}, #{user.permissions}, #{user.passwordHash}, #{createdAt}, #{updatedAt})
        RETURNING id
        """, affectData = true)
    Long insert(@Param("user") User user, @Param("createdAt") OffsetDateTime createdAt,
                @Param("updatedAt") OffsetDateTime updatedAt);

    @Results(id = "userResult", value = {
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
        SELECT id, email, nickname, avatar, family_id, role, permissions,
            password_hash, created_at, updated_at FROM users WHERE email = #{email}
        """)
    User findByEmail(String email);

    @ResultMap("userResult")
    @Select("""
        SELECT id, email, nickname, avatar, family_id, role, permissions,
            password_hash, created_at, updated_at FROM users WHERE id = #{id}
        """)
    User findById(long id);

    @ResultMap("userResult")
    @Select("""
        SELECT id, email, nickname, avatar, family_id, role, permissions,
            password_hash, created_at, updated_at FROM users WHERE id = #{id} FOR UPDATE
        """)
    User findByIdForUpdate(long id);

    @Update("""
        UPDATE users SET email = #{user.email}, nickname = #{user.nickname}, avatar = #{user.avatar},
            family_id = #{user.familyId}, role = #{user.role}, permissions = #{user.permissions},
            password_hash = #{user.passwordHash}, created_at = #{createdAt}, updated_at = #{updatedAt}
        WHERE id = #{user.id}
        """)
    int update(@Param("user") User user, @Param("createdAt") OffsetDateTime createdAt,
               @Param("updatedAt") OffsetDateTime updatedAt);

    @Update("""
        UPDATE users SET password_hash = #{nextPasswordHash}, updated_at = #{updatedAt}
        WHERE id = #{userId} AND password_hash = #{currentPasswordHash}
        """)
    int updatePasswordHashIfCurrent(@Param("userId") long userId,
                                    @Param("currentPasswordHash") String currentPasswordHash,
                                    @Param("nextPasswordHash") String nextPasswordHash,
                                    @Param("updatedAt") OffsetDateTime updatedAt);
}
