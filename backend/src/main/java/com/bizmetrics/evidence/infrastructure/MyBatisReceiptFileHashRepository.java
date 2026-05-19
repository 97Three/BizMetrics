package com.bizmetrics.evidence.infrastructure;

import com.bizmetrics.evidence.application.ReceiptFileHashRepository;
import com.bizmetrics.evidence.application.ReceiptFileRegistration;
import com.bizmetrics.evidence.domain.ReceiptFileDigest;
import java.util.OptionalLong;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for company-scoped receipt attachment deduplication. */
@Repository
public class MyBatisReceiptFileHashRepository implements ReceiptFileHashRepository {
    private final ReceiptFileHashMapper mapper;

    public MyBatisReceiptFileHashRepository(ReceiptFileHashMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void lock(long companyId, ReceiptFileDigest digest) {
        mapper.lock("receipt-file:" + companyId + ":" + digest.value());
    }

    @Override
    public OptionalLong findVoucherId(long companyId, ReceiptFileDigest digest) {
        Long voucherId = mapper.findVoucherId(companyId, digest.value());
        return voucherId == null ? OptionalLong.empty() : OptionalLong.of(voucherId);
    }

    @Override
    public void register(ReceiptFileRegistration registration) {
        mapper.register(
            registration.companyId(),
            registration.voucherId(),
            registration.digest().value(),
            registration.fileName(),
            registration.fileSize(),
            registration.createdAt()
        );
    }
}
