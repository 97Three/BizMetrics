package com.bizmetrics.budget.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bizmetrics.budget.domain.Budget;
import com.bizmetrics.budget.domain.BudgetPolicy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class MyBatisBudgetRepositoryTest {
    @Test
    void rejectsAStaleProjectionWrite() {
        BudgetMapper mapper = mock(BudgetMapper.class);
        when(mapper.persistProjection(any())).thenReturn(0);
        when(mapper.version(42)).thenReturn(4L);
        MyBatisBudgetRepository repository = new MyBatisBudgetRepository(mapper, new BudgetPolicy());
        Budget budget = budget();

        OptimisticLockingFailureException exception = assertThrows(
            OptimisticLockingFailureException.class,
            () -> repository.persistProjection(budget)
        );

        assertEquals("Budget was changed by another request: 42", exception.getMessage());
        assertEquals(3, budget.version);
    }

    @Test
    void keepsVersionWhenTheStoredProjectionIsAlreadyCurrent() {
        BudgetMapper mapper = mock(BudgetMapper.class);
        when(mapper.persistProjection(any())).thenReturn(0);
        when(mapper.version(42)).thenReturn(3L);
        MyBatisBudgetRepository repository = new MyBatisBudgetRepository(mapper, new BudgetPolicy());
        Budget budget = budget();

        repository.persistProjection(budget);

        assertEquals(3, budget.version);
    }

    @Test
    void advancesVersionAfterAStoredProjection() {
        BudgetMapper mapper = mock(BudgetMapper.class);
        when(mapper.persistProjection(any())).thenReturn(1);
        MyBatisBudgetRepository repository = new MyBatisBudgetRepository(mapper, new BudgetPolicy());
        Budget budget = budget();

        repository.persistProjection(budget);

        assertEquals(4, budget.version);
    }

    private Budget budget() {
        Budget budget = new Budget();
        budget.id = 42;
        budget.version = 3;
        budget.spent = new BigDecimal("250.00");
        budget.remainingAmount = new BigDecimal("750.00");
        budget.riskLevel = "low";
        budget.riskMessage = "预算健康";
        budget.status = 1;
        budget.updatedAt = OffsetDateTime.now().toString();
        return budget;
    }
}
