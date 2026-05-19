package com.bizmetrics.evidence.infrastructure;

import com.bizmetrics.common.PageRequest;
import com.bizmetrics.common.PagedResponse;
import com.bizmetrics.evidence.application.ReceiptListQuery;
import com.bizmetrics.evidence.application.ReceiptVoucherRepository;
import com.bizmetrics.evidence.domain.ReceiptSummary;
import com.bizmetrics.evidence.domain.ReceiptVoucher;
import com.bizmetrics.evidence.domain.ReceiptVoucherDraft;
import com.bizmetrics.evidence.domain.ReceiptVoucherPolicy;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisReceiptVoucherRepository implements ReceiptVoucherRepository {
    private final ReceiptVoucherMapper mapper;

    public MyBatisReceiptVoucherRepository(ReceiptVoucherMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PagedResponse<ReceiptVoucher> findPage(long companyId, ReceiptListQuery filters) {
        PageRequest page = filters.pageRequest();
        String pattern = filters.keyword().isBlank() ? null
            : "%" + escapeLike(filters.keyword().toLowerCase(Locale.ROOT)) + "%";
        long total = mapper.countFiltered(companyId, filters, pattern);
        List<ReceiptVoucher> content = hydrate(mapper.page(companyId, filters, pattern,
            page.size(), (long) page.page() * page.size()));
        int pages = (int) Math.ceil((double) total / page.size());
        return new PagedResponse<>(content, total, pages, page.size(), page.page());
    }

    @Override
    public ReceiptSummary summarize(long companyId) {
        ReceiptVoucherMapper.SummaryRow row = mapper.summarize(companyId);
        return new ReceiptSummary(row.totalCount, row.totalAmount, row.salesInvoiceAmount,
            row.purchaseInvoiceAmount, row.outputTaxAmount, row.deductibleTaxAmount,
            row.reimbursementAmount, row.reimbursementPendingAmount, row.pendingAmount,
            row.pendingReviewCount, row.missingAttachmentCount, row.missingTransactionCount,
            row.highRiskCount, row.uncheckedInvoiceCount, row.pendingDeductionCount,
            row.pendingReimbursementCount, row.missingTaxPeriodCount, row.pendingApprovalCount,
            row.pendingAccountingCount, row.postedAccountingCount);
    }

    @Override
    public List<ReceiptVoucher> findByCompany(long companyId) {
        return hydrate(mapper.findByCompany(companyId));
    }

    @Override
    public List<ReceiptVoucher> findAll() {
        return hydrate(mapper.findAll());
    }

    @Override
    public Optional<ReceiptVoucher> findById(long id) {
        return Optional.ofNullable(hydrate(mapper.findById(id)));
    }

    @Override
    public Optional<ReceiptVoucher> findByIdForUpdate(long id) {
        return Optional.ofNullable(hydrate(mapper.findByIdForUpdate(id)));
    }

    @Override
    public long count() {
        return mapper.count();
    }

    @Override
    public ReceiptVoucher insert(ReceiptVoucherDraft draft) {
        LocalDate today = LocalDate.now();
        ReceiptVoucher voucher = ReceiptVoucherPolicy.initialize(draft, today, OffsetDateTime.now().toString());
        mapper.insert(voucher);
        if (voucher.id <= 0) {
            throw new IllegalStateException("Database did not return a generated receipt voucher id");
        }
        String accountingVoucherNoBeforeHydration = voucher.accountingVoucherNo;
        ReceiptVoucherPolicy.hydrate(voucher, today);
        if (isBlank(accountingVoucherNoBeforeHydration) && !isBlank(voucher.accountingVoucherNo)) {
            save(voucher);
        }
        return voucher;
    }

    @Override
    public void save(ReceiptVoucher voucher) {
        int updated = mapper.save(voucher);
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Receipt voucher was changed by another request: " + voucher.id);
        }
        voucher.version++;
    }

    private List<ReceiptVoucher> hydrate(List<ReceiptVoucher> vouchers) {
        vouchers.forEach(voucher -> ReceiptVoucherPolicy.hydrate(voucher, LocalDate.now()));
        return vouchers;
    }

    private ReceiptVoucher hydrate(ReceiptVoucher voucher) {
        if (voucher != null) {
            ReceiptVoucherPolicy.hydrate(voucher, LocalDate.now());
        }
        return voucher;
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
