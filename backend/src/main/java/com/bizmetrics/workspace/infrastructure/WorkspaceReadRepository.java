package com.bizmetrics.workspace.infrastructure;

import com.bizmetrics.workspace.api.WorkspaceView.RecentTransaction;
import com.bizmetrics.workspace.api.WorkspaceView.UpcomingItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceReadRepository {
    private final WorkspaceReadMapper mapper;

    public WorkspaceReadRepository(WorkspaceReadMapper mapper) {
        this.mapper = mapper;
    }

    public OperatingMetrics operatingMetrics(long companyId, LocalDate start, LocalDate end, DataScope scope) {
        return mapper.operatingMetrics(companyId, start, end, scope);
    }

    public FinanceMetrics financeMetrics(long companyId) {
        WorkspaceReadMapper.FinanceRow row = mapper.financeMetrics(companyId);
        return new FinanceMetrics(row.availableCash(), row.reconciliationIssues() + row.riskIssues());
    }

    public int evidenceIssueCount(long companyId) {
        return mapper.evidenceIssueCount(companyId);
    }

    public int pendingApprovalCount(long companyId, long userId, boolean companyWide) {
        return mapper.pendingApprovalCount(companyId, userId, companyWide);
    }

    public RecurringMetrics recurringMetrics(long companyId, LocalDate today, DataScope scope) {
        WorkspaceReadMapper.RecurringRow row = mapper.recurringMetrics(companyId, today, today.plusDays(7), scope);
        return new RecurringMetrics(row.overdueCount(), row.upcomingCount());
    }

    public List<RecentTransaction> recentTransactions(long companyId, int limit, DataScope scope) {
        return mapper.recentTransactions(companyId, Math.max(1, Math.min(limit, 20)), scope).stream()
            .map(row -> new RecentTransaction(row.id(), row.type(), row.amount(), row.date().toString(),
                row.note(), row.categoryName(), row.accountName()))
            .toList();
    }

    public List<UpcomingItem> upcomingItems(long companyId, LocalDate today, int limit, DataScope scope) {
        return mapper.upcomingItems(companyId, today.plusDays(14), Math.max(1, Math.min(limit, 20)), scope)
            .stream().map(row -> new UpcomingItem(row.id(), row.name(), row.nextExecution().toString(),
                row.nextExecution().isBefore(today), "/recurring"))
            .toList();
    }

    public record OperatingMetrics(BigDecimal income, BigDecimal expense, int reviewCount) {}
    public record FinanceMetrics(BigDecimal availableCash, int issueCount) {}
    public record RecurringMetrics(int overdueCount, int upcomingCount) {}
    public record DataScope(long actorUserId, Long departmentId, boolean companyWide) {}
}
