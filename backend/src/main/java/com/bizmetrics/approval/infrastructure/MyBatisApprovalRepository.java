package com.bizmetrics.approval.infrastructure;

import com.bizmetrics.approval.application.ApprovalRepository;
import com.bizmetrics.approval.application.ApprovalRepository.NewAction;
import com.bizmetrics.approval.application.ApprovalRepository.NewApproval;
import com.bizmetrics.approval.domain.ApprovalAction;
import com.bizmetrics.approval.domain.ApprovalRequest;
import com.bizmetrics.common.PageRequest;
import com.bizmetrics.common.PagedResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for approval requests, actions, and creation locks. */
@Repository
public class MyBatisApprovalRepository implements ApprovalRepository {
    private final ApprovalMapper mapper;

    public MyBatisApprovalRepository(ApprovalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PagedResponse<ApprovalRequest> findPage(long companyId, Long participantUserId,
        String status, String requestType, String keyword, PageRequest page) {
        String normalizedKeyword = Objects.toString(keyword, "").trim().toLowerCase(Locale.ROOT);
        long total = mapper.count(companyId, participantUserId, status, requestType, normalizedKeyword);
        List<ApprovalRequest> content = mapper.page(companyId, participantUserId, status,
            requestType, normalizedKeyword, page.size(), (long) page.page() * page.size());
        int totalPages = (int) Math.ceil((double) total / page.size());
        return new PagedResponse<>(content, total, totalPages, page.size(), page.page());
    }

    @Override
    public Map<String, Object> summarize(long companyId, long userId, boolean administrator) {
        ApprovalMapper.Summary row = mapper.summarize(companyId, userId, administrator);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", row.total());
        summary.put("pending", row.pending());
        summary.put("approved", row.approved());
        summary.put("rejected", row.rejected());
        summary.put("minePending", row.minePending());
        return summary;
    }

    @Override
    public Optional<ApprovalRequest> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public Optional<ApprovalRequest> findByIdForUpdate(long id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id));
    }

    @Override
    public List<ApprovalAction> findActions(long requestId) {
        return mapper.findActions(requestId);
    }

    @Override
    public void lockIdempotencyKey(long companyId, String idempotencyKey) {
        mapper.advisoryLock("approval:" + companyId + ":" + idempotencyKey);
    }

    @Override
    public Optional<ApprovalRequest> findByIdempotencyKey(long companyId, String idempotencyKey) {
        return Optional.ofNullable(mapper.findByIdempotencyKey(companyId, idempotencyKey));
    }

    @Override
    public void lockEntity(long companyId, String entityType, long entityId) {
        mapper.advisoryLock("approval:" + companyId + ":" + entityType + ":" + entityId);
    }

    @Override
    public boolean hasPendingRequest(long companyId, String entityType, long entityId) {
        return mapper.countPending(companyId, entityType, entityId) > 0;
    }

    @Override
    public boolean isValidAssignee(long companyId, long ownerId, long assigneeId) {
        return mapper.countValidAssignee(companyId, ownerId, assigneeId) > 0;
    }

    @Override
    public ApprovalRequest insert(NewApproval approval) {
        return mapper.insert(approval);
    }

    @Override
    public void updateState(long id, String status, String currentStep, String decidedAt, String updatedAt) {
        mapper.updateState(id, status, currentStep, decidedAt, updatedAt);
    }

    @Override
    public void insertAction(NewAction action) {
        mapper.insertAction(action);
    }
}
