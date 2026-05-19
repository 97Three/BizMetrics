package com.bizmetrics.evidence.infrastructure;

import com.bizmetrics.evidence.application.ReceiptListQuery;
import com.bizmetrics.evidence.domain.ReceiptVoucher;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReceiptVoucherMapper {
    String FILTER = """
        FROM receipt_vouchers WHERE company_id = #{companyId}
        <if test='f.voucherType != null'>AND voucher_type = #{f.voucherType}</if>
        <if test='f.direction != null'>AND direction = #{f.direction}</if>
        <if test='f.status != null'>AND status = #{f.status}</if>
        <if test='f.invoiceCheckStatus != null'>AND invoice_check_status = #{f.invoiceCheckStatus}</if>
        <if test='f.deductionStatus != null'>AND deduction_status = #{f.deductionStatus}</if>
        <if test='f.reimbursementStatus != null'>AND reimbursement_status = #{f.reimbursementStatus}</if>
        <if test='f.taxPeriod != null'>AND tax_period = #{f.taxPeriod}</if>
        <if test='f.startDate != null'>AND issue_date &gt;= #{f.startDate}</if>
        <if test='f.endDate != null'>AND issue_date &lt;= #{f.endDate}</if>
        <if test='f.minAmount != null'>AND amount &gt;= #{f.minAmount}</if>
        <if test='f.maxAmount != null'>AND amount &lt;= #{f.maxAmount}</if>
        <if test='f.linkState == "missing"'>AND transaction_id IS NULL</if>
        <if test='f.linkState == "linked"'>AND transaction_id IS NOT NULL</if>
        <if test='keywordPattern != null'>
            AND (LOWER(COALESCE(title, '')) LIKE #{keywordPattern} ESCAPE '!'
              OR LOWER(COALESCE(voucher_no, '')) LIKE #{keywordPattern} ESCAPE '!'
              OR LOWER(COALESCE(counterparty, '')) LIKE #{keywordPattern} ESCAPE '!'
              OR LOWER(COALESCE(note, '')) LIKE #{keywordPattern} ESCAPE '!')
        </if>
        """;

    @Select("<script>SELECT COUNT(*) " + FILTER + "</script>")
    long countFiltered(@Param("companyId") long companyId, @Param("f") ReceiptListQuery filters,
                       @Param("keywordPattern") String keywordPattern);

    @Select("<script>SELECT * " + FILTER + " ORDER BY issue_date DESC, id LIMIT #{limit} OFFSET #{offset}</script>")
    List<ReceiptVoucher> page(@Param("companyId") long companyId, @Param("f") ReceiptListQuery filters,
                              @Param("keywordPattern") String keywordPattern,
                              @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
        SELECT COUNT(*) AS total_count, COALESCE(SUM(amount), 0) AS total_amount,
            COALESCE(SUM(amount) FILTER (WHERE voucher_type = 'sales_invoice'), 0) AS sales_invoice_amount,
            COALESCE(SUM(amount) FILTER (WHERE voucher_type = 'purchase_invoice'), 0) AS purchase_invoice_amount,
            COALESCE(SUM(tax_amount) FILTER (WHERE voucher_type = 'sales_invoice'), 0) AS output_tax_amount,
            COALESCE(SUM(tax_amount) FILTER (WHERE voucher_type = 'purchase_invoice'), 0) AS deductible_tax_amount,
            COALESCE(SUM(amount) FILTER (WHERE voucher_type = 'reimbursement'), 0) AS reimbursement_amount,
            COALESCE(SUM(amount) FILTER (WHERE voucher_type = 'reimbursement'
                AND reimbursement_status NOT IN ('paid', 'archived')), 0) AS reimbursement_pending_amount,
            COALESCE(SUM(amount) FILTER (WHERE status = 'pending_review'), 0) AS pending_amount,
            COUNT(*) FILTER (WHERE status = 'pending_review') AS pending_review_count,
            COUNT(*) FILTER (WHERE file_name IS NULL OR BTRIM(file_name) = '') AS missing_attachment_count,
            COUNT(*) FILTER (WHERE transaction_id IS NULL) AS missing_transaction_count,
            COUNT(*) FILTER (WHERE risk_level IN ('high', 'critical')) AS high_risk_count,
            COUNT(*) FILTER (WHERE invoice_check_status NOT IN ('not_required', 'verified')) AS unchecked_invoice_count,
            COUNT(*) FILTER (WHERE deduction_status IN ('pending', 'deductible')) AS pending_deduction_count,
            COUNT(*) FILTER (WHERE voucher_type = 'reimbursement'
                AND reimbursement_status NOT IN ('paid', 'archived')) AS pending_reimbursement_count,
            COUNT(*) FILTER (WHERE (tax_period IS NULL OR BTRIM(tax_period) = '')
                AND voucher_type IN ('sales_invoice', 'purchase_invoice', 'tax_receipt')) AS missing_tax_period_count,
            COUNT(*) FILTER (WHERE approval_status = 'pending') AS pending_approval_count,
            COUNT(*) FILTER (WHERE accounting_status IN ('not_started', 'draft')) AS pending_accounting_count,
            COUNT(*) FILTER (WHERE accounting_status = 'posted') AS posted_accounting_count
        FROM receipt_vouchers WHERE company_id = #{companyId}
        """)
    SummaryRow summarize(@Param("companyId") long companyId);

    @Select("SELECT * FROM receipt_vouchers WHERE company_id = #{companyId} ORDER BY issue_date DESC, id")
    List<ReceiptVoucher> findByCompany(@Param("companyId") long companyId);

    @Select("SELECT * FROM receipt_vouchers ORDER BY company_id, issue_date DESC, id")
    List<ReceiptVoucher> findAll();

    @Select("SELECT * FROM receipt_vouchers WHERE id = #{id}")
    ReceiptVoucher findById(@Param("id") long id);

    @Select("SELECT * FROM receipt_vouchers WHERE id = #{id} FOR UPDATE")
    ReceiptVoucher findByIdForUpdate(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM receipt_vouchers")
    long count();

    @Insert("""
        INSERT INTO receipt_vouchers (
            company_id, transaction_id, voucher_no, title, voucher_type, direction, counterparty,
            amount, tax_amount, tax_rate, tax_period, invoice_check_status, deduction_status,
            reimbursement_status, approval_status, accounting_status, accounting_voucher_no, accounting_entry,
            approved_by_user_id, approved_at, accounted_at, business_purpose, expense_owner, issue_date, due_date,
            status, file_name, file_size, file_type, file_storage_provider, file_bucket, file_object_key, file_url,
            risk_level, note, operator_user_id, created_at, updated_at
        ) VALUES (
            #{companyId}, #{transactionId}, #{voucherNo}, #{title}, #{voucherType}, #{direction}, #{counterparty},
            #{amount}, #{taxAmount}, #{taxRate}, #{taxPeriod}, #{invoiceCheckStatus}, #{deductionStatus},
            #{reimbursementStatus}, #{approvalStatus}, #{accountingStatus}, #{accountingVoucherNo}, #{accountingEntry},
            #{approvedByUserId}, CAST(#{approvedAt} AS TIMESTAMPTZ), CAST(#{accountedAt} AS TIMESTAMPTZ),
            #{businessPurpose}, #{expenseOwner}, CAST(#{issueDate} AS DATE), CAST(#{dueDate} AS DATE),
            #{status}, #{fileName}, #{fileSize}, #{fileType}, #{fileStorageProvider}, #{fileBucket},
            #{fileObjectKey}, #{fileUrl}, #{riskLevel}, #{note}, #{operatorUserId},
            CAST(#{createdAt} AS TIMESTAMPTZ), CAST(#{updatedAt} AS TIMESTAMPTZ))
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ReceiptVoucher voucher);

    @Update("""
        UPDATE receipt_vouchers SET company_id = #{companyId}, transaction_id = #{transactionId},
            voucher_no = #{voucherNo}, title = #{title}, voucher_type = #{voucherType},
            direction = #{direction}, counterparty = #{counterparty}, amount = #{amount},
            tax_amount = #{taxAmount}, tax_rate = #{taxRate}, tax_period = #{taxPeriod},
            invoice_check_status = #{invoiceCheckStatus}, deduction_status = #{deductionStatus},
            reimbursement_status = #{reimbursementStatus}, approval_status = #{approvalStatus},
            accounting_status = #{accountingStatus}, accounting_voucher_no = #{accountingVoucherNo},
            accounting_entry = #{accountingEntry}, approved_by_user_id = #{approvedByUserId},
            approved_at = CAST(#{approvedAt} AS TIMESTAMPTZ),
            accounted_at = CAST(#{accountedAt} AS TIMESTAMPTZ), business_purpose = #{businessPurpose},
            expense_owner = #{expenseOwner}, issue_date = CAST(#{issueDate} AS DATE),
            due_date = CAST(#{dueDate} AS DATE), status = #{status}, file_name = #{fileName},
            file_size = #{fileSize}, file_type = #{fileType}, file_storage_provider = #{fileStorageProvider},
            file_bucket = #{fileBucket}, file_object_key = #{fileObjectKey}, file_url = #{fileUrl},
            risk_level = #{riskLevel}, note = #{note}, operator_user_id = #{operatorUserId},
            updated_at = CAST(#{updatedAt} AS TIMESTAMPTZ), version = version + 1
        WHERE id = #{id} AND version = #{version}
        """)
    int save(ReceiptVoucher voucher);

    class SummaryRow {
        public long totalCount;
        public BigDecimal totalAmount;
        public BigDecimal salesInvoiceAmount;
        public BigDecimal purchaseInvoiceAmount;
        public BigDecimal outputTaxAmount;
        public BigDecimal deductibleTaxAmount;
        public BigDecimal reimbursementAmount;
        public BigDecimal reimbursementPendingAmount;
        public BigDecimal pendingAmount;
        public long pendingReviewCount;
        public long missingAttachmentCount;
        public long missingTransactionCount;
        public long highRiskCount;
        public long uncheckedInvoiceCount;
        public long pendingDeductionCount;
        public long pendingReimbursementCount;
        public long missingTaxPeriodCount;
        public long pendingApprovalCount;
        public long pendingAccountingCount;
        public long postedAccountingCount;
    }
}
