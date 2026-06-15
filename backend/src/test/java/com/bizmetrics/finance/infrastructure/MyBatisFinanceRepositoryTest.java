package com.bizmetrics.finance.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bizmetrics.finance.domain.Account;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class MyBatisFinanceRepositoryTest {
    @Test
    void rejectsAStaleAccountUpdate() {
        FinanceMapper mapper = mock(FinanceMapper.class);
        when(mapper.updateAccount(any())).thenReturn(0);
        MyBatisFinanceRepository repository = new MyBatisFinanceRepository(mapper);
        Account account = account(42, 3);

        OptimisticLockingFailureException exception = assertThrows(
            OptimisticLockingFailureException.class,
            () -> repository.updateAccount(account)
        );

        assertEquals("Account was changed by another request: 42", exception.getMessage());
        assertEquals(3, account.version);
    }

    @Test
    void advancesVersionAfterACommittedAccountUpdate() {
        FinanceMapper mapper = mock(FinanceMapper.class);
        when(mapper.updateAccount(any())).thenReturn(1);
        MyBatisFinanceRepository repository = new MyBatisFinanceRepository(mapper);
        Account account = account(42, 3);

        repository.updateAccount(account);

        assertEquals(4, account.version);
    }

    @Test
    void rejectsAStaleAccountDeletion() {
        FinanceMapper mapper = mock(FinanceMapper.class);
        when(mapper.deleteAccount(any())).thenReturn(0);
        MyBatisFinanceRepository repository = new MyBatisFinanceRepository(mapper);

        assertThrows(
            OptimisticLockingFailureException.class,
            () -> repository.deleteAccount(account(42, 3))
        );

    }

    @Test
    void deletesTheExpectedAccountVersion() {
        FinanceMapper mapper = mock(FinanceMapper.class);
        when(mapper.deleteAccount(any())).thenReturn(1);
        MyBatisFinanceRepository repository = new MyBatisFinanceRepository(mapper);

        repository.deleteAccount(account(42, 3));
    }

    private Account account(long id, long version) {
        Account account = new Account();
        account.id = id;
        account.version = version;
        account.createdAt = "2026-09-01T09:00:00Z";
        account.updatedAt = "2026-09-01T10:00:00Z";
        return account;
    }
}
