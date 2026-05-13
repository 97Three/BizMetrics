package com.bizmetrics.operations.infrastructure;

import com.bizmetrics.operations.application.TransactionLinkQuery;
import com.bizmetrics.operations.application.TransactionLinkTarget;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter that exposes only transaction ownership needed by cross-module links. */
@Repository
public class MyBatisTransactionLinkQuery implements TransactionLinkQuery {
    private final TransactionLinkMapper mapper;

    public MyBatisTransactionLinkQuery(TransactionLinkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TransactionLinkTarget> findById(long transactionId) {
        return Optional.ofNullable(mapper.findById(transactionId));
    }
}
