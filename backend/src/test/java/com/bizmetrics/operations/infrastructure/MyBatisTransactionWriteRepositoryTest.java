package com.bizmetrics.operations.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bizmetrics.operations.application.TransactionQueryRepository;
import com.bizmetrics.operations.domain.TransactionRecord;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class MyBatisTransactionWriteRepositoryTest {
    @Test
    void rejectsAStaleTransactionUpdate() {
        TransactionWriteMapper mapper = mock(TransactionWriteMapper.class);
        when(mapper.update(any(TransactionWriteMapper.Row.class))).thenReturn(0);
        MyBatisTransactionWriteRepository repository = repository(mapper);
        TransactionRecord transaction = transaction(42, 3);

        OptimisticLockingFailureException exception = assertThrows(
            OptimisticLockingFailureException.class,
            () -> repository.update(transaction)
        );

        assertEquals("Transaction was changed by another request: 42", exception.getMessage());
        assertEquals(3, transaction.version);
    }

    @Test
    void advancesVersionAfterUpdate() {
        TransactionWriteMapper mapper = mock(TransactionWriteMapper.class);
        when(mapper.update(any(TransactionWriteMapper.Row.class))).thenReturn(1);
        MyBatisTransactionWriteRepository repository = repository(mapper);
        TransactionRecord transaction = transaction(42, 3);

        repository.update(transaction);

        assertEquals(4, transaction.version);
    }

    @Test
    void rejectsAStaleTransactionDeletion() {
        TransactionWriteMapper mapper = mock(TransactionWriteMapper.class);
        MyBatisTransactionWriteRepository repository = repository(mapper);
        TransactionRecord transaction = transaction(42, 3);

        assertThrows(OptimisticLockingFailureException.class, () -> repository.delete(transaction));
    }

    @Test
    void deletesTheExpectedVersion() {
        TransactionWriteMapper mapper = mock(TransactionWriteMapper.class);
        when(mapper.delete(42, 3)).thenReturn(1);
        MyBatisTransactionWriteRepository repository = repository(mapper);

        repository.delete(transaction(42, 3));
    }

    private MyBatisTransactionWriteRepository repository(TransactionWriteMapper mapper) {
        return new MyBatisTransactionWriteRepository(
            mapper,
            mock(TransactionQueryRepository.class)
        );
    }

    private TransactionRecord transaction(long id, long version) {
        TransactionRecord transaction = new TransactionRecord();
        transaction.id = id;
        transaction.version = version;
        transaction.companyId = 9L;
        transaction.date = "2026-09-01";
        transaction.createdAt = OffsetDateTime.now().minusMinutes(1).toString();
        transaction.updatedAt = OffsetDateTime.now().toString();
        return transaction;
    }
}
