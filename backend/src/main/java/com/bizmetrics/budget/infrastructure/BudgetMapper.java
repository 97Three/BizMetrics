package com.bizmetrics.budget.infrastructure;

import com.bizmetrics.budget.application.BudgetRepository.CategoryRef;
import com.bizmetrics.budget.domain.Budget;
import com.bizmetrics.operations.domain.TransactionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BudgetMapper {
    String PROJECTED_SELECT = """
        SELECT b.*, c.name AS category_name, c.icon AS category_icon,
            COALESCE(SUM(CASE WHEN t.type = 2 THEN t.amount
                WHEN t.type = 3 THEN -t.amount ELSE 0 END), 0) AS computed_spent,
            COALESCE((SELECT SUM(reservation.amount) FROM budget_reservations reservation
                WHERE reservation.budget_id = b.id AND reservation.status = 'reserved'), 0) AS computed_reserved
        FROM budgets b LEFT JOIN categories c ON c.id = b.category_id
        LEFT JOIN transactions t ON t.company_id = b.company_id AND t.type IN (2, 3)
            AND (b.ledger_id IS NULL OR t.family_id = b.ledger_id)
            AND (b.category_id IS NULL OR t.category_id = b.category_id)
            AND ((t.type = 3 AND t.budget_id = b.id)
                OR t.date BETWEEN b.start_date AND b.end_date)
        """;

    @Results(id = "projectedBudget", value = {
        @Result(column = "computed_spent", property = "spent"),
        @Result(column = "computed_reserved", property = "reservedAmount"),
        @Result(column = "created_at", property = "createdAt", typeHandler = OffsetDateTimeStringTypeHandler.class),
        @Result(column = "updated_at", property = "updatedAt", typeHandler = OffsetDateTimeStringTypeHandler.class)
    })
    @Select(PROJECTED_SELECT + " WHERE b.company_id = #{companyId} GROUP BY b.id, c.name, c.icon ORDER BY b.id")
    List<Budget> findByCompany(@Param("companyId") long companyId);

    @ResultMap("projectedBudget")
    @Select(PROJECTED_SELECT + " WHERE b.company_id = #{companyId} AND b.id = #{id} GROUP BY b.id, c.name, c.icon")
    Budget findById(@Param("companyId") long companyId, @Param("id") long id);

    @Results({
        @Result(column = "created_at", property = "createdAt", typeHandler = OffsetDateTimeStringTypeHandler.class),
        @Result(column = "updated_at", property = "updatedAt", typeHandler = OffsetDateTimeStringTypeHandler.class)
    })
    @Select("SELECT * FROM budgets WHERE id = #{id} FOR UPDATE")
    Budget findByIdForUpdate(@Param("id") long id);

    @Select(value = """
        INSERT INTO budgets (name, amount, start_date, end_date, warning_threshold, status,
            spent, remaining_amount, usage_rate, warning_reached, risk_level, risk_message,
            user_id, ledger_id, category_id, created_at, updated_at, company_id)
        VALUES (#{budget.name}, COALESCE(#{budget.amount}, 0), CAST(#{budget.startDate} AS date),
            CAST(#{budget.endDate} AS date), #{budget.warningThreshold}, #{budget.status},
            COALESCE(#{budget.spent}, 0), COALESCE(#{budget.remainingAmount}, 0),
            #{budget.usageRate}, #{budget.warningReached}, #{budget.riskLevel},
            #{budget.riskMessage}, #{budget.userId}, #{budget.ledgerId}, #{budget.categoryId},
            CAST(#{budget.createdAt} AS timestamptz), CAST(#{budget.updatedAt} AS timestamptz),
            #{budget.companyId}) RETURNING id
        """, affectData = true)
    Long insert(@Param("budget") Budget budget);

    @Update("""
        UPDATE budgets SET name = #{budget.name}, amount = COALESCE(#{budget.amount}, 0),
            start_date = CAST(#{budget.startDate} AS date), end_date = CAST(#{budget.endDate} AS date),
            warning_threshold = #{budget.warningThreshold}, status = #{budget.status},
            spent = COALESCE(#{budget.spent}, 0), remaining_amount = COALESCE(#{budget.remainingAmount}, 0),
            usage_rate = #{budget.usageRate}, warning_reached = #{budget.warningReached},
            risk_level = #{budget.riskLevel}, risk_message = #{budget.riskMessage},
            user_id = #{budget.userId}, ledger_id = #{budget.ledgerId}, category_id = #{budget.categoryId},
            created_at = CAST(#{budget.createdAt} AS timestamptz),
            updated_at = CAST(#{budget.updatedAt} AS timestamptz),
            company_id = #{budget.companyId}, version = version + 1
        WHERE id = #{budget.id} AND version = #{budget.version}
        """)
    int update(@Param("budget") Budget budget);

    @Delete("DELETE FROM budgets WHERE id = #{id}")
    int delete(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM transactions WHERE budget_id = #{id}")
    long countTransactions(@Param("id") long id);

    @ConstructorArgs({@Arg(column = "id", javaType = long.class),
        @Arg(column = "company_id", javaType = Long.class),
        @Arg(column = "type", javaType = String.class)})
    @Select("SELECT id, company_id, type FROM categories WHERE id = #{id}")
    CategoryRef category(@Param("id") long id);

    @Select("""
        SELECT id FROM budgets WHERE company_id = #{transaction.companyId} AND status <> 0
            AND (ledger_id IS NULL OR ledger_id = #{transaction.familyId})
            AND (category_id IS NULL OR category_id = #{transaction.categoryId})
            AND CAST(#{transaction.date} AS date) BETWEEN start_date AND end_date
        ORDER BY CASE WHEN category_id = #{transaction.categoryId} THEN 0 ELSE 1 END, id LIMIT 1
        """)
    Long matchingBudgetId(@Param("transaction") TransactionRecord transaction);

    @Update("""
        UPDATE budgets SET spent = COALESCE(#{budget.spent}, 0),
            remaining_amount = COALESCE(#{budget.remainingAmount}, 0),
            usage_rate = #{budget.usageRate}, warning_reached = #{budget.warningReached},
            risk_level = #{budget.riskLevel}, risk_message = #{budget.riskMessage},
            status = #{budget.status}, updated_at = CAST(#{budget.updatedAt} AS timestamptz),
            version = version + 1 WHERE id = #{budget.id} AND version = #{budget.version}
            AND (spent IS DISTINCT FROM COALESCE(#{budget.spent}, 0)
              OR remaining_amount IS DISTINCT FROM COALESCE(#{budget.remainingAmount}, 0)
              OR usage_rate IS DISTINCT FROM #{budget.usageRate}
              OR warning_reached IS DISTINCT FROM #{budget.warningReached}
              OR risk_level IS DISTINCT FROM #{budget.riskLevel}
              OR risk_message IS DISTINCT FROM #{budget.riskMessage}
              OR status IS DISTINCT FROM #{budget.status})
        """)
    int persistProjection(@Param("budget") Budget budget);

    @Select("SELECT version FROM budgets WHERE id = #{id}")
    Long version(@Param("id") long id);
}
