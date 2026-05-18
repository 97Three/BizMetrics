package com.bizmetrics.evidence.application;

import com.bizmetrics.common.PagedResponse;
import com.bizmetrics.evidence.domain.ReceiptSummary;
import com.bizmetrics.evidence.domain.ReceiptVoucher;
import com.bizmetrics.evidence.domain.ReceiptVoucherDraft;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for receipt vouchers owned by the Evidence module. */
public interface ReceiptVoucherRepository {
    PagedResponse<ReceiptVoucher> findPage(long companyId, ReceiptListQuery query);

    ReceiptSummary summarize(long companyId);

    List<ReceiptVoucher> findByCompany(long companyId);

    List<ReceiptVoucher> findAll();

    Optional<ReceiptVoucher> findById(long id);

    Optional<ReceiptVoucher> findByIdForUpdate(long id);

    long count();

    ReceiptVoucher insert(ReceiptVoucherDraft draft);

    void save(ReceiptVoucher voucher);
}
