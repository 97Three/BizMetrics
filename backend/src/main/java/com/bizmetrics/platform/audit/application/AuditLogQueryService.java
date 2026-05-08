package com.bizmetrics.platform.audit.application;

import com.bizmetrics.common.PagedResponse;
import com.bizmetrics.platform.audit.api.AuditLogQueryRequest;
import com.bizmetrics.platform.audit.domain.AuditLog;
import com.bizmetrics.platform.identity.ActorContext;
import com.bizmetrics.service.support.AccessControlService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Administrator-only application boundary for consistent, database-paged audit reads. */
@Service
public class AuditLogQueryService {
    private final AuditLogRepository repository;
    private final AccessControlService accessControl;

    public AuditLogQueryService(AuditLogRepository repository, AccessControlService accessControl) {
        this.repository = repository;
        this.accessControl = accessControl;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PagedResponse<AuditLog> list(ActorContext actor, AuditLogQueryRequest request) {
        accessControl.requireAdmin(actor.legacyAuthorization());
        return repository.findPage(request.criteria(), request.pageRequest());
    }
}
