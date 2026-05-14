package com.bizmetrics.budget.infrastructure;

import com.bizmetrics.budget.application.BudgetRepository;
import com.bizmetrics.budget.application.BudgetRepository.CategoryRef;
import com.bizmetrics.budget.domain.Budget;
import com.bizmetrics.budget.domain.BudgetPolicy;
import com.bizmetrics.operations.domain.TransactionRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisBudgetRepository implements BudgetRepository {
    private final BudgetMapper mapper;
    private final BudgetPolicy policy;

    public MyBatisBudgetRepository(BudgetMapper mapper, BudgetPolicy policy) {
        this.mapper = mapper;
        this.policy = policy;
    }

    @Override
    public List<Budget> findByCompany(long companyId) {
        return mapper.findByCompany(companyId).stream().map(policy::apply).toList();
    }

    @Override
    public Optional<Budget> findById(long companyId, long id) {
        return Optional.ofNullable(mapper.findById(companyId, id)).map(policy::apply);
    }

    @Override
    public Optional<Budget> findByIdForUpdate(long id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id));
    }

    @Override
    public Budget insert(Budget budget) {
        budget.version = 0;
        Long id = mapper.insert(budget);
        if (id == null) throw new IllegalStateException("Budget insert did not return an id");
        budget.id = id;
        return budget;
    }

    @Override
    public void update(Budget budget) {
        if (mapper.update(budget) != 1) {
            throw new OptimisticLockingFailureException("Budget was changed by another request: " + budget.id);
        }
        budget.version++;
    }

    @Override
    public void delete(long id) {
        if (mapper.delete(id) != 1) {
            throw new OptimisticLockingFailureException("Budget was changed by another request: " + id);
        }
    }

    @Override
    public boolean hasTransactions(long id) {
        return mapper.countTransactions(id) > 0;
    }

    @Override
    public Optional<CategoryRef> category(long id) {
        return Optional.ofNullable(mapper.category(id));
    }

    @Override
    public Optional<Long> matchingBudgetId(TransactionRecord transaction) {
        return Optional.ofNullable(mapper.matchingBudgetId(transaction));
    }

    @Override
    public void persistProjection(Budget budget) {
        if (mapper.persistProjection(budget) == 1) {
            budget.version++;
            return;
        }
        Long version = mapper.version(budget.id);
        if (version == null || version != budget.version) {
            throw new OptimisticLockingFailureException("Budget was changed by another request: " + budget.id);
        }
    }
}
