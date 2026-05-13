package com.bizmetrics.operations.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TransactionWriteMapper {
    record Row(long id, long version, long userId, Long familyId, int type, BigDecimal amount,
               long categoryId, long accountId, LocalDate date, String note, Long originalTransactionId,
               BigDecimal refundedAmount, boolean isRefundable, Long budgetId, OffsetDateTime createdAt,
               OffsetDateTime updatedAt, Long companyId, String idempotencyKey) {
    }

    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{lockName}, 0))) locked")
    int lockIdempotency(@Param("lockName") String lockName);

    @Select("SELECT id FROM transactions WHERE company_id = #{companyId} AND idempotency_key = #{key}")
    Long findByIdempotency(@Param("companyId") long companyId, @Param("key") String key);

    @Select("SELECT id FROM transactions WHERE id = #{id} FOR UPDATE")
    Long findForUpdate(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM transactions WHERE original_transaction_id = #{id}")
    int countRefunds(@Param("id") long id);

    @Select(value = """
        INSERT INTO transactions (user_id, family_id, type, amount, category_id, account_id, date, note,
            original_transaction_id, refunded_amount, is_refundable, budget_id, created_at, updated_at,
            company_id, idempotency_key)
        VALUES (#{row.userId}, #{row.familyId}, #{row.type}, #{row.amount}, #{row.categoryId},
            #{row.accountId}, #{row.date}, #{row.note}, #{row.originalTransactionId},
            #{row.refundedAmount}, #{row.isRefundable}, #{row.budgetId}, #{row.createdAt},
            #{row.updatedAt}, #{row.companyId}, #{row.idempotencyKey}) RETURNING id
        """, affectData = true)
    Long insert(@Param("row") Row row);

    @Update("""
        UPDATE transactions SET user_id = #{row.userId}, family_id = #{row.familyId},
            type = #{row.type}, amount = #{row.amount}, category_id = #{row.categoryId},
            account_id = #{row.accountId}, date = #{row.date}, note = #{row.note},
            original_transaction_id = #{row.originalTransactionId}, refunded_amount = #{row.refundedAmount},
            is_refundable = #{row.isRefundable}, budget_id = #{row.budgetId},
            created_at = #{row.createdAt}, updated_at = #{row.updatedAt}, company_id = #{row.companyId},
            idempotency_key = #{row.idempotencyKey}, version = version + 1
        WHERE id = #{row.id} AND version = #{row.version}
        """)
    int update(@Param("row") Row row);

    @Delete("DELETE FROM transactions WHERE id = #{id} AND version = #{version}")
    int delete(@Param("id") long id, @Param("version") long version);
}
