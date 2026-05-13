package com.bizmetrics.operations.infrastructure;

import com.bizmetrics.operations.domain.TransactionRecord;
import com.bizmetrics.operations.domain.TransactionSearchCriteria;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface TransactionQueryMapper {
    record Totals(BigDecimal income, BigDecimal expense, BigDecimal refund,
                  BigDecimal pendingCollection, BigDecimal customerRefund, BigDecimal severance,
                  long rowCount, long largeCount, long reviewCount) {
    }

    @SelectProvider(type = Sql.class, method = "count")
    long count(@Param("userId") long userId, @Param("companyId") long companyId,
               @Param("criteria") TransactionSearchCriteria criteria);

    @Results(id = "transaction", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "version", property = "version"),
        @Result(column = "idempotency_key", property = "idempotencyKey"),
        @Result(column = "company_id", property = "companyId"),
        @Result(column = "user_id", property = "userId"),
        @Result(column = "family_id", property = "familyId"),
        @Result(column = "type", property = "type"),
        @Result(column = "amount", property = "amount"),
        @Result(column = "category_id", property = "categoryId"),
        @Result(column = "account_id", property = "accountId"),
        @Result(column = "date", property = "date"),
        @Result(column = "note", property = "note"),
        @Result(column = "original_transaction_id", property = "originalTransactionId"),
        @Result(column = "refunded_amount", property = "refundedAmount"),
        @Result(column = "is_refundable", property = "isRefundable"),
        @Result(column = "budget_id", property = "budgetId"),
        @Result(column = "created_at", property = "createdAt", typeHandler = OffsetDateTimeStringTypeHandler.class),
        @Result(column = "updated_at", property = "updatedAt", typeHandler = OffsetDateTimeStringTypeHandler.class),
        @Result(column = "resolved_category_name", property = "categoryName"),
        @Result(column = "resolved_category_icon", property = "categoryIcon"),
        @Result(column = "resolved_category_color", property = "categoryColor"),
        @Result(column = "resolved_account_name", property = "accountName")
    })
    @SelectProvider(type = Sql.class, method = "page")
    List<TransactionRecord> page(@Param("userId") long userId, @Param("companyId") long companyId,
        @Param("criteria") TransactionSearchCriteria criteria, @Param("limit") int limit,
        @Param("offset") long offset);

    @ResultMap("transaction")
    @Select("""
        SELECT t.id, t.version, t.idempotency_key, t.company_id, t.user_id, t.family_id,
            t.type, t.amount, t.category_id, t.account_id, t.date::text AS date,
            t.note, t.original_transaction_id, t.refunded_amount, t.is_refundable,
            t.budget_id, t.created_at, t.updated_at,
            c.name AS resolved_category_name,
            c.icon AS resolved_category_icon, c.color AS resolved_category_color,
            a.name AS resolved_account_name
        FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id WHERE t.id = #{id}
        """)
    TransactionRecord findById(@Param("id") long id);

    @ResultMap("transaction")
    @Select("""
        SELECT t.id, t.version, t.idempotency_key, t.company_id, t.user_id, t.family_id,
            t.type, t.amount, t.category_id, t.account_id, t.date::text AS date,
            t.note, t.original_transaction_id, t.refunded_amount, t.is_refundable,
            t.budget_id, t.created_at, t.updated_at,
            c.name AS resolved_category_name,
            c.icon AS resolved_category_icon, c.color AS resolved_category_color,
            a.name AS resolved_account_name
        FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.user_id = #{userId} AND t.company_id = #{companyId}
        ORDER BY t.date DESC, t.id DESC
        """)
    List<TransactionRecord> findAll(@Param("userId") long userId, @Param("companyId") long companyId);

    @ConstructorArgs({
        @Arg(column = "income", javaType = BigDecimal.class),
        @Arg(column = "expense", javaType = BigDecimal.class),
        @Arg(column = "refund", javaType = BigDecimal.class),
        @Arg(column = "pending_collection", javaType = BigDecimal.class),
        @Arg(column = "customer_refund", javaType = BigDecimal.class),
        @Arg(column = "severance", javaType = BigDecimal.class),
        @Arg(column = "row_count", javaType = long.class),
        @Arg(column = "large_count", javaType = long.class),
        @Arg(column = "review_count", javaType = long.class)
    })
    @SelectProvider(type = Sql.class, method = "summary")
    Totals summary(@Param("userId") long userId, @Param("companyId") long companyId,
                   @Param("criteria") TransactionSearchCriteria criteria);

    final class Sql {
        private static final String PROJECTION = """
            SELECT t.id, t.version, t.idempotency_key, t.company_id, t.user_id, t.family_id,
                t.type, t.amount, t.category_id, t.account_id, t.date::text AS date,
                t.note, t.original_transaction_id, t.refunded_amount, t.is_refundable,
                t.budget_id, t.created_at, t.updated_at,
                c.name AS resolved_category_name,
                c.icon AS resolved_category_icon, c.color AS resolved_category_color,
                a.name AS resolved_account_name
            """;

        private static String from(TransactionSearchCriteria criteria) {
            StringBuilder sql = new StringBuilder("""
                FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
                LEFT JOIN accounts a ON a.id = t.account_id
                WHERE t.user_id = #{userId} AND t.company_id = #{companyId}
                """);
            if (criteria.type() != null) sql.append(" AND t.type = #{criteria.type}");
            if (criteria.categoryId() != null) sql.append(" AND t.category_id = #{criteria.categoryId}");
            if (criteria.accountId() != null) sql.append(" AND t.account_id = #{criteria.accountId}");
            if (criteria.startDate() != null) sql.append(" AND t.date >= #{criteria.startDate}");
            if (criteria.endDate() != null) sql.append(" AND t.date <= #{criteria.endDate}");
            if (criteria.minAmount() != null) sql.append(" AND t.amount >= #{criteria.minAmount}");
            if (criteria.maxAmount() != null) sql.append(" AND t.amount <= #{criteria.maxAmount}");
            if (!criteria.keyword().isBlank()) {
                sql.append(" AND (LOWER(t.note) LIKE CONCAT('%', LOWER(#{criteria.keyword}), '%') OR LOWER(COALESCE(c.name, '')) LIKE CONCAT('%', LOWER(#{criteria.keyword}), '%') OR LOWER(COALESCE(a.name, '')) LIKE CONCAT('%', LOWER(#{criteria.keyword}), '%'))");
            }
            return sql.toString();
        }

        public String count(Map<String, Object> params) {
            return "SELECT COUNT(*) " + from((TransactionSearchCriteria) params.get("criteria"));
        }

        public String page(Map<String, Object> params) {
            return PROJECTION + from((TransactionSearchCriteria) params.get("criteria"))
                + " ORDER BY t.date DESC, t.id DESC LIMIT #{limit} OFFSET #{offset}";
        }

        public String summary(Map<String, Object> params) {
            return """
                WITH filtered AS (
                    SELECT t.type, t.amount, t.note, t.is_refundable,
                        LOWER(COALESCE(t.note, '') || ' ' || COALESCE(c.name, '') || ' ' || COALESCE(a.name, '')) AS searchable
                """ + from((TransactionSearchCriteria) params.get("criteria")) + """
                ), classified AS (
                    SELECT *,
                        type = 1 AND searchable LIKE ANY (ARRAY['%待回款%', '%应收%', '%未回款%', '%尾款%', '%分期%', '%验收后%', '%交付后%', '%回款中%']) AS pending,
                        type = 2 AND searchable LIKE ANY (ARRAY['%客户退款%', '%退款给客户%', '%收入退款%', '%订单退款%', '%项目退款%', '%退货退款%', '%服务退款%']) AS customer_refund,
                        type = 2 AND searchable LIKE ANY (ARRAY['%裁员%', '%离职补偿%', '%经济补偿%', '%遣散%', '%n+1%', '%n+ 1%', '%补偿金%', '%解除劳动%']) AS severance
                    FROM filtered
                )
                SELECT COALESCE(SUM(amount) FILTER (WHERE type = 1), 0) AS income,
                    COALESCE(SUM(amount) FILTER (WHERE type = 2), 0) AS expense,
                    COALESCE(SUM(amount) FILTER (WHERE type = 3), 0) AS refund,
                    COALESCE(SUM(amount) FILTER (WHERE pending), 0) AS pending_collection,
                    COALESCE(SUM(amount) FILTER (WHERE customer_refund), 0) AS customer_refund,
                    COALESCE(SUM(amount) FILTER (WHERE severance), 0) AS severance,
                    COUNT(*) AS row_count,
                    COUNT(*) FILTER (WHERE amount >= 10000) AS large_count,
                    COUNT(*) FILTER (WHERE amount >= 10000 OR (type = 2 AND is_refundable) OR pending OR customer_refund OR severance OR COALESCE(BTRIM(note), '') = '') AS review_count
                FROM classified
                """;
        }
    }
}
