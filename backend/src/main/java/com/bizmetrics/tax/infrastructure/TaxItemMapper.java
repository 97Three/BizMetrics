package com.bizmetrics.tax.infrastructure;

import com.bizmetrics.tax.domain.TaxItem;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaxItemMapper {
    @Select("""
        SELECT id, company_id, name, period, tax_type, taxable_amount, tax_amount, paid_amount,
            deductible_amount, tax_rate, due_date, status, filing_status, payment_status,
            frequency, declaration_date, payment_date, responsible_person, risk_level,
            policy_basis, source_type, note, created_at, updated_at
        FROM tax_items WHERE company_id = #{companyId} ORDER BY due_date, id
        """)
    List<TaxItem> findByCompany(long companyId);

    @Select("""
        SELECT id, company_id, name, period, tax_type, taxable_amount, tax_amount, paid_amount,
            deductible_amount, tax_rate, due_date, status, filing_status, payment_status,
            frequency, declaration_date, payment_date, responsible_person, risk_level,
            policy_basis, source_type, note, created_at, updated_at
        FROM tax_items ORDER BY company_id, due_date, id
        """)
    List<TaxItem> findAll();

    @Select("""
        SELECT id, company_id, name, period, tax_type, taxable_amount, tax_amount, paid_amount,
            deductible_amount, tax_rate, due_date, status, filing_status, payment_status,
            frequency, declaration_date, payment_date, responsible_person, risk_level,
            policy_basis, source_type, note, created_at, updated_at
        FROM tax_items WHERE id = #{id} FOR UPDATE
        """)
    TaxItem findByIdForUpdate(long id);

    @Select(value = """
        INSERT INTO tax_items (company_id, name, period, tax_type, taxable_amount, tax_amount, paid_amount,
            deductible_amount, tax_rate, due_date, status, filing_status, payment_status,
            frequency, declaration_date, payment_date, responsible_person, risk_level,
            policy_basis, source_type, note, created_at, updated_at)
        VALUES (#{item.companyId}, #{item.name}, #{item.period}, #{item.taxType}, #{item.taxableAmount},
            #{item.taxAmount}, #{item.paidAmount}, #{item.deductibleAmount}, #{item.taxRate}, #{dueDate},
            #{item.status}, #{item.filingStatus}, #{item.paymentStatus}, #{item.frequency},
            #{declarationDate}, #{paymentDate}, #{item.responsiblePerson}, #{item.riskLevel},
            #{item.policyBasis}, #{item.sourceType}, #{item.note}, #{createdAt}, #{updatedAt})
        RETURNING id
        """, affectData = true)
    Long insert(@Param("item") TaxItem item, @Param("dueDate") LocalDate dueDate,
                @Param("declarationDate") LocalDate declarationDate,
                @Param("paymentDate") LocalDate paymentDate,
                @Param("createdAt") OffsetDateTime createdAt,
                @Param("updatedAt") OffsetDateTime updatedAt);

    @Update("""
        UPDATE tax_items SET name = #{item.name}, period = #{item.period}, tax_type = #{item.taxType},
            taxable_amount = #{item.taxableAmount}, tax_amount = #{item.taxAmount},
            paid_amount = #{item.paidAmount}, deductible_amount = #{item.deductibleAmount},
            tax_rate = #{item.taxRate}, due_date = #{dueDate}, status = #{item.status},
            filing_status = #{item.filingStatus}, payment_status = #{item.paymentStatus},
            frequency = #{item.frequency}, declaration_date = #{declarationDate},
            payment_date = #{paymentDate}, responsible_person = #{item.responsiblePerson},
            risk_level = #{item.riskLevel}, policy_basis = #{item.policyBasis},
            source_type = #{item.sourceType}, note = #{item.note}, updated_at = #{updatedAt}
        WHERE id = #{item.id} AND company_id = #{item.companyId}
        """)
    int update(@Param("item") TaxItem item, @Param("dueDate") LocalDate dueDate,
               @Param("declarationDate") LocalDate declarationDate,
               @Param("paymentDate") LocalDate paymentDate,
               @Param("updatedAt") OffsetDateTime updatedAt);

    @Delete("DELETE FROM tax_items WHERE id = #{id}")
    int delete(long id);

    @Select("SELECT COUNT(*) FROM audit_logs WHERE company_id = #{companyId} AND entity_type = 'tax_item'")
    int lifecycleHistoryCount(long companyId);
}
