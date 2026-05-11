package com.bizmetrics.accessmanagement.infrastructure;

import com.bizmetrics.accessmanagement.domain.ManagedUser;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ManagedUserMapper {
    @Select("""
        SELECT COUNT(*) FROM users WHERE
          POSITION(CAST(#{keyword} AS TEXT) IN LOWER(email)) > 0
          OR POSITION(CAST(#{keyword} AS TEXT) IN LOWER(nickname)) > 0
        """)
    long searchCount(String keyword);

    @Select("""
        SELECT id, email, nickname, avatar, family_id, role, permissions, created_at, updated_at
        FROM users WHERE POSITION(CAST(#{keyword} AS TEXT) IN LOWER(email)) > 0
          OR POSITION(CAST(#{keyword} AS TEXT) IN LOWER(nickname)) > 0
        ORDER BY id LIMIT #{limit} OFFSET #{offset}
        """)
    List<ManagedUser> search(@Param("keyword") String keyword, @Param("limit") int limit,
                             @Param("offset") long offset);

    @Update("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE")
    void lockAccessMutations();

    @Select("""
        SELECT id, email, nickname, avatar, family_id, role, permissions, created_at, updated_at
        FROM users WHERE id = #{id} FOR UPDATE
        """)
    ManagedUser findForAccessMutation(long id);

    @Select("SELECT COUNT(*) FROM users")
    long countUsers();

    @Select("SELECT COUNT(*) FROM users WHERE role = 1")
    long countAdministrators();

    @Update("""
        UPDATE users SET role = #{user.role}, permissions = #{user.permissions}, updated_at = #{updatedAt}
        WHERE id = #{user.id}
        """)
    int updateAccess(@Param("user") ManagedUser user, @Param("updatedAt") OffsetDateTime updatedAt);

    @Delete("DELETE FROM users WHERE id = #{id}")
    int delete(long id);
}
