package com.bizmetrics.operations.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bizmetrics.operations.domain.Category;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class MyBatisCategoryRepositoryTest {
    @Test
    void rejectsAnUpdateWhenTheLockedCategoryDisappeared() {
        CategoryMapper mapper = mock(CategoryMapper.class);
        when(mapper.update(org.mockito.ArgumentMatchers.any())).thenReturn(0);
        MyBatisCategoryRepository repository = new MyBatisCategoryRepository(mapper);
        Category category = category(42);

        OptimisticLockingFailureException exception = assertThrows(
            OptimisticLockingFailureException.class,
            () -> repository.update(category)
        );

        assertEquals("Category was changed by another request: 42", exception.getMessage());
    }

    @Test
    void acceptsExactlyOneUpdatedDatabaseRow() {
        CategoryMapper mapper = mock(CategoryMapper.class);
        when(mapper.update(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        MyBatisCategoryRepository repository = new MyBatisCategoryRepository(mapper);
        Category category = category(42);

        repository.update(category);
    }

    @Test
    void rejectsADeletionWhenTheLockedCategoryDisappeared() {
        CategoryMapper mapper = mock(CategoryMapper.class);
        when(mapper.delete(42)).thenReturn(0);
        MyBatisCategoryRepository repository = new MyBatisCategoryRepository(mapper);

        assertThrows(OptimisticLockingFailureException.class, () -> repository.delete(category(42)));
    }

    @Test
    void acceptsExactlyOneDeletedDatabaseRow() {
        CategoryMapper mapper = mock(CategoryMapper.class);
        when(mapper.delete(42)).thenReturn(1);
        MyBatisCategoryRepository repository = new MyBatisCategoryRepository(mapper);

        repository.delete(category(42));
    }

    private Category category(long id) {
        Category category = new Category();
        category.id = id;
        category.name = "Category";
        category.icon = "C";
        category.color = "#112233";
        category.type = "expense";
        category.createdAt = "2026-09-02T00:00:00Z";
        category.updatedAt = category.createdAt;
        return category;
    }
}
