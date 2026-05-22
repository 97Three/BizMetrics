package com.bizmetrics.people.infrastructure;

import com.bizmetrics.people.application.DepartmentRepository;
import com.bizmetrics.people.domain.Department;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDepartmentRepository implements DepartmentRepository {
    private final DepartmentMapper mapper;

    public MyBatisDepartmentRepository(DepartmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Department> findByCompany(long companyId) {
        return mapper.findByCompany(companyId).stream().map(this::normalizeDates).toList();
    }

    @Override
    public Optional<Department> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::normalizeDates);
    }

    @Override
    public Optional<Department> findByIdForUpdate(long id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id)).map(this::normalizeDates);
    }

    @Override
    public Department insert(Department department) {
        Long id = mapper.insert(department, timestamp(department.createdAt), timestamp(department.updatedAt));
        if (id == null) throw new IllegalStateException("Database did not return a generated department id");
        department.id = id;
        return department;
    }

    @Override
    public void update(Department department) {
        int updated = mapper.update(department, timestamp(department.updatedAt));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Department was changed by another request: " + department.id);
        }
    }

    @Override
    public boolean employeeBelongsToCompany(long employeeId, long companyId) {
        return mapper.employeeCount(employeeId, companyId) == 1;
    }

    private Department normalizeDates(Department department) {
        department.createdAt = OffsetDateTime.parse(department.createdAt.replace(' ', 'T')).toString();
        department.updatedAt = OffsetDateTime.parse(department.updatedAt.replace(' ', 'T')).toString();
        return department;
    }

    private OffsetDateTime timestamp(String value) {
        return OffsetDateTime.parse(value);
    }
}
