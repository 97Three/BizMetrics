package com.bizmetrics.recurring.infrastructure;

import com.bizmetrics.recurring.application.RecurringItemRepository;
import com.bizmetrics.recurring.domain.RecurringItem;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for recurring rule persistence and execution locking. */
@Repository
public class MyBatisRecurringItemRepository implements RecurringItemRepository {
    private final RecurringItemMapper mapper;

    public MyBatisRecurringItemRepository(RecurringItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RecurringItem> findByOwnerAndCompany(long userId, long companyId) {
        return mapper.findByOwnerAndCompany(userId, companyId);
    }

    @Override
    public Optional<RecurringItem> findByIdForUpdate(String id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id));
    }

    @Override
    public RecurringItem insert(RecurringItem item) {
        if (mapper.insert(item) != 1) {
            throw new OptimisticLockingFailureException("Recurring item was not inserted: " + item.id);
        }
        return item;
    }

    @Override
    public void update(RecurringItem item) {
        if (mapper.update(item) != 1) {
            throw new OptimisticLockingFailureException("Recurring item was changed by another request: " + item.id);
        }
    }

    @Override
    public void delete(String id) {
        if (mapper.delete(id) != 1) {
            throw new OptimisticLockingFailureException("Recurring item was changed by another request: " + id);
        }
    }
}
