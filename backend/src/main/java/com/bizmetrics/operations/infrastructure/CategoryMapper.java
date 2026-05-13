package com.bizmetrics.operations.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CategoryMapper {
    record Row(long id, long companyId, String name, String icon, String color, String type,
               long userId, int status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
    record WriteRow(long id, String name, String icon, String color, String type, long userId,
                    int status, OffsetDateTime createdAt, OffsetDateTime updatedAt, long companyId) {
    }

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class), @Arg(column = "company_id", javaType = long.class),
        @Arg(column = "name", javaType = String.class), @Arg(column = "icon", javaType = String.class),
        @Arg(column = "color", javaType = String.class), @Arg(column = "type", javaType = String.class),
        @Arg(column = "user_id", javaType = long.class), @Arg(column = "status", javaType = int.class),
        @Arg(column = "created_at", javaType = OffsetDateTime.class),
        @Arg(column = "updated_at", javaType = OffsetDateTime.class)
    })
    @Select("""
        <script>
        SELECT * FROM categories WHERE user_id = #{userId} AND company_id = #{companyId}
        <if test='type != null'>AND type = #{type}</if> ORDER BY id
        </script>
        """)
    List<Row> findAll(@Param("userId") long userId, @Param("companyId") long companyId,
                      @Param("type") String type);

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class), @Arg(column = "company_id", javaType = long.class),
        @Arg(column = "name", javaType = String.class), @Arg(column = "icon", javaType = String.class),
        @Arg(column = "color", javaType = String.class), @Arg(column = "type", javaType = String.class),
        @Arg(column = "user_id", javaType = long.class), @Arg(column = "status", javaType = int.class),
        @Arg(column = "created_at", javaType = OffsetDateTime.class),
        @Arg(column = "updated_at", javaType = OffsetDateTime.class)
    })
    @Select("SELECT * FROM categories WHERE id = #{id}")
    Row findById(@Param("id") long id);

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class), @Arg(column = "company_id", javaType = long.class),
        @Arg(column = "name", javaType = String.class), @Arg(column = "icon", javaType = String.class),
        @Arg(column = "color", javaType = String.class), @Arg(column = "type", javaType = String.class),
        @Arg(column = "user_id", javaType = long.class), @Arg(column = "status", javaType = int.class),
        @Arg(column = "created_at", javaType = OffsetDateTime.class),
        @Arg(column = "updated_at", javaType = OffsetDateTime.class)
    })
    @Select("SELECT * FROM categories WHERE id = #{id} FOR UPDATE")
    Row findForUpdate(@Param("id") long id);

    @Select(value = """
        INSERT INTO categories (name, icon, color, type, user_id, status, created_at, updated_at, company_id)
        VALUES (#{row.name}, #{row.icon}, #{row.color}, #{row.type}, #{row.userId}, #{row.status},
            #{row.createdAt}, #{row.updatedAt}, #{row.companyId}) RETURNING id
        """, affectData = true)
    Long insert(@Param("row") WriteRow row);

    @Update("""
        UPDATE categories SET name = #{row.name}, icon = #{row.icon}, color = #{row.color},
            type = #{row.type}, user_id = #{row.userId}, status = #{row.status},
            created_at = #{row.createdAt}, updated_at = #{row.updatedAt}, company_id = #{row.companyId}
        WHERE id = #{row.id}
        """)
    int update(@Param("row") WriteRow row);

    @Select("""
        SELECT (SELECT COUNT(*) FROM transactions WHERE category_id = #{id})
             + (SELECT COUNT(*) FROM budgets WHERE category_id = #{id})
        """)
    int countAccountingReferences(@Param("id") long id);

    @Delete("DELETE FROM categories WHERE id = #{id}")
    int delete(@Param("id") long id);

    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{name}, 0))) AS locked")
    int lockDefaults(@Param("name") String name);
}
