package com.bizmetrics.operations.application;

import com.bizmetrics.common.PageRequest;
import com.bizmetrics.common.PagedResponse;
import com.bizmetrics.operations.domain.TransactionRecord;
import com.bizmetrics.operations.domain.TransactionSearchCriteria;
import com.bizmetrics.operations.domain.TransactionSummary;
import java.util.List;
import java.util.Optional;

/** Persistence port owned by the transaction query use cases. */
public interface TransactionQueryRepository {
    PagedResponse<TransactionRecord> findPage(
        long userId,
        long companyId,
        TransactionSearchCriteria criteria,
        PageRequest pageRequest
    );

    TransactionSummary summarize(long userId, long companyId, TransactionSearchCriteria criteria);

    List<TransactionRecord> findAll(long userId, long companyId);

    Optional<TransactionRecord> findById(long id);
}
