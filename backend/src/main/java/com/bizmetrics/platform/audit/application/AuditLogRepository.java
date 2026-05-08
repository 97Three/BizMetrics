package com.bizmetrics.platform.audit.application;

import com.bizmetrics.common.PageRequest;
import com.bizmetrics.common.PagedResponse;
import com.bizmetrics.platform.audit.domain.AuditEvent;
import com.bizmetrics.platform.audit.domain.AuditLog;
import com.bizmetrics.platform.audit.domain.AuditLogSearchCriteria;
import java.util.List;

/** Persistence port exposing append and read operations, but no audit mutation or deletion. */
public interface AuditLogRepository {
    AuditLog append(AuditEvent event);

    PagedResponse<AuditLog> findPage(AuditLogSearchCriteria criteria, PageRequest pageRequest);

    List<AuditLog> findByEntity(long companyId, String entityType, long entityId);

    boolean existsByEntityType(String entityType);
}
