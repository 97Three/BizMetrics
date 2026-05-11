package com.bizmetrics.accessmanagement.infrastructure;

import com.bizmetrics.accessmanagement.application.AccessManagementAuditLog;
import com.bizmetrics.accessmanagement.application.AdministratorActor;
import com.bizmetrics.platform.audit.application.AuditTrailService;
import org.springframework.stereotype.Component;

@Component
public class EnterpriseAccessManagementAuditLog implements AccessManagementAuditLog {
    private final AuditTrailService auditTrail;

    public EnterpriseAccessManagementAuditLog(AuditTrailService auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Override
    public void record(long targetUserId, String action, String description, AdministratorActor actor) {
        auditTrail.record(
            0, "user", targetUserId, action, description, actor.userId(), actor.nickname()
        );
    }
}
