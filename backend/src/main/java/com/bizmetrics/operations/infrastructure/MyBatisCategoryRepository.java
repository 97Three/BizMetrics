package com.bizmetrics.operations.infrastructure;

import com.bizmetrics.operations.application.CategoryRepository;
import com.bizmetrics.operations.domain.Category;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter for transaction category persistence and default initialization. */
@Repository
public class MyBatisCategoryRepository implements CategoryRepository {
    private final CategoryMapper mapper;

    public MyBatisCategoryRepository(CategoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Category> findAll(long userId, long companyId, String type) {
        return mapper.findAll(userId, companyId, type == null || type.isBlank() ? null : type)
            .stream().map(this::category).toList();
    }

    @Override
    public Optional<Category> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::category);
    }

    @Override
    public Optional<Category> findForUpdate(long id) {
        return Optional.ofNullable(mapper.findForUpdate(id)).map(this::category);
    }

    @Override
    public Category insert(Category category) {
        Long key = mapper.insert(writeRow(category));
        if (key == null) throw new IllegalStateException("Database did not return a generated category id");
        category.id = key;
        return category;
    }

    @Override
    public void update(Category category) {
        int updated = mapper.update(writeRow(category));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Category was changed by another request: " + category.id);
        }
    }

    @Override
    public boolean hasAccountingReferences(long categoryId) {
        return mapper.countAccountingReferences(categoryId) > 0;
    }

    @Override
    public void delete(Category category) {
        int deleted = mapper.delete(category.id);
        if (deleted != 1) {
            throw new OptimisticLockingFailureException("Category was changed by another request: " + category.id);
        }
    }

    @Override
    @Transactional
    public void ensureCompanyDefaults(long ownerId, long companyId) {
        mapper.lockDefaults("category-defaults:" + companyId);
        if (findAll(ownerId, companyId, "income").isEmpty()) {
            insert(defaultCategory(ownerId, companyId, "经营收入", "💼", "#22c55e", "income"));
        }
        if (findAll(ownerId, companyId, "expense").isEmpty()) {
            insert(defaultCategory(ownerId, companyId, "经营支出", "🧾", "#ef4444", "expense"));
        }
    }

    private Category defaultCategory(
        long ownerId,
        long companyId,
        String name,
        String icon,
        String color,
        String type
    ) {
        Category category = new Category();
        category.userId = ownerId;
        category.companyId = companyId;
        category.name = name;
        category.icon = icon;
        category.color = color;
        category.type = type;
        category.status = 1;
        category.createdAt = java.time.OffsetDateTime.now().toString();
        category.updatedAt = category.createdAt;
        return category;
    }

    private Category category(CategoryMapper.Row row) {
        Category category = new Category();
        category.id = row.id();
        category.companyId = row.companyId();
        category.name = row.name();
        category.icon = row.icon();
        category.color = row.color();
        category.type = row.type();
        category.userId = row.userId();
        category.status = row.status();
        category.createdAt = row.createdAt().toString();
        category.updatedAt = row.updatedAt().toString();
        return category;
    }

    private CategoryMapper.WriteRow writeRow(Category category) {
        return new CategoryMapper.WriteRow(category.id, category.name, category.icon, category.color,
            category.type, category.userId, category.status, timestamp(category.createdAt),
            timestamp(category.updatedAt), category.companyId);
    }

    private OffsetDateTime timestamp(String value) {
        return value == null ? null : OffsetDateTime.parse(value);
    }
}
