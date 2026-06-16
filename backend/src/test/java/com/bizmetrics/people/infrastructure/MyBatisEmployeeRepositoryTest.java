package com.bizmetrics.people.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bizmetrics.people.domain.Employee;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisEmployeeRepositoryTest {
    @Test
    void employeeProfilesUseTwoBatchQueriesRegardlessOfEmployeeCount() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        when(mapper.findByCompany(9)).thenReturn(List.of(employee(1, 9), employee(2, 9), employee(3, 9)));
        when(mapper.findCertificatesByCompany(9)).thenReturn(List.of());
        when(mapper.findExperiencesByCompany(9)).thenReturn(List.of());
        MyBatisEmployeeRepository repository = new MyBatisEmployeeRepository(mapper);

        List<Employee> employees = repository.findByCompany(9, true);

        assertEquals(3, employees.size());
        assertTrue(employees.stream().allMatch(employee -> employee.certificates.isEmpty()));
        assertTrue(employees.stream().allMatch(employee -> employee.experiences.isEmpty()));
        verify(mapper).findByCompany(9);
        verify(mapper).findCertificatesByCompany(9);
        verify(mapper).findExperiencesByCompany(9);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void basicEmployeeReadSkipsProfileQueries() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        when(mapper.findByCompany(9)).thenReturn(List.of(employee(1, 9)));
        MyBatisEmployeeRepository repository = new MyBatisEmployeeRepository(mapper);

        repository.findByCompany(9, false);

        verify(mapper).findByCompany(9);
        verifyNoMoreInteractions(mapper);
    }

    private Employee employee(long id, long companyId) {
        Employee employee = new Employee();
        employee.id = id;
        employee.companyId = companyId;
        employee.status = "active";
        return employee;
    }
}
