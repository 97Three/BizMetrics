package com.bizmetrics.approval.application;

import com.bizmetrics.approval.domain.ApprovalAction;
import com.bizmetrics.approval.domain.ApprovalRequest;
import com.bizmetrics.common.PageRequest;
import com.bizmetrics.common.PagedResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence and transaction-lock boundary for the approval aggregate. */
public interface ApprovalRepository {
    PagedResponse<ApprovalRequest> findPage(
        long companyId,
        Long participantUserId,
        String status,
        String requestType,
        String keyword,
        PageRequest page
    );

    Map<String, Object> summarize(long companyId, long userId, boolean administrator);

    Optional<ApprovalRequest> findById(long id);

    Optional<ApprovalRequest> findByIdForUpdate(long id);

    List<ApprovalAction> findActions(long requestId);

    void lockIdempotencyKey(long companyId, String idempotencyKey);

    Optional<ApprovalRequest> findByIdempotencyKey(long companyId, String idempotencyKey);

    void lockEntity(long companyId, String entityType, long entityId);

    boolean hasPendingRequest(long companyId, String entityType, long entityId);

    boolean isValidAssignee(long companyId, long ownerId, long assigneeId);

    ApprovalRequest insert(NewApproval approval);

    void updateState(long id, String status, String currentStep, String decidedAt, String updatedAt);

    void insertAction(NewAction action);

    record NewApproval(
        long companyId,
        String requestType,
        String entityType,
        Long entityId,
        String title,
        BigDecimal amount,
        long applicantUserId,
        Long assigneeUserId,
        String status,
        String currentStep,
        String description,
        String createdAt,
        String updatedAt,
        String idempotencyKey
    ) {
    }

    record NewAction(long requestId, long actorUserId, String action, String comment, String createdAt) {
    }
}
