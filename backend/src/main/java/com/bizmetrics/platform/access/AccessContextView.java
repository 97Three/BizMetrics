package com.bizmetrics.platform.access;

import com.bizmetrics.platform.tenant.Company;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.product.ProductModuleCatalog.ProductModules;
import java.util.List;
import java.util.Set;

/** Stable frontend contract for identity, tenant and capability-aware UI. */
public record AccessContextView(
    User actor,
    Company company,
    List<Company> companies,
    String role,
    String scope,
    Long departmentId,
    Set<String> permissions,
    ProductModules modules
) {
}
