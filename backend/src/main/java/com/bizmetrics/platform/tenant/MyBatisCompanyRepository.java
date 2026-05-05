package com.bizmetrics.platform.tenant;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCompanyRepository implements CompanyRepository {
    private final CompanyMapper mapper;

    public MyBatisCompanyRepository(CompanyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Company> findAll() {
        return mapper.findAll().stream().map(this::normalizeDates).toList();
    }

    @Override
    public Optional<Company> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::normalizeDates);
    }

    @Override
    public boolean existsAny() {
        return mapper.existsAny();
    }

    @Override
    public Company insert(Company company) {
        CompanyProfilePolicy.normalizeAndValidate(company);
        String now = OffsetDateTime.now().toString();
        company.version = 0;
        company.createdAt = isBlank(company.createdAt) ? now : OffsetDateTime.parse(company.createdAt).toString();
        company.updatedAt = isBlank(company.updatedAt) ? company.createdAt : OffsetDateTime.parse(company.updatedAt).toString();
        Long id = mapper.insert(company, OffsetDateTime.parse(company.createdAt), OffsetDateTime.parse(company.updatedAt));
        if (id == null) throw new IllegalStateException("Database did not return a generated company id");
        company.id = id;
        return company;
    }

    @Override
    public void update(Company company) {
        CompanyProfilePolicy.normalizeAndValidate(company);
        company.updatedAt = OffsetDateTime.now().toString();
        int updated = mapper.update(company, OffsetDateTime.parse(company.updatedAt));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Company was changed by another request: " + company.id);
        }
        company.version += 1;
    }

    private Company normalizeDates(Company company) {
        company.createdAt = OffsetDateTime.parse(company.createdAt.replace(' ', 'T')).toString();
        company.updatedAt = OffsetDateTime.parse(company.updatedAt.replace(' ', 'T')).toString();
        return company;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
