package com.bizmetrics.approval.infrastructure;

import com.bizmetrics.approval.application.ApprovalRepository.NewAction;
import com.bizmetrics.approval.application.ApprovalRepository.NewApproval;
import com.bizmetrics.approval.domain.ApprovalAction;
import com.bizmetrics.approval.domain.ApprovalRequest;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalMapper {
    record Summary(long total, long pending, long approved, long rejected, long minePending) {
    }

    String FILTER = """
        FROM approval_requests WHERE company_id = #{companyId}
        <if test='participantUserId != null'>
            AND (applicant_user_id = #{participantUserId} OR assignee_user_id = #{participantUserId})
        </if>
        <if test='status != null and status != ""'>AND status = #{status}</if>
        <if test='requestType != null and requestType != ""'>AND request_type = #{requestType}</if>
        <if test='keyword != null and keyword != ""'>
            AND (LOWER(title) LIKE CONCAT('%', #{keyword}, '%')
              OR LOWER(COALESCE(description, '')) LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        """;

    @Select("<script>SELECT COUNT(*) " + FILTER + "</script>")
    long count(@Param("companyId") long companyId, @Param("participantUserId") Long participantUserId,
               @Param("status") String status, @Param("requestType") String requestType,
               @Param("keyword") String keyword);

    @Results(id = "request", value = {})
    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class), @Arg(column = "version", javaType = long.class),
        @Arg(column = "idempotency_key", javaType = String.class),
        @Arg(column = "company_id", javaType = long.class),
        @Arg(column = "request_type", javaType = String.class),
        @Arg(column = "entity_type", javaType = String.class),
        @Arg(column = "entity_id", javaType = Long.class),
        @Arg(column = "title", javaType = String.class),
        @Arg(column = "amount", javaType = java.math.BigDecimal.class),
        @Arg(column = "applicant_user_id", javaType = long.class),
        @Arg(column = "assignee_user_id", javaType = Long.class),
        @Arg(column = "status", javaType = String.class),
        @Arg(column = "current_step", javaType = String.class),
        @Arg(column = "description", javaType = String.class),
        @Arg(column = "decided_at", javaType = String.class),
        @Arg(column = "created_at", javaType = String.class),
        @Arg(column = "updated_at", javaType = String.class)
    })
    @Select("<script>SELECT * " + FILTER
        + " ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<ApprovalRequest> page(@Param("companyId") long companyId,
        @Param("participantUserId") Long participantUserId, @Param("status") String status,
        @Param("requestType") String requestType, @Param("keyword") String keyword,
        @Param("limit") int limit, @Param("offset") long offset);

    @ConstructorArgs({
        @Arg(column = "total", javaType = long.class),
        @Arg(column = "pending", javaType = long.class),
        @Arg(column = "approved", javaType = long.class),
        @Arg(column = "rejected", javaType = long.class),
        @Arg(column = "mine_pending", javaType = long.class)
    })
    @Select("""
        <script>
        SELECT COUNT(*) AS total,
            COUNT(*) FILTER (WHERE status = 'pending') AS pending,
            COUNT(*) FILTER (WHERE status = 'approved') AS approved,
            COUNT(*) FILTER (WHERE status = 'rejected') AS rejected,
            COUNT(*) FILTER (WHERE status = 'pending' AND assignee_user_id = #{userId}) AS mine_pending
        FROM approval_requests WHERE company_id = #{companyId}
        <if test='!administrator'>AND (applicant_user_id = #{userId} OR assignee_user_id = #{userId})</if>
        </script>
        """)
    Summary summarize(@Param("companyId") long companyId, @Param("userId") long userId,
                      @Param("administrator") boolean administrator);

    @ResultMap("request")
    @Select("SELECT * FROM approval_requests WHERE id = #{id}")
    ApprovalRequest findById(@Param("id") long id);

    @ResultMap("request")
    @Select("SELECT * FROM approval_requests WHERE id = #{id} FOR UPDATE")
    ApprovalRequest findByIdForUpdate(@Param("id") long id);

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class),
        @Arg(column = "request_id", javaType = long.class),
        @Arg(column = "actor_user_id", javaType = long.class),
        @Arg(column = "action", javaType = String.class),
        @Arg(column = "comment", javaType = String.class),
        @Arg(column = "created_at", javaType = String.class)
    })
    @Select("SELECT * FROM approval_actions WHERE request_id = #{requestId} ORDER BY id")
    List<ApprovalAction> findActions(@Param("requestId") long requestId);

    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{key}, 0))) AS locked")
    int advisoryLock(@Param("key") String key);

    @ResultMap("request")
    @Select("SELECT * FROM approval_requests WHERE company_id = #{companyId} AND idempotency_key = #{key}")
    ApprovalRequest findByIdempotencyKey(@Param("companyId") long companyId, @Param("key") String key);

    @Select("""
        SELECT COUNT(*) FROM approval_requests WHERE company_id = #{companyId}
            AND entity_type = #{entityType} AND entity_id = #{entityId} AND status = 'pending'
        """)
    int countPending(@Param("companyId") long companyId, @Param("entityType") String entityType,
                     @Param("entityId") long entityId);

    @Select("""
        SELECT COUNT(*) FROM users u WHERE u.id = #{assigneeId}
            AND (u.id = #{ownerId} OR EXISTS (SELECT 1 FROM employees e
                WHERE e.company_id = #{companyId} AND e.user_id = u.id AND e.status <> 'departed'))
        """)
    int countValidAssignee(@Param("companyId") long companyId, @Param("ownerId") long ownerId,
                           @Param("assigneeId") long assigneeId);

    @ResultMap("request")
    @Select(value = """
        INSERT INTO approval_requests (company_id, request_type, entity_type, entity_id, title,
            amount, applicant_user_id, assignee_user_id, status, current_step, description,
            decided_at, created_at, updated_at, idempotency_key)
        VALUES (#{approval.companyId}, #{approval.requestType}, #{approval.entityType},
            #{approval.entityId}, #{approval.title}, #{approval.amount},
            #{approval.applicantUserId}, #{approval.assigneeUserId}, #{approval.status},
            #{approval.currentStep}, #{approval.description}, NULL, #{approval.createdAt},
            #{approval.updatedAt}, #{approval.idempotencyKey}) RETURNING *
        """, affectData = true)
    ApprovalRequest insert(@Param("approval") NewApproval approval);

    @Update("""
        UPDATE approval_requests SET status = #{status}, current_step = #{currentStep},
            decided_at = #{decidedAt}, updated_at = #{updatedAt}, version = version + 1
        WHERE id = #{id}
        """)
    int updateState(@Param("id") long id, @Param("status") String status,
        @Param("currentStep") String currentStep, @Param("decidedAt") String decidedAt,
        @Param("updatedAt") String updatedAt);

    @Insert("""
        INSERT INTO approval_actions (request_id, actor_user_id, action, comment, created_at)
        VALUES (#{action.requestId}, #{action.actorUserId}, #{action.action},
            #{action.comment}, #{action.createdAt})
        """)
    int insertAction(@Param("action") NewAction action);
}
