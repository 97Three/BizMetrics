package com.bizmetrics.evidence.infrastructure;

import com.bizmetrics.evidence.application.ReceiptStorageUsageRepository;
import com.bizmetrics.evidence.domain.ReceiptStorageUsage;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for serialized, company-scoped receipt capacity checks. */
@Repository
public class MyBatisReceiptStorageUsageRepository implements ReceiptStorageUsageRepository {
    private final ReceiptStorageUsageMapper mapper;

    public MyBatisReceiptStorageUsageRepository(ReceiptStorageUsageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void lockCompany(long companyId) {
        mapper.lock("receipt-storage:" + companyId);
    }

    @Override
    public ReceiptStorageUsage findByCompany(long companyId) {
        return Objects.requireNonNull(mapper.findByCompany(companyId));
    }
}
