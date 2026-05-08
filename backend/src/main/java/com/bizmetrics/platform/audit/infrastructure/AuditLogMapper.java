package com.bizmetrics.platform.audit.infrastructure;

import com.bizmetrics.platform.audit.domain.AuditEvent;
import com.bizmetrics.platform.audit.domain.AuditLog;
import com.bizmetrics.platform.audit.domain.AuditLogSearchCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper {
    @Select(value = """
        INSERT INTO audit_logs (company_id, entity_type, entity_id, action, summary,
            actor_user_id, actor_name, created_at)
        VALUES (#{event.companyId}, #{event.entityType}, #{event.entityId}, #{event.action},
            #{event.summary}, #{event.actorUserId}, #{event.actorName}, #{event.createdAt})
        RETURNING id
        """, affectData = true)
    Long append(@Param("event") AuditEvent event);

    @Select("""
        <script>
        SELECT COUNT(*) FROM audit_logs
        <where>
          <if test="criteria.companyId != null">AND company_id = #{criteria.companyId}</if>
          <if test="criteria.entityType != ''">AND entity_type = #{criteria.entityType}</if>
          <if test="criteria.entityId != null">AND entity_id = #{criteria.entityId}</if>
          <if test="criteria.action != ''">AND action = #{criteria.action}</if>
          <if test="criteria.actorUserId != null">AND actor_user_id = #{criteria.actorUserId}</if>
          <if test="criteria.keyword != ''">
            AND POSITION(CAST(#{criteria.keyword} AS TEXT) IN
                LOWER(summary || ' ' || actor_name || ' ' || entity_type || ' ' || action)) &gt; 0
          </if>
        </where>
        </script>
        """)
    long count(@Param("criteria") AuditLogSearchCriteria criteria);

    @Select("""
        <script>
        SELECT id, company_id, entity_type, entity_id, action, summary,
            actor_user_id, actor_name, created_at FROM audit_logs
        <where>
          <if test="criteria.companyId != null">AND company_id = #{criteria.companyId}</if>
          <if test="criteria.entityType != ''">AND entity_type = #{criteria.entityType}</if>
          <if test="criteria.entityId != null">AND entity_id = #{criteria.entityId}</if>
          <if test="criteria.action != ''">AND action = #{criteria.action}</if>
          <if test="criteria.actorUserId != null">AND actor_user_id = #{criteria.actorUserId}</if>
          <if test="criteria.keyword != ''">
            AND POSITION(CAST(#{criteria.keyword} AS TEXT) IN
                LOWER(summary || ' ' || actor_name || ' ' || entity_type || ' ' || action)) &gt; 0
          </if>
        </where>
        ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<AuditLog> findPage(@Param("criteria") AuditLogSearchCriteria criteria,
                            @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
        SELECT id, company_id, entity_type, entity_id, action, summary,
            actor_user_id, actor_name, created_at FROM audit_logs
        WHERE company_id = #{companyId} AND entity_type = #{entityType} AND entity_id = #{entityId}
        ORDER BY created_at DESC, id DESC
        """)
    List<AuditLog> findByEntity(@Param("companyId") long companyId, @Param("entityType") String entityType,
                                @Param("entityId") long entityId);

    @Select("SELECT EXISTS(SELECT 1 FROM audit_logs WHERE entity_type = #{entityType})")
    boolean existsByEntityType(String entityType);
}
