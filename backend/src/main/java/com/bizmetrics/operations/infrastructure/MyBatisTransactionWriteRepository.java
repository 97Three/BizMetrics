package com.bizmetrics.operations.infrastructure;

import com.bizmetrics.operations.application.TransactionQueryRepository;
import com.bizmetrics.operations.application.TransactionWriteRepository;
import com.bizmetrics.operations.domain.TransactionRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for locked and optimistic transaction writes. */
@Repository
public class MyBatisTransactionWriteRepository implements TransactionWriteRepository {
    private final TransactionWriteMapper mapper;
    private final TransactionQueryRepository queryRepository;

    public MyBatisTransactionWriteRepository(
        TransactionWriteMapper mapper,
        TransactionQueryRepository queryRepository
    ) {
        this.mapper = mapper;
        this.queryRepository = queryRepository;
    }

    @Override
    public void lockIdempotency(long companyId, String idempotencyKey) {
        mapper.lockIdempotency("transaction:" + companyId + ":" + idempotencyKey);
    }

    @Override
    public Optional<TransactionRecord> findByIdempotency(long companyId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
        return Optional.ofNullable(mapper.findByIdempotency(companyId, idempotencyKey))
            .flatMap(queryRepository::findById);
    }

    @Override
    public Optional<TransactionRecord> findForUpdate(long id) {
        return Optional.ofNullable(mapper.findForUpdate(id)).flatMap(queryRepository::findById);
    }

    @Override
    public boolean hasRefunds(long transactionId) {
        return mapper.countRefunds(transactionId) > 0;
    }

    @Override
    public TransactionRecord insert(TransactionRecord transaction) {
        transaction.version = 0;
        Long key = mapper.insert(row(transaction));
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated transaction id");
        }
        transaction.id = key;
        return transaction;
    }

    @Override
    public void update(TransactionRecord transaction) {
        int updated = mapper.update(row(transaction));
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                "Transaction was changed by another request: " + transaction.id
            );
        }
        transaction.version++;
    }

    @Override
    public void delete(TransactionRecord transaction) {
        int deleted = mapper.delete(transaction.id, transaction.version);
        if (deleted != 1) {
            throw new OptimisticLockingFailureException(
                "Transaction was changed by another request: " + transaction.id
            );
        }
    }

    private TransactionWriteMapper.Row row(TransactionRecord transaction) {
        return new TransactionWriteMapper.Row(
            transaction.id, transaction.version, transaction.userId, transaction.familyId, transaction.type,
            money(transaction.amount), transaction.categoryId, transaction.accountId,
            LocalDate.parse(transaction.date), transaction.note, transaction.originalTransactionId,
            money(transaction.refundedAmount), transaction.isRefundable, transaction.budgetId,
            OffsetDateTime.parse(transaction.createdAt), OffsetDateTime.parse(transaction.updatedAt),
            transaction.companyId, transaction.idempotencyKey
        );
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
