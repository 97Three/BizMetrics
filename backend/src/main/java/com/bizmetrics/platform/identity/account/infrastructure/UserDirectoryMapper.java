package com.bizmetrics.platform.identity.account.infrastructure;

import com.bizmetrics.platform.identity.account.application.UserDirectory.Entry;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserDirectoryMapper {
    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class),
        @Arg(column = "email", javaType = String.class),
        @Arg(column = "nickname", javaType = String.class),
        @Arg(column = "avatar", javaType = String.class),
        @Arg(column = "family_id", javaType = Long.class),
        @Arg(column = "role", javaType = int.class),
        @Arg(column = "permissions", javaType = int.class)
    })
    @Select("SELECT id, email, nickname, avatar, family_id, role, permissions FROM users WHERE id = #{id}")
    Entry findById(@Param("id") long id);

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class),
        @Arg(column = "email", javaType = String.class),
        @Arg(column = "nickname", javaType = String.class),
        @Arg(column = "avatar", javaType = String.class),
        @Arg(column = "family_id", javaType = Long.class),
        @Arg(column = "role", javaType = int.class),
        @Arg(column = "permissions", javaType = int.class)
    })
    @Select("""
        SELECT id, email, nickname, avatar, family_id, role, permissions
        FROM users ORDER BY CASE WHEN role = 1 THEN 0 ELSE 1 END, id LIMIT 1
        """)
    Entry findBootstrapOwner();

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class),
        @Arg(column = "email", javaType = String.class),
        @Arg(column = "nickname", javaType = String.class),
        @Arg(column = "avatar", javaType = String.class),
        @Arg(column = "family_id", javaType = Long.class),
        @Arg(column = "role", javaType = int.class),
        @Arg(column = "permissions", javaType = int.class)
    })
    @Select("SELECT id, email, nickname, avatar, family_id, role, permissions FROM users ORDER BY id")
    List<Entry> findAll();
}
