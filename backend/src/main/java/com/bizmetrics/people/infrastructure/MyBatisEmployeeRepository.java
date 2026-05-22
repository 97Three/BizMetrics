package com.bizmetrics.people.infrastructure;

import com.bizmetrics.people.application.EmployeeRepository;
import com.bizmetrics.people.domain.Employee;
import com.bizmetrics.people.domain.EmployeeCertificate;
import com.bizmetrics.people.domain.EmployeeCompensationPolicy;
import com.bizmetrics.people.domain.EmployeeExperience;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisEmployeeRepository implements EmployeeRepository {
    private final EmployeeMapper mapper;

    public MyBatisEmployeeRepository(EmployeeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Employee> findByCompany(long companyId, boolean includeProfileDetails) {
        List<Employee> employees = mapper.findByCompany(companyId).stream().map(this::hydrate).toList();
        if (includeProfileDetails) attachProfileDetails(companyId, employees);
        return employees;
    }

    @Override
    public List<Employee> findAll() {
        return mapper.findAll().stream().map(this::hydrate).toList();
    }

    @Override
    public Optional<Employee> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::hydrate);
    }

    @Override
    public Optional<Employee> findByIdForUpdate(long id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id)).map(this::hydrate);
    }

    @Override
    public Optional<Employee> findActiveByUser(long userId, long companyId) {
        return Optional.ofNullable(mapper.findActiveByUser(userId, companyId)).map(this::hydrate);
    }

    @Override
    public boolean existsByCompanyAndEmail(long companyId, String email) {
        return mapper.countByCompanyAndEmail(companyId, email) > 0;
    }

    @Override
    public Employee insert(Employee employee) {
        EmployeeCompensationPolicy.hydrate(employee);
        Object[] values = values(employee);
        Long id = mapper.insert(values);
        if (id == null) throw new IllegalStateException("Database did not return a generated employee id");
        employee.id = id;
        employee.departmentName = departmentName(employee.departmentId);
        return employee;
    }

    @Override
    public void update(Employee employee) {
        EmployeeCompensationPolicy.hydrate(employee);
        int updated = mapper.update(values(employee), employee.id, employee.companyId);
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Employee was changed by another request: " + employee.id);
        }
        employee.departmentName = departmentName(employee.departmentId);
    }

    @Override
    public List<EmployeeCertificate> replaceCertificates(
        long employeeId,
        List<EmployeeCertificate> certificates
    ) {
        mapper.deleteCertificates(employeeId);
        List<EmployeeCertificate> saved = new ArrayList<>();
        for (EmployeeCertificate certificate : certificates) {
            if (certificate == null || isBlank(certificate.name)) continue;
            certificate.employeeId = employeeId;
            certificate.name = certificate.name.trim();
            certificate.verificationStatus = defaultText(certificate.verificationStatus, "unverified");
            certificate.materialStatus = defaultText(certificate.materialStatus, "missing");
            String now = OffsetDateTime.now().toString();
            certificate.createdAt = now;
            certificate.updatedAt = now;
            certificate.id = requiredId(mapper.insertCertificate(certificate));
            saved.add(certificate);
        }
        return List.copyOf(saved);
    }

    @Override
    public List<EmployeeExperience> replaceExperiences(
        long employeeId,
        List<EmployeeExperience> experiences
    ) {
        mapper.deleteExperiences(employeeId);
        List<EmployeeExperience> saved = new ArrayList<>();
        for (EmployeeExperience experience : experiences) {
            if (experience == null
                || (isBlank(experience.organization) && isBlank(experience.title) && isBlank(experience.description))) {
                continue;
            }
            experience.employeeId = employeeId;
            experience.type = defaultText(experience.type, "work");
            experience.organization = defaultText(experience.organization, "未填写");
            String now = OffsetDateTime.now().toString();
            experience.createdAt = now;
            experience.updatedAt = now;
            experience.id = requiredId(mapper.insertExperience(experience));
            saved.add(experience);
        }
        return List.copyOf(saved);
    }

    @Override
    public void deleteForDemoReset(long id) {
        mapper.deleteCertificates(id);
        mapper.deleteExperiences(id);
        mapper.deletePayrollRunItems(id);
        mapper.deleteEmployee(id);
    }

    private Employee hydrate(Employee employee) {
        if (employee.createdAt != null) employee.createdAt = normalizeTimestamp(employee.createdAt);
        if (employee.updatedAt != null) employee.updatedAt = normalizeTimestamp(employee.updatedAt);
        if (employee.profileVerifiedAt != null) {
            employee.profileVerifiedAt = normalizeTimestamp(employee.profileVerifiedAt);
        }
        EmployeeCompensationPolicy.hydrate(employee);
        return employee;
    }

    private String normalizeTimestamp(String value) {
        return OffsetDateTime.parse(value.replace(' ', 'T')).toString();
    }

    private Object[] values(Employee employee) {
        return new Object[] {
            employee.companyId, employee.userId, employee.departmentId, normalizedNullable(employee.employeeNo),
            employee.name.trim(), normalizedNullable(employee.legalName), normalizedNullable(employee.preferredName),
            employee.email.trim().toLowerCase(Locale.ROOT), normalizedNullable(employee.phone), employee.position.trim(),
            employee.directManagerEmployeeId, normalizedNullable(employee.jobLevel), normalizedNullable(employee.workLocation),
            employee.employmentType, employee.status, employee.accessRole, employee.accessScope,
            LocalDate.parse(employee.hireDate), nullableDate(employee.leaveDate), nullableDate(employee.probationStartDate),
            nullableDate(employee.probationEndDate), nullableDate(employee.contractStartDate),
            nullableDate(employee.contractEndDate), normalizedNullable(employee.contractType),
            normalizedNullable(employee.contractStatus), normalizedNullable(employee.educationLevel),
            normalizedNullable(employee.graduationSchool), normalizedNullable(employee.major),
            nullableDate(employee.graduationDate), employee.graduationYear, normalizedNullable(employee.graduateStatus),
            normalizedNullable(employee.skillTags), normalizedNullable(employee.resumeSummary),
            normalizedNullable(employee.materialStatus), nullableTimestamp(employee.profileVerifiedAt),
            employee.profileVerifiedBy, employee.salary, employee.overtimeBase, employee.weekdayOvertimeHours,
            employee.restDayOvertimeHours, employee.holidayOvertimeHours, employee.overtimePay,
            normalizedNullable(employee.overtimePolicyNote), employee.socialInsurance, employee.housingFund,
            employee.taxEstimate, employee.socialInsuranceBase, employee.socialInsurancePersonalRate,
            employee.socialInsuranceCompanyRate, employee.socialInsurancePersonalAmount,
            employee.socialInsuranceCompanyAmount, employee.housingFundBase, employee.housingFundPersonalRate,
            employee.housingFundCompanyRate, employee.housingFundPersonalAmount, employee.housingFundCompanyAmount,
            employee.personalDeduction, employee.netPayEstimate, employee.socialInsuranceRegion.trim(),
            employee.hukouType, employee.medicalTier, employee.pensionBase, employee.medicalBase,
            employee.unemploymentBase, employee.workInjuryBase, employee.maternityBase,
            employee.workInjuryCompanyRate, normalizedNullable(employee.socialInsurancePolicyNote), employee.monthlyCost,
            normalizedNullable(employee.emergencyContact), OffsetDateTime.parse(employee.createdAt),
            OffsetDateTime.parse(employee.updatedAt)
        };
    }

    private void attachProfileDetails(long companyId, List<Employee> employees) {
        if (employees.isEmpty()) return;
        Map<Long, List<EmployeeCertificate>> certificates = new HashMap<>();
        mapper.findCertificatesByCompany(companyId).forEach(certificate ->
            certificates.computeIfAbsent(certificate.employeeId, ignored -> new ArrayList<>()).add(certificate));
        Map<Long, List<EmployeeExperience>> experiences = new HashMap<>();
        mapper.findExperiencesByCompany(companyId).forEach(experience ->
            experiences.computeIfAbsent(experience.employeeId, ignored -> new ArrayList<>()).add(experience));
        for (Employee employee : employees) {
            employee.certificates = List.copyOf(certificates.getOrDefault(employee.id, List.of()));
            employee.experiences = List.copyOf(experiences.getOrDefault(employee.id, List.of()));
        }
    }

    private String departmentName(Long departmentId) {
        return departmentId == null ? null : mapper.departmentName(departmentId);
    }

    private LocalDate nullableDate(String value) {
        return isBlank(value) ? null : LocalDate.parse(value.trim());
    }

    private OffsetDateTime nullableTimestamp(String value) {
        return isBlank(value) ? null : OffsetDateTime.parse(value.trim());
    }

    private String normalizedNullable(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long requiredId(Long value) {
        if (value == null) throw new IllegalStateException("Database did not return a generated profile id");
        return value;
    }
}
