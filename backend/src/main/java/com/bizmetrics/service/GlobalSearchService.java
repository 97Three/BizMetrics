package com.bizmetrics.service;

import com.bizmetrics.platform.tenant.Company;
import com.bizmetrics.platform.access.AccessContextService;
import com.bizmetrics.platform.access.AccessContextView;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.product.ProductModuleCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalSearchService {
    private final GlobalSearchMapper mapper;
    private final AccessContextService accessContexts;
    private final ProductModuleCatalog modules;

    public GlobalSearchService(GlobalSearchMapper mapper, AccessContextService accessContexts, ProductModuleCatalog modules) {
        this.mapper = mapper;
        this.accessContexts = accessContexts;
        this.modules = modules;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SearchResponse search(String authorization, Long companyId, String keyword, Integer requestedLimit) {
        AccessContextView access = accessContexts.resolve(authorization, companyId);
        User user = access.actor();
        Company company = access.company();
        boolean companyWide = companyWideScope(access.scope());
        Long departmentId = "department".equals(access.scope()) ? access.departmentId() : null;
        boolean operationsReadable = hasAny(access.permissions(), "operations.read", "operations.write", "reports.read");
        boolean financeReadable = companyWide && hasAny(access.permissions(), "finance.read", "finance.write");
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            return new SearchResponse(normalized, List.of());
        }
        int limit = Math.max(1, Math.min(requestedLimit == null ? 5 : requestedLimit, 10));
        String pattern = "%" + normalized + "%";
        List<SearchResult> results = new ArrayList<>();

        if (operationsReadable || financeReadable) {
            results.addAll(mapper.transactions(company.id, pattern, companyWide, user.id, departmentId, limit)
                .stream().map(row -> result("transaction", row, "/transactions?keyword=" + url(keyword))).toList());
        }

        if (financeReadable) {
            results.addAll(mapper.receipts(company.id, pattern, limit).stream()
                .map(row -> result("receipt", row, "/receipts?keyword=" + url(keyword))).toList());

            results.addAll(mapper.accounts(company.id, pattern, limit).stream()
                .map(row -> result("account", row, "/accounts?keyword=" + url(keyword))).toList());
        }

        if (companyWide && modules.isEnabled("tax") && access.permissions().contains("tax.manage")) {
            results.addAll(mapper.taxItems(company.id, pattern, limit).stream()
                .map(row -> result("tax", row, "/tax?keyword=" + url(keyword))).toList());
        }

        if (modules.isEnabled("people-core") && access.permissions().contains("people.read")) {
            results.addAll(mapper.employees(company.id, pattern, companyWide, user.id, departmentId, limit).stream()
                .map(row -> result("employee", row, "/hr/organization?keyword=" + url(keyword))).toList());
        }
        return new SearchResponse(keyword.trim(), results);
    }

    private SearchResult result(String type, GlobalSearchMapper.Row row, String path) {
        return new SearchResult(type, row.id, row.title, row.subtitle, path);
    }

    private String url(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value.trim(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private boolean hasAny(Set<String> permissions, String... candidates) {
        for (String candidate : candidates) {
            if (permissions.contains(candidate)) return true;
        }
        return false;
    }

    private boolean companyWideScope(String scope) {
        return Set.of("group", "company", "company_set", "readonly").contains(scope);
    }

    public record SearchResult(String type, long id, String title, String subtitle, String path) {}
    public record SearchResponse(String keyword, List<SearchResult> results) {}
}
