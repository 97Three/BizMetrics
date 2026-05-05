package com.bizmetrics.platform.tenant;

import com.bizmetrics.people.domain.Employee;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CompanyMembershipRepository {
    private final CompanyMembershipMapper mapper;

    public CompanyMembershipRepository(CompanyMembershipMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<CompanyMembership> find(long userId, long companyId) {
        return Optional.ofNullable(mapper.find(userId, companyId));
    }

    public List<CompanyMembership> findActiveByUser(long userId) {
        return mapper.findActiveByUser(userId);
    }

    public List<CompanyMembership> findActiveByCompany(long companyId) {
        return mapper.findActiveByCompany(companyId);
    }

    public void ensureOwner(Company company) {
        upsert(company.id, company.ownerId, null, "founder", "company", "active", true);
    }

    public void synchronize(Employee employee) {
        if (employee.userId == null) return;
        upsert(
            employee.companyId,
            employee.userId,
            employee.departmentId,
            defaultText(employee.accessRole, "employee"),
            defaultText(employee.accessScope, "self"),
            "departed".equals(employee.status) ? "inactive" : "active",
            false
        );
    }

    private void upsert(
        long companyId,
        long userId,
        Long departmentId,
        String role,
        String scope,
        String status,
        boolean forceOwner
    ) {
        mapper.upsert(companyId, userId, departmentId, role, scope, status, forceOwner);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
