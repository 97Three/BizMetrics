package com.bizmetrics.evidence.application;

import com.bizmetrics.evidence.domain.ReceiptStorageUsage;

public interface ReceiptStorageUsageRepository {
    void lockCompany(long companyId);

    ReceiptStorageUsage findByCompany(long companyId);
}
