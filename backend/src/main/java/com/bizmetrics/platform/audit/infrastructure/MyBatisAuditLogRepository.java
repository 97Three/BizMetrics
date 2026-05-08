package com.bizmetrics.platform.audit.infrastructure;

import com.bizmetrics.common.PageRequest;
import com.bizmetrics.common.PagedResponse;
import com.bizmetrics.platform.audit.application.AuditLogRepository;
import com.bizmetrics.platform.audit.domain.AuditEvent;
import com.bizmetrics.platform.audit.domain.AuditLog;
import com.bizmetrics.platform.audit.domain.AuditLogSearchCriteria;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAuditLogRepository implements AuditLogRepository {
    private final AuditLogMapper mapper;

    public MyBatisAuditLogRepository(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AuditLog append(AuditEvent event) {
        Long id = mapper.append(event);
        if (id == null) {
            throw new IllegalStateException("Audit insert did not return an identifier");
        }
        return new AuditLog(
            id, event.companyId(), event.entityType(), event.entityId(), event.action(), event.summary(),
            event.actorUserId(), event.actorName(), event.createdAt()
        );
    }

    @Override
    public PagedResponse<AuditLog> findPage(AuditLogSearchCriteria criteria, PageRequest pageRequest) {
        long totalElements = mapper.count(criteria);
        List<AuditLog> content = mapper.findPage(criteria, pageRequest.size(),
            (long) pageRequest.page() * pageRequest.size());
        int totalPages = (int) Math.ceil((double) totalElements / pageRequest.size());
        return new PagedResponse<>(content, totalElements, totalPages, pageRequest.size(), pageRequest.page());
    }

    @Override
    public List<AuditLog> findByEntity(long companyId, String entityType, long entityId) {
        return mapper.findByEntity(companyId, entityType, entityId);
    }

    @Override
    public boolean existsByEntityType(String entityType) {
        return mapper.existsByEntityType(entityType);
    }

}
