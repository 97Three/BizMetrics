package com.bizmetrics.service;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Company-scoped search projections; access decisions remain in GlobalSearchService. */
@Mapper
public interface GlobalSearchMapper {
    class Row {
        public long id;
        public String title;
        public String subtitle;
    }

    @Select("""
        SELECT t.id, COALESCE(NULLIF(t.note, ''), '流水 #' || t.id) AS title,
               t.date::TEXT || ' · ' || c.name || ' · ¥' || t.amount::TEXT AS subtitle
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.company_id = #{companyId} AND (
            LOWER(COALESCE(t.note, '')) LIKE #{pattern} OR LOWER(COALESCE(c.name, '')) LIKE #{pattern}
            OR LOWER(COALESCE(a.name, '')) LIKE #{pattern} OR t.amount::TEXT LIKE #{pattern}
        ) AND (
            #{companyWide} OR t.user_id = #{userId} OR (
                CAST(#{departmentId} AS BIGINT) IS NOT NULL AND EXISTS (
                    SELECT 1 FROM employees scoped_employee
                    WHERE scoped_employee.company_id = t.company_id
                      AND scoped_employee.user_id = t.user_id
                      AND scoped_employee.department_id = #{departmentId}
                      AND scoped_employee.status <> 'departed'
                )
            )
        ) ORDER BY t.date DESC, t.id DESC LIMIT #{limit}
        """)
    List<Row> transactions(@Param("companyId") long companyId, @Param("pattern") String pattern,
                           @Param("companyWide") boolean companyWide, @Param("userId") long userId,
                           @Param("departmentId") Long departmentId, @Param("limit") int limit);

    @Select("""
        SELECT id, title, voucher_no || ' · ' || counterparty || ' · ¥' || amount AS subtitle
        FROM receipt_vouchers WHERE company_id = #{companyId} AND (
            LOWER(title) LIKE #{pattern} OR LOWER(voucher_no) LIKE #{pattern}
            OR LOWER(counterparty) LIKE #{pattern} OR LOWER(COALESCE(business_purpose, '')) LIKE #{pattern}
        ) ORDER BY updated_at DESC, id DESC LIMIT #{limit}
        """)
    List<Row> receipts(@Param("companyId") long companyId, @Param("pattern") String pattern,
                       @Param("limit") int limit);

    @Select("""
        SELECT id, name AS title,
               COALESCE(bank, '账户') || ' · ' || COALESCE(NULLIF(account_no, ''), '未设置账号')
                   || ' · ¥' || TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM balance::TEXT)) AS subtitle
        FROM accounts WHERE company_id = #{companyId} AND status = 1 AND (
            LOWER(name) LIKE #{pattern} OR LOWER(COALESCE(bank, '')) LIKE #{pattern}
            OR LOWER(COALESCE(account_no, '')) LIKE #{pattern}
        ) ORDER BY updated_at DESC, id DESC LIMIT #{limit}
        """)
    List<Row> accounts(@Param("companyId") long companyId, @Param("pattern") String pattern,
                       @Param("limit") int limit);

    @Select("""
        SELECT id, name AS title, period || ' · ' || tax_type || ' · ¥' || tax_amount AS subtitle
        FROM tax_items WHERE company_id = #{companyId} AND (
            LOWER(name) LIKE #{pattern} OR LOWER(tax_type) LIKE #{pattern} OR LOWER(period) LIKE #{pattern}
            OR LOWER(COALESCE(responsible_person, '')) LIKE #{pattern}
        ) ORDER BY due_date DESC, id DESC LIMIT #{limit}
        """)
    List<Row> taxItems(@Param("companyId") long companyId, @Param("pattern") String pattern,
                       @Param("limit") int limit);

    @Select("""
        SELECT employee.id, employee.name AS title,
               COALESCE(NULLIF(position, ''), '员工') || ' · ' || COALESCE(NULLIF(email, ''), '未设置邮箱') AS subtitle
        FROM employees employee WHERE employee.company_id = #{companyId} AND (
            LOWER(employee.name) LIKE #{pattern} OR LOWER(COALESCE(employee.employee_no, '')) LIKE #{pattern}
            OR LOWER(COALESCE(employee.position, '')) LIKE #{pattern} OR LOWER(COALESCE(employee.email, '')) LIKE #{pattern}
        ) AND (
            #{companyWide} OR employee.user_id = #{userId}
            OR (CAST(#{departmentId} AS BIGINT) IS NOT NULL AND employee.department_id = #{departmentId})
        ) ORDER BY employee.updated_at DESC, employee.id DESC LIMIT #{limit}
        """)
    List<Row> employees(@Param("companyId") long companyId, @Param("pattern") String pattern,
                        @Param("companyWide") boolean companyWide, @Param("userId") long userId,
                        @Param("departmentId") Long departmentId, @Param("limit") int limit);
}
