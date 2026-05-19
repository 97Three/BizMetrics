package com.bizmetrics.tax.infrastructure;

import com.bizmetrics.tax.application.TaxItemRepository;
import com.bizmetrics.tax.domain.TaxItem;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisTaxItemRepository implements TaxItemRepository {
    private final TaxItemMapper mapper;

    public MyBatisTaxItemRepository(TaxItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TaxItem> findByCompany(long companyId) {
        return mapper.findByCompany(companyId).stream().map(this::normalizeDates).toList();
    }

    @Override
    public List<TaxItem> findAll() {
        return mapper.findAll().stream().map(this::normalizeDates).toList();
    }

    @Override
    public Optional<TaxItem> findByIdForUpdate(long id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id)).map(this::normalizeDates);
    }

    @Override
    public TaxItem insert(TaxItem item) {
        Long id = mapper.insert(item, date(item.dueDate), nullableDate(item.declarationDate),
            nullableDate(item.paymentDate), timestamp(item.createdAt), timestamp(item.updatedAt));
        if (id == null) throw new IllegalStateException("Database did not return a generated tax item id");
        item.id = id;
        return item;
    }

    @Override
    public void update(TaxItem item) {
        int updated = mapper.update(item, date(item.dueDate), nullableDate(item.declarationDate),
            nullableDate(item.paymentDate), timestamp(item.updatedAt));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Tax item was changed by another request: " + item.id);
        }
    }

    @Override
    public void delete(long id) {
        if (mapper.delete(id) != 1) {
            throw new OptimisticLockingFailureException("Tax item was changed by another request: " + id);
        }
    }

    @Override
    public boolean hasLifecycleHistory(long companyId) {
        return mapper.lifecycleHistoryCount(companyId) > 0;
    }

    private TaxItem normalizeDates(TaxItem item) {
        item.createdAt = OffsetDateTime.parse(item.createdAt.replace(' ', 'T')).toString();
        item.updatedAt = OffsetDateTime.parse(item.updatedAt.replace(' ', 'T')).toString();
        return item;
    }

    private LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    private LocalDate nullableDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private OffsetDateTime timestamp(String value) {
        return OffsetDateTime.parse(value);
    }
}
