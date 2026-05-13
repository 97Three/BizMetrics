package com.bizmetrics.operations.infrastructure;

import com.bizmetrics.common.PageRequest;
import com.bizmetrics.common.PagedResponse;
import com.bizmetrics.operations.application.TransactionQueryRepository;
import com.bizmetrics.operations.domain.TransactionRecord;
import com.bizmetrics.operations.domain.TransactionSearchCriteria;
import com.bizmetrics.operations.domain.TransactionSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for database-paged transaction reads and aggregate summaries. */
@Repository
public class MyBatisTransactionQueryRepository implements TransactionQueryRepository {
    private final TransactionQueryMapper mapper;

    public MyBatisTransactionQueryRepository(TransactionQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PagedResponse<TransactionRecord> findPage(long userId, long companyId,
        TransactionSearchCriteria criteria, PageRequest pageRequest) {
        long total = mapper.count(userId, companyId, criteria);
        List<TransactionRecord> content = mapper.page(userId, companyId, criteria,
            pageRequest.size(), (long) pageRequest.page() * pageRequest.size());
        int totalPages = (int) Math.ceil((double) total / pageRequest.size());
        return new PagedResponse<>(content, total, totalPages, pageRequest.size(), pageRequest.page());
    }

    @Override
    public TransactionSummary summarize(long userId, long companyId, TransactionSearchCriteria criteria) {
        TransactionQueryMapper.Totals totals = mapper.summary(userId, companyId, criteria);
        return TransactionSummary.fromTotals(totals.income(), totals.expense(), totals.refund(),
            totals.pendingCollection(), totals.customerRefund(), totals.severance(),
            totals.rowCount(), totals.largeCount(), totals.reviewCount());
    }

    @Override
    public Optional<TransactionRecord> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public List<TransactionRecord> findAll(long userId, long companyId) {
        return mapper.findAll(userId, companyId);
    }
}
