package com.bizmetrics.budget.infrastructure;

import com.bizmetrics.budget.domain.BudgetReservation;
import com.bizmetrics.budget.domain.BudgetReservationCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BudgetReservationMapper {
    record Candidate(long id, BigDecimal amount, Long ledgerId, Long categoryId,
                     LocalDate startDate, LocalDate endDate) {
    }
    record Target(long companyId, Long budgetId, int type, BigDecimal amount) {
    }
    record Row(long id, long version, long companyId, long budgetId, Long transactionId,
               String referenceKey, BigDecimal amount, String status, long createdByUserId,
               OffsetDateTime confirmedAt, OffsetDateTime releasedAt, String releaseReason,
               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class),
        @Arg(column = "amount", javaType = BigDecimal.class),
        @Arg(column = "ledger_id", javaType = Long.class),
        @Arg(column = "category_id", javaType = Long.class),
        @Arg(column = "start_date", javaType = LocalDate.class),
        @Arg(column = "end_date", javaType = LocalDate.class)
    })
    @Select("""
        SELECT id, CAST(amount AS NUMERIC) AS amount, ledger_id, category_id, start_date, end_date
        FROM budgets WHERE company_id = #{command.companyId} AND status IN (1, 3)
          AND (ledger_id IS NULL OR ledger_id = #{command.ledgerId})
          AND (category_id IS NULL OR category_id = #{command.categoryId})
          AND #{command.transactionDate} BETWEEN start_date AND end_date
        ORDER BY CASE WHEN category_id = #{command.categoryId} THEN 0 ELSE 1 END,
                 CASE WHEN ledger_id = #{command.ledgerId} THEN 0 ELSE 1 END, id
        LIMIT 1 FOR UPDATE
        """)
    Candidate findCandidate(@Param("command") BudgetReservationCommand command);

    @Select("""
        SELECT COALESCE(SUM(CASE WHEN type = 2 THEN amount WHEN type = 3 THEN -amount ELSE 0 END), 0)
        FROM transactions WHERE company_id = #{command.companyId} AND type IN (2, 3)
          AND (CAST(#{candidate.ledgerId} AS BIGINT) IS NULL OR family_id = #{candidate.ledgerId})
          AND (CAST(#{candidate.categoryId} AS BIGINT) IS NULL OR category_id = #{candidate.categoryId})
          AND (CAST(#{command.replacedTransactionId} AS BIGINT) IS NULL
               OR id <> #{command.replacedTransactionId})
          AND ((type = 3 AND budget_id = #{candidate.id})
               OR (type = 2 AND date BETWEEN #{candidate.startDate} AND #{candidate.endDate}))
        """)
    BigDecimal committed(@Param("command") BudgetReservationCommand command,
                         @Param("candidate") Candidate candidate);

    @Select("""
        SELECT COALESCE(SUM(amount), 0) FROM budget_reservations
        WHERE budget_id = #{budgetId} AND status = 'reserved'
        """)
    BigDecimal reserved(@Param("budgetId") long budgetId);

    @Results(id = "reservation", value = {})
    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class),
        @Arg(column = "version", javaType = long.class),
        @Arg(column = "company_id", javaType = long.class),
        @Arg(column = "budget_id", javaType = long.class),
        @Arg(column = "transaction_id", javaType = Long.class),
        @Arg(column = "reference_key", javaType = String.class),
        @Arg(column = "amount", javaType = BigDecimal.class),
        @Arg(column = "status", javaType = String.class),
        @Arg(column = "created_by_user_id", javaType = long.class),
        @Arg(column = "confirmed_at", javaType = OffsetDateTime.class),
        @Arg(column = "released_at", javaType = OffsetDateTime.class),
        @Arg(column = "release_reason", javaType = String.class),
        @Arg(column = "created_at", javaType = OffsetDateTime.class),
        @Arg(column = "updated_at", javaType = OffsetDateTime.class)
    })
    @Select(value = """
        INSERT INTO budget_reservations (company_id, budget_id, reference_key, amount,
            status, created_by_user_id, created_at, updated_at)
        VALUES (#{command.companyId}, #{budgetId}, #{command.referenceKey}, #{command.amount},
            'reserved', #{command.userId}, #{now}, #{now}) RETURNING *
        """, affectData = true)
    Row insert(@Param("command") BudgetReservationCommand command, @Param("budgetId") long budgetId,
               @Param("now") OffsetDateTime now);

    @ResultMap("reservation")
    @Select("""
        SELECT * FROM budget_reservations WHERE transaction_id = #{transactionId}
            AND status = 'confirmed' FOR UPDATE
        """)
    Row findConfirmedByTransaction(@Param("transactionId") long transactionId);

    @ResultMap("reservation")
    @Select("SELECT * FROM budget_reservations WHERE id = #{id}")
    Row findById(@Param("id") long id);

    @ResultMap("reservation")
    @Select("SELECT * FROM budget_reservations WHERE id = #{id} FOR UPDATE")
    Row findByIdForUpdate(@Param("id") long id);

    @ConstructorArgs({
        @Arg(column = "company_id", javaType = long.class),
        @Arg(column = "budget_id", javaType = Long.class),
        @Arg(column = "type", javaType = int.class),
        @Arg(column = "amount", javaType = BigDecimal.class)
    })
    @Select("SELECT company_id, budget_id, type, amount FROM transactions WHERE id = #{transactionId}")
    Target findConfirmationTarget(@Param("transactionId") long transactionId);

    @Update("""
        UPDATE budget_reservations SET transaction_id = #{reservation.transactionId},
            source_transaction_id = COALESCE(source_transaction_id, #{reservation.transactionId}),
            status = #{status}, confirmed_at = #{reservation.confirmedAt},
            released_at = #{reservation.releasedAt}, release_reason = #{reservation.releaseReason},
            updated_at = #{reservation.updatedAt}, version = version + 1
        WHERE id = #{reservation.id} AND version = #{reservation.version}
        """)
    int update(@Param("reservation") BudgetReservation reservation, @Param("status") String status);
}
