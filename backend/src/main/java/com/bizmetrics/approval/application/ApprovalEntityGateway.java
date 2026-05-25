package com.bizmetrics.approval.application;

import com.bizmetrics.platform.identity.User;

/** Approval boundary for validating and synchronizing polymorphic business entities. */
public interface ApprovalEntityGateway {
    void validateReference(User applicant, long companyId, String entityType, Long entityId);

    void synchronizeStatus(String authorization, String entityType, Long entityId, String status);
}
