package com.bizmetrics.workforce.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class WorkforceCostReadRepository {
    private final WorkforceCostMapper mapper;

    public WorkforceCostReadRepository(WorkforceCostMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<PayrollRunRef> payrollRun(long companyId, String period) {
        return Optional.ofNullable(mapper.payrollRun(companyId, period));
    }

    public CostAggregate payrollCost(long companyId, long runId, DataScope scope) {
        return mapper.payrollCost(companyId, runId, scope);
    }

    public CostAggregate estimatedCost(long companyId, DataScope scope) {
        return mapper.estimatedCost(companyId, scope);
    }

    public HeadcountAggregate headcount(long companyId, LocalDate periodStart, LocalDate periodEnd, DataScope scope) {
        WorkforceCostMapper.HeadcountRow row = mapper.headcount(companyId, periodStart, periodEnd, scope);
        return new HeadcountAggregate(row.activeCount(), row.probationCount(), row.onboardingCount(), row.departedCount());
    }

    public List<DepartmentAggregate> payrollDepartments(long companyId, long runId, DataScope scope) {
        return mapper.payrollDepartments(companyId, runId, scope).stream()
            .map(WorkforceCostMapper.DepartmentRow::aggregate).toList();
    }

    public List<DepartmentAggregate> estimatedDepartments(long companyId, DataScope scope) {
        return mapper.estimatedDepartments(companyId, scope).stream()
            .map(WorkforceCostMapper.DepartmentRow::aggregate).toList();
    }

    public BigDecimal operatingExpense(long companyId, LocalDate periodStart, LocalDate periodEnd, DataScope scope) {
        BigDecimal result = mapper.operatingExpense(companyId, periodStart, periodEnd, scope);
        return result == null ? BigDecimal.ZERO : result;
    }

    public List<TrendAggregate> payrollTrend(long companyId, String throughPeriod, DataScope scope) {
        List<TrendAggregate> points = mapper.payrollTrend(companyId, throughPeriod, scope);
        Collections.reverse(points);
        return points;
    }

    public record PayrollRunRef(long id, String status) {}
    public record CostAggregate(int employeeCount, BigDecimal salary, BigDecimal overtime,
                                BigDecimal employerSocial, BigDecimal employerHousing, BigDecimal total) {}
    public record DepartmentAggregate(Long departmentId, String departmentName, BigDecimal budget, CostAggregate cost) {}
    public record HeadcountAggregate(int active, int probation, int onboarding, int departedThisMonth) {}
    public record TrendAggregate(long runId, String period, String status, int employeeCount, BigDecimal total) {}
    public record DataScope(long actorUserId, Long departmentId, boolean companyWide) {}
}
