package com.bizmetrics.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bizmetrics.people.application.DepartmentRepository;
import com.bizmetrics.people.application.EmployeeRepository;
import com.bizmetrics.people.application.EmploymentEventRepository;
import com.bizmetrics.people.domain.Department;
import com.bizmetrics.people.domain.Employee;
import com.bizmetrics.people.domain.EmploymentEvent;
import com.bizmetrics.platform.audit.application.AuditTrailService;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.identity.account.application.LocalUserAccountRepository;
import com.bizmetrics.platform.identity.account.application.UserDirectory;
import com.bizmetrics.platform.tenant.Company;
import com.bizmetrics.platform.tenant.CompanyMembershipRepository;
import com.bizmetrics.platform.tenant.CompanyRepository;
import com.bizmetrics.service.CompanyProvisioningService;
import com.bizmetrics.service.support.PasswordHasher;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProductionBootstrapCommandTest {
    private final BootstrapMaintenanceMapper bootstrapMapper = mock(BootstrapMaintenanceMapper.class);
    private final LocalUserAccountRepository userAccounts = mock(LocalUserAccountRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final AuditTrailService auditTrail = mock(AuditTrailService.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EmploymentEventRepository employmentEvents = mock(EmploymentEventRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyMembershipRepository memberships = mock(CompanyMembershipRepository.class);
    private final CompanyProvisioningService companyProvisioning = mock(CompanyProvisioningService.class);
    private final ProductionBootstrapCommand command = new ProductionBootstrapCommand(
        bootstrapMapper,
        userAccounts,
        userDirectory,
        passwordHasher,
        auditTrail,
        departments,
        employees,
        employmentEvents,
        companies,
        memberships,
        companyProvisioning
    );

    @Test
    void leavesAnInitializedTenantUntouched() {
        when(companies.existsAny()).thenReturn(true);

        assertEquals(ProductionBootstrapCommand.Outcome.ALREADY_INITIALIZED, command.execute(request()));

        verifyNoInteractions(userDirectory, passwordHasher, userAccounts, companyProvisioning, departments, employees);
    }

    @Test
    void createsTheFirstAdministratorAndCompleteTenantWorkspace() {
        when(userDirectory.findBootstrapOwner()).thenReturn(Optional.empty());
        when(passwordHasher.hash("Strong-pass-123!")).thenReturn("production-hash");
        when(userAccounts.insert(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.id = 7L;
            return user;
        });
        when(companyProvisioning.create(any())).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.id = 101L;
            return company;
        });
        when(departments.insert(any())).thenAnswer(invocation -> {
            Department department = invocation.getArgument(0);
            department.id = 201L;
            return department;
        });
        when(employees.insert(any())).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.id = 301L;
            return employee;
        });

        assertEquals(ProductionBootstrapCommand.Outcome.CREATED, command.execute(request()));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userAccounts).insert(user.capture());
        assertEquals("owner@bizmetrics.test", user.getValue().email);
        assertEquals("production-hash", user.getValue().passwordHash);

        ArgumentCaptor<Company> company = ArgumentCaptor.forClass(Company.class);
        verify(companyProvisioning).create(company.capture());
        assertEquals(7L, company.getValue().ownerId);

        ArgumentCaptor<Employee> founder = ArgumentCaptor.forClass(Employee.class);
        verify(employees).insert(founder.capture());
        assertEquals(101L, founder.getValue().companyId);
        assertEquals(201L, founder.getValue().departmentId);
        assertEquals("founder", founder.getValue().accessRole);
        verify(memberships).synchronize(founder.getValue());
        verify(employmentEvents).append(any(EmploymentEvent.class));
        verify(auditTrail).record(101L, "company", 101L, "bootstrap", "生产环境初始化公司主体: Company", 7L, "Owner");
    }

    @Test
    void rejectsWeakCredentialsBeforeHashingOrWriting() {
        when(userDirectory.findBootstrapOwner()).thenReturn(Optional.empty());
        ProductionBootstrapCommand.Request weak = new ProductionBootstrapCommand.Request(
            "owner@bizmetrics.test",
            "123456",
            "Owner",
            12,
            true,
            "Company",
            null,
            "Industry",
            "Taxpayer",
            "CNY"
        );

        assertThrows(IllegalStateException.class, () -> command.execute(weak));

        verifyNoInteractions(passwordHasher, userAccounts, companyProvisioning, departments, employees);
    }

    @Test
    void recoversAFirstCompanyForAnExistingAdministratorWithoutChangingCredentials() {
        when(userDirectory.findBootstrapOwner()).thenReturn(Optional.of(
            new UserDirectory.Entry(9L, "existing@bizmetrics.test", "Existing", "", null, 1, 15)
        ));
        when(companyProvisioning.create(any())).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.id = 102L;
            return company;
        });
        when(departments.insert(any())).thenAnswer(invocation -> {
            Department department = invocation.getArgument(0);
            department.id = 202L;
            return department;
        });
        when(employees.insert(any())).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.id = 302L;
            return employee;
        });

        assertEquals(ProductionBootstrapCommand.Outcome.CREATED, command.execute(request()));

        verify(userAccounts, never()).insert(any());
        verifyNoInteractions(passwordHasher);
        ArgumentCaptor<Company> company = ArgumentCaptor.forClass(Company.class);
        verify(companyProvisioning).create(company.capture());
        assertEquals(9L, company.getValue().ownerId);
    }

    private ProductionBootstrapCommand.Request request() {
        return new ProductionBootstrapCommand.Request(
            " Owner@BizMetrics.TEST ",
            "Strong-pass-123!",
            "Owner",
            12,
            true,
            "Company",
            null,
            "Industry",
            "Taxpayer",
            "CNY"
        );
    }
}
