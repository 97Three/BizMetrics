package com.bizmetrics.workforce.infrastructure;

import com.bizmetrics.workforce.infrastructure.WorkforceCostReadRepository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkforceCostMapper {
    @Select("""
        SELECT id, status FROM payroll_runs
        WHERE company_id = #{companyId} AND period = #{period}
        ORDER BY id DESC LIMIT 1
        """)
    PayrollRunRef payrollRun(@Param("companyId") long companyId, @Param("period") String period);

    @Select("""
        SELECT COUNT(*) AS employee_count,
            COALESCE(SUM(CAST(NULLIF(item.salary, '') AS NUMERIC)), 0) AS salary,
            COALESCE(SUM(GREATEST(CAST(NULLIF(item.payable_salary, '') AS NUMERIC)
                - CAST(NULLIF(item.salary, '') AS NUMERIC), 0)), 0) AS overtime,
            COALESCE(SUM(CAST(NULLIF(item.social_company_amount, '') AS NUMERIC)), 0) AS employer_social,
            COALESCE(SUM(CAST(NULLIF(item.housing_company_amount, '') AS NUMERIC)), 0) AS employer_housing,
            COALESCE(SUM(CAST(NULLIF(item.company_cost, '') AS NUMERIC)), 0) AS total
        FROM payroll_run_items item
        LEFT JOIN employees employee ON employee.id = item.employee_id AND employee.company_id = item.company_id
        WHERE item.company_id = #{companyId} AND item.run_id = #{runId}
          AND (#{scope.companyWide} OR employee.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL
              AND employee.department_id = #{scope.departmentId}))
        """)
    CostAggregate payrollCost(@Param("companyId") long companyId, @Param("runId") long runId,
                              @Param("scope") DataScope scope);

    @Select("""
        SELECT COUNT(*) AS employee_count,
            COALESCE(SUM(employee.salary), 0) AS salary,
            COALESCE(SUM(employee.overtime_pay), 0) AS overtime,
            COALESCE(SUM(employee.social_insurance_company_amount), 0) AS employer_social,
            COALESCE(SUM(employee.housing_fund_company_amount), 0) AS employer_housing,
            COALESCE(SUM(employee.monthly_cost), 0) AS total
        FROM employees employee
        WHERE employee.company_id = #{companyId} AND employee.status IN ('active', 'probation')
          AND (#{scope.companyWide} OR employee.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL
              AND employee.department_id = #{scope.departmentId}))
        """)
    CostAggregate estimatedCost(@Param("companyId") long companyId, @Param("scope") DataScope scope);

    @Select("""
        SELECT COUNT(*) FILTER (WHERE employee.status = 'active') AS active_count,
            COUNT(*) FILTER (WHERE employee.status = 'probation') AS probation_count,
            COUNT(*) FILTER (WHERE employee.status = 'onboarding') AS onboarding_count,
            COUNT(*) FILTER (WHERE employee.leave_date BETWEEN #{periodStart} AND #{periodEnd}) AS departed_count
        FROM employees employee
        WHERE employee.company_id = #{companyId}
          AND (#{scope.companyWide} OR employee.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL
              AND employee.department_id = #{scope.departmentId}))
        """)
    HeadcountRow headcount(@Param("companyId") long companyId, @Param("periodStart") LocalDate periodStart,
                           @Param("periodEnd") LocalDate periodEnd, @Param("scope") DataScope scope);

    @Select("""
        SELECT employee.department_id,
            COALESCE(NULLIF(department.name, ''), NULLIF(item.department_name, ''), '未分配部门') AS department_name,
            COALESCE(department.budget, 0) AS budget,
            COUNT(*) AS employee_count,
            COALESCE(SUM(CAST(NULLIF(item.salary, '') AS NUMERIC)), 0) AS salary,
            COALESCE(SUM(GREATEST(CAST(NULLIF(item.payable_salary, '') AS NUMERIC)
                - CAST(NULLIF(item.salary, '') AS NUMERIC), 0)), 0) AS overtime,
            COALESCE(SUM(CAST(NULLIF(item.social_company_amount, '') AS NUMERIC)), 0) AS employer_social,
            COALESCE(SUM(CAST(NULLIF(item.housing_company_amount, '') AS NUMERIC)), 0) AS employer_housing,
            COALESCE(SUM(CAST(NULLIF(item.company_cost, '') AS NUMERIC)), 0) AS total
        FROM payroll_run_items item
        LEFT JOIN employees employee ON employee.id = item.employee_id AND employee.company_id = item.company_id
        LEFT JOIN departments department ON department.id = employee.department_id
            AND department.company_id = item.company_id
        WHERE item.company_id = #{companyId} AND item.run_id = #{runId}
          AND (#{scope.companyWide} OR employee.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL
              AND employee.department_id = #{scope.departmentId}))
        GROUP BY employee.department_id, department.name, item.department_name, department.budget
        ORDER BY total DESC, department_name
        """)
    List<DepartmentRow> payrollDepartments(@Param("companyId") long companyId, @Param("runId") long runId,
                                            @Param("scope") DataScope scope);

    @Select("""
        SELECT employee.department_id,
            COALESCE(NULLIF(department.name, ''), '未分配部门') AS department_name,
            COALESCE(department.budget, 0) AS budget,
            COUNT(*) AS employee_count,
            COALESCE(SUM(employee.salary), 0) AS salary,
            COALESCE(SUM(employee.overtime_pay), 0) AS overtime,
            COALESCE(SUM(employee.social_insurance_company_amount), 0) AS employer_social,
            COALESCE(SUM(employee.housing_fund_company_amount), 0) AS employer_housing,
            COALESCE(SUM(employee.monthly_cost), 0) AS total
        FROM employees employee
        LEFT JOIN departments department ON department.id = employee.department_id
            AND department.company_id = employee.company_id
        WHERE employee.company_id = #{companyId} AND employee.status IN ('active', 'probation')
          AND (#{scope.companyWide} OR employee.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL
              AND employee.department_id = #{scope.departmentId}))
        GROUP BY employee.department_id, department.name, department.budget
        ORDER BY total DESC, department_name
        """)
    List<DepartmentRow> estimatedDepartments(@Param("companyId") long companyId,
                                              @Param("scope") DataScope scope);

    @Select("""
        SELECT GREATEST(COALESCE(SUM(CASE
            WHEN transaction_record.type = 2 THEN transaction_record.amount
            WHEN transaction_record.type = 3 THEN -transaction_record.amount
            ELSE 0 END), 0), 0)
        FROM transactions transaction_record
        WHERE transaction_record.company_id = #{companyId}
          AND transaction_record.date BETWEEN #{periodStart} AND #{periodEnd}
          AND (#{scope.companyWide} OR transaction_record.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL AND EXISTS (
                  SELECT 1 FROM employees scoped_employee
                  WHERE scoped_employee.company_id = transaction_record.company_id
                    AND scoped_employee.user_id = transaction_record.user_id
                    AND scoped_employee.department_id = #{scope.departmentId})))
        """)
    BigDecimal operatingExpense(@Param("companyId") long companyId, @Param("periodStart") LocalDate periodStart,
                                @Param("periodEnd") LocalDate periodEnd, @Param("scope") DataScope scope);

    @Select("""
        SELECT payroll.id AS run_id, payroll.period, payroll.status,
            COUNT(*) AS employee_count,
            COALESCE(SUM(CAST(NULLIF(item.company_cost, '') AS NUMERIC)), 0) AS total
        FROM payroll_runs payroll
        JOIN payroll_run_items item ON item.run_id = payroll.id AND item.company_id = payroll.company_id
        LEFT JOIN employees employee ON employee.id = item.employee_id AND employee.company_id = item.company_id
        WHERE payroll.company_id = #{companyId} AND payroll.period <= #{throughPeriod}
          AND (#{scope.companyWide} OR employee.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL
              AND employee.department_id = #{scope.departmentId}))
        GROUP BY payroll.id, payroll.period, payroll.status
        ORDER BY payroll.period DESC, payroll.id DESC LIMIT 6
        """)
    List<TrendAggregate> payrollTrend(@Param("companyId") long companyId,
                                      @Param("throughPeriod") String throughPeriod,
                                      @Param("scope") DataScope scope);

    record HeadcountRow(int activeCount, int probationCount, int onboardingCount, int departedCount) {}
    record DepartmentRow(Long departmentId, String departmentName, BigDecimal budget, int employeeCount,
                         BigDecimal salary, BigDecimal overtime, BigDecimal employerSocial,
                         BigDecimal employerHousing, BigDecimal total) {
        DepartmentAggregate aggregate() {
            return new DepartmentAggregate(departmentId, departmentName, budget,
                new CostAggregate(employeeCount, salary, overtime, employerSocial, employerHousing, total));
        }
    }
}

