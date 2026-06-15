package com.bizmetrics.platform.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

class MyBatisCompanyRepositoryTest {
    @Test
    void insertNormalizesAndBindsTypedTenantProfile() {
        CompanyMapper mapper = mock(CompanyMapper.class);
        when(mapper.insert(any(Company.class), any(OffsetDateTime.class), any(OffsetDateTime.class)))
            .thenReturn(41L);
        MyBatisCompanyRepository repository = new MyBatisCompanyRepository(mapper);
        Company company = company();
        Company saved = repository.insert(company);
        assertEquals(41, saved.id);
        assertEquals(0, saved.version);
        assertEquals("Acme", saved.name);
        assertEquals("company", saved.entityType);
        assertEquals("CN-001", saved.creditCode);
        assertEquals("CNY", saved.currency);
        assertNotNull(saved.createdAt);
        ArgumentCaptor<OffsetDateTime> createdAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper).insert(org.mockito.ArgumentMatchers.same(company), createdAt.capture(),
            any(OffsetDateTime.class));
        assertNotNull(createdAt.getValue());
    }

    @Test
    void updateUsesOptimisticVersionAndAdvancesReturnedProfile() {
        CompanyMapper mapper = mock(CompanyMapper.class);
        when(mapper.update(any(Company.class), any(OffsetDateTime.class))).thenReturn(1);
        MyBatisCompanyRepository repository = new MyBatisCompanyRepository(mapper);
        Company company = company();
        company.id = 41;
        company.version = 3;
        repository.update(company);
        assertEquals(4, company.version);
        verify(mapper).update(org.mockito.ArgumentMatchers.same(company), any(OffsetDateTime.class));
    }

    @Test
    void staleUpdateIsRejected() {
        CompanyMapper mapper = mock(CompanyMapper.class);
        MyBatisCompanyRepository repository = new MyBatisCompanyRepository(mapper);
        Company company = company();
        company.id = 41;
        company.version = 3;
        assertThrows(OptimisticLockingFailureException.class, () -> repository.update(company));
        assertEquals(3, company.version);
    }

    @Test
    void allCompaniesUseOneAuthoritativeQuery() {
        CompanyMapper mapper = mock(CompanyMapper.class);
        Company stored = company();
        stored.createdAt = "2026-09-04T12:00:00Z";
        stored.updatedAt = stored.createdAt;
        when(mapper.findAll()).thenReturn(List.of(stored));
        MyBatisCompanyRepository repository = new MyBatisCompanyRepository(mapper);
        assertEquals(1, repository.findAll().size());
        verify(mapper).findAll();
    }

    private Company company() {
        Company company = new Company();
        company.ownerId = 7;
        company.name = " Acme ";
        company.entityType = " COMPANY ";
        company.creditCode = " cn-001 ";
        company.industry = " Software ";
        company.taxpayerType = " General ";
        company.currency = " cny ";
        company.country = " China ";
        company.province = " Guangdong ";
        company.city = " Shenzhen ";
        company.district = " Nanshan ";
        company.registeredAddress = " 1 Road ";
        company.operatingRegion = " China/Guangdong/Shenzhen ";
        company.taxAuthority = " Shenzhen Tax ";
        company.policyProfileKey = " CN-GD-SZ ";
        company.fiscalYearStartMonth = 1;
        return company;
    }
}
