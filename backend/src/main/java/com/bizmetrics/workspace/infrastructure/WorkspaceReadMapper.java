package com.bizmetrics.workspace.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkspaceReadMapper {
    @Select("""
        SELECT COALESCE(SUM(CASE WHEN t.type = 1 THEN t.amount ELSE 0 END), 0) AS income,
            GREATEST(COALESCE(SUM(CASE
                WHEN t.type = 2 THEN t.amount
                WHEN t.type = 3 THEN -t.amount
                ELSE 0 END), 0), 0) AS expense,
            COUNT(*) FILTER (WHERE t.type = 2
                AND (t.amount >= 10000 OR BTRIM(COALESCE(t.note, '')) = '')) AS review_count
        FROM transactions t
        WHERE t.company_id = #{companyId} AND t.date BETWEEN #{start} AND #{end}
          AND (#{scope.companyWide} OR t.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL AND EXISTS (
                  SELECT 1 FROM employees scoped_employee
                  WHERE scoped_employee.company_id = t.company_id
                    AND scoped_employee.user_id = t.user_id
                    AND scoped_employee.department_id = #{scope.departmentId}
                    AND scoped_employee.status <> 'departed')))
        """)
    WorkspaceReadRepository.OperatingMetrics operatingMetrics(@Param("companyId") long companyId,
        @Param("start") LocalDate start, @Param("end") LocalDate end,
        @Param("scope") WorkspaceReadRepository.DataScope scope);

    @Select("""
        SELECT COALESCE(SUM(CASE WHEN status = 1 AND type NOT IN ('debt', 'credit')
            THEN available_balance ELSE 0 END), 0) AS available_cash,
            COUNT(*) FILTER (WHERE status = 1 AND reconciliation_status <> 'reconciled') AS reconciliation_issues,
            COUNT(*) FILTER (WHERE status = 1 AND risk_level IN ('high', 'critical')) AS risk_issues
        FROM accounts WHERE company_id = #{companyId}
        """)
    FinanceRow financeMetrics(long companyId);

    @Select("""
        SELECT COUNT(*) FROM receipt_vouchers WHERE company_id = #{companyId}
          AND status <> 'archived'
          AND (approval_status IN ('pending', 'rejected')
            OR accounting_status NOT IN ('completed', 'posted')
            OR file_name IS NULL OR file_name = '')
        """)
    int evidenceIssueCount(long companyId);

    @Select("""
        SELECT COUNT(*) FROM approval_requests WHERE company_id = #{companyId}
          AND status = 'pending'
          AND (#{companyWide} OR applicant_user_id = #{userId} OR assignee_user_id = #{userId})
        """)
    int pendingApprovalCount(@Param("companyId") long companyId, @Param("userId") long userId,
                             @Param("companyWide") boolean companyWide);

    @Select("""
        SELECT COUNT(*) FILTER (WHERE next_execution < #{today}) AS overdue_count,
            COUNT(*) FILTER (WHERE next_execution BETWEEN #{today} AND #{weekEnd}) AS upcoming_count
        FROM recurring_items item WHERE item.company_id = #{companyId} AND item.status = 1
          AND (#{scope.companyWide} OR item.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL AND EXISTS (
                  SELECT 1 FROM employees scoped_employee
                  WHERE scoped_employee.company_id = item.company_id
                    AND scoped_employee.user_id = item.user_id
                    AND scoped_employee.department_id = #{scope.departmentId}
                    AND scoped_employee.status <> 'departed')))
        """)
    RecurringRow recurringMetrics(@Param("companyId") long companyId, @Param("today") LocalDate today,
        @Param("weekEnd") LocalDate weekEnd, @Param("scope") WorkspaceReadRepository.DataScope scope);

    @Select("""
        SELECT t.id, t.type, t.amount, t.date, t.note,
            COALESCE(c.name, '未分类') AS category_name,
            COALESCE(a.name, '未关联账户') AS account_name
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.company_id = #{companyId}
          AND (#{scope.companyWide} OR t.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL AND EXISTS (
                  SELECT 1 FROM employees scoped_employee
                  WHERE scoped_employee.company_id = t.company_id
                    AND scoped_employee.user_id = t.user_id
                    AND scoped_employee.department_id = #{scope.departmentId}
                    AND scoped_employee.status <> 'departed')))
        ORDER BY t.date DESC, t.id DESC LIMIT #{limit}
        """)
    List<TransactionRow> recentTransactions(@Param("companyId") long companyId, @Param("limit") int limit,
        @Param("scope") WorkspaceReadRepository.DataScope scope);

    @Select("""
        SELECT id, name, next_execution FROM recurring_items item
        WHERE item.company_id = #{companyId} AND item.status = 1
          AND item.next_execution <= #{through}
          AND (#{scope.companyWide} OR item.user_id = #{scope.actorUserId} OR (
              CAST(#{scope.departmentId} AS BIGINT) IS NOT NULL AND EXISTS (
                  SELECT 1 FROM employees scoped_employee
                  WHERE scoped_employee.company_id = item.company_id
                    AND scoped_employee.user_id = item.user_id
                    AND scoped_employee.department_id = #{scope.departmentId}
                    AND scoped_employee.status <> 'departed')))
        ORDER BY item.next_execution, item.id LIMIT #{limit}
        """)
    List<UpcomingRow> upcomingItems(@Param("companyId") long companyId, @Param("through") LocalDate through,
        @Param("limit") int limit, @Param("scope") WorkspaceReadRepository.DataScope scope);

    record FinanceRow(BigDecimal availableCash, int reconciliationIssues, int riskIssues) {}
    record RecurringRow(int overdueCount, int upcomingCount) {}
    record TransactionRow(long id, int type, BigDecimal amount, LocalDate date, String note,
                          String categoryName, String accountName) {}
    record UpcomingRow(String id, String name, LocalDate nextExecution) {}
}

