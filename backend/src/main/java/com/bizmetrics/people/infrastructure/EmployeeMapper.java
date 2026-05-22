package com.bizmetrics.people.infrastructure;

import com.bizmetrics.people.domain.Employee;
import com.bizmetrics.people.domain.EmployeeCertificate;
import com.bizmetrics.people.domain.EmployeeExperience;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmployeeMapper {
    @Select("SELECT employee.*, (SELECT department.name FROM departments department WHERE department.id = employee.department_id) AS department_name FROM employees employee WHERE employee.company_id = #{companyId} ORDER BY CASE WHEN employee.status = 'departed' THEN 1 ELSE 0 END, employee.id")
    List<Employee> findByCompany(long companyId);

    @Select("SELECT employee.*, (SELECT department.name FROM departments department WHERE department.id = employee.department_id) AS department_name FROM employees employee ORDER BY employee.company_id, employee.id")
    List<Employee> findAll();

    @Select("SELECT employee.*, (SELECT department.name FROM departments department WHERE department.id = employee.department_id) AS department_name FROM employees employee WHERE employee.id = #{id}")
    Employee findById(long id);

    @Select("SELECT employee.*, (SELECT department.name FROM departments department WHERE department.id = employee.department_id) AS department_name FROM employees employee WHERE employee.id = #{id} FOR UPDATE")
    Employee findByIdForUpdate(long id);

    @Select("SELECT employee.*, (SELECT department.name FROM departments department WHERE department.id = employee.department_id) AS department_name FROM employees employee WHERE employee.user_id = #{userId} AND employee.company_id = #{companyId} AND employee.status <> 'departed' ORDER BY employee.id LIMIT 1")
    Employee findActiveByUser(@Param("userId") long userId, @Param("companyId") long companyId);

    @Select("SELECT COUNT(*) FROM employees WHERE company_id = #{companyId} AND LOWER(email) = LOWER(#{email})")
    int countByCompanyAndEmail(@Param("companyId") long companyId, @Param("email") String email);

    @Select(value = """
        INSERT INTO employees (company_id, user_id, department_id, employee_no, name, legal_name, preferred_name, email, phone, position, direct_manager_employee_id, job_level, work_location, employment_type, status, access_role, access_scope, hire_date, leave_date, probation_start_date, probation_end_date, contract_start_date, contract_end_date, contract_type, contract_status, education_level, graduation_school, major, graduation_date, graduation_year, graduate_status, skill_tags, resume_summary, material_status, profile_verified_at, profile_verified_by, salary, overtime_base, weekday_overtime_hours, rest_day_overtime_hours, holiday_overtime_hours, overtime_pay, overtime_policy_note, social_insurance, housing_fund, tax_estimate, social_insurance_base, social_insurance_personal_rate, social_insurance_company_rate, social_insurance_personal_amount, social_insurance_company_amount, housing_fund_base, housing_fund_personal_rate, housing_fund_company_rate, housing_fund_personal_amount, housing_fund_company_amount, personal_deduction, net_pay_estimate, social_insurance_region, hukou_type, medical_tier, pension_base, medical_base, unemployment_base, work_injury_base, maternity_base, work_injury_company_rate, social_insurance_policy_note, monthly_cost, emergency_contact, created_at, updated_at)
        VALUES (#{values[0]}, #{values[1]}, #{values[2]}, #{values[3]}, #{values[4]}, #{values[5]}, #{values[6]}, #{values[7]}, #{values[8]}, #{values[9]}, #{values[10]}, #{values[11]}, #{values[12]}, #{values[13]}, #{values[14]}, #{values[15]}, #{values[16]}, #{values[17]}, #{values[18]}, #{values[19]}, #{values[20]}, #{values[21]}, #{values[22]}, #{values[23]}, #{values[24]}, #{values[25]}, #{values[26]}, #{values[27]}, #{values[28]}, #{values[29]}, #{values[30]}, #{values[31]}, #{values[32]}, #{values[33]}, #{values[34]}, #{values[35]}, #{values[36]}, #{values[37]}, #{values[38]}, #{values[39]}, #{values[40]}, #{values[41]}, #{values[42]}, #{values[43]}, #{values[44]}, #{values[45]}, #{values[46]}, #{values[47]}, #{values[48]}, #{values[49]}, #{values[50]}, #{values[51]}, #{values[52]}, #{values[53]}, #{values[54]}, #{values[55]}, #{values[56]}, #{values[57]}, #{values[58]}, #{values[59]}, #{values[60]}, #{values[61]}, #{values[62]}, #{values[63]}, #{values[64]}, #{values[65]}, #{values[66]}, #{values[67]}, #{values[68]}, #{values[69]}, #{values[70]}, #{values[71]}) RETURNING id
        """, affectData = true)
    Long insert(@Param("values") Object[] values);

    @Update("""
        UPDATE employees SET user_id = #{values[1]}, department_id = #{values[2]}, employee_no = #{values[3]}, name = #{values[4]}, legal_name = #{values[5]}, preferred_name = #{values[6]}, email = #{values[7]}, phone = #{values[8]}, position = #{values[9]}, direct_manager_employee_id = #{values[10]}, job_level = #{values[11]}, work_location = #{values[12]}, employment_type = #{values[13]}, status = #{values[14]}, access_role = #{values[15]}, access_scope = #{values[16]}, hire_date = #{values[17]}, leave_date = #{values[18]}, probation_start_date = #{values[19]}, probation_end_date = #{values[20]}, contract_start_date = #{values[21]}, contract_end_date = #{values[22]}, contract_type = #{values[23]}, contract_status = #{values[24]}, education_level = #{values[25]}, graduation_school = #{values[26]}, major = #{values[27]}, graduation_date = #{values[28]}, graduation_year = #{values[29]}, graduate_status = #{values[30]}, skill_tags = #{values[31]}, resume_summary = #{values[32]}, material_status = #{values[33]}, profile_verified_at = #{values[34]}, profile_verified_by = #{values[35]}, salary = #{values[36]}, overtime_base = #{values[37]}, weekday_overtime_hours = #{values[38]}, rest_day_overtime_hours = #{values[39]}, holiday_overtime_hours = #{values[40]}, overtime_pay = #{values[41]}, overtime_policy_note = #{values[42]}, social_insurance = #{values[43]}, housing_fund = #{values[44]}, tax_estimate = #{values[45]}, social_insurance_base = #{values[46]}, social_insurance_personal_rate = #{values[47]}, social_insurance_company_rate = #{values[48]}, social_insurance_personal_amount = #{values[49]}, social_insurance_company_amount = #{values[50]}, housing_fund_base = #{values[51]}, housing_fund_personal_rate = #{values[52]}, housing_fund_company_rate = #{values[53]}, housing_fund_personal_amount = #{values[54]}, housing_fund_company_amount = #{values[55]}, personal_deduction = #{values[56]}, net_pay_estimate = #{values[57]}, social_insurance_region = #{values[58]}, hukou_type = #{values[59]}, medical_tier = #{values[60]}, pension_base = #{values[61]}, medical_base = #{values[62]}, unemployment_base = #{values[63]}, work_injury_base = #{values[64]}, maternity_base = #{values[65]}, work_injury_company_rate = #{values[66]}, social_insurance_policy_note = #{values[67]}, monthly_cost = #{values[68]}, emergency_contact = #{values[69]}, created_at = #{values[70]}, updated_at = #{values[71]}
        WHERE id = #{id} AND company_id = #{companyId}
        """)
    int update(@Param("values") Object[] values, @Param("id") long id, @Param("companyId") long companyId);

    @Delete("DELETE FROM employee_certificates WHERE employee_id = #{employeeId}")
    void deleteCertificates(long employeeId);

    @Select(value = """
        INSERT INTO employee_certificates (employee_id, name, category, level, issuer,
            certificate_no, issue_date, expiry_date, verification_status, material_status,
            note, created_at, updated_at)
        VALUES (#{certificate.employeeId}, #{certificate.name}, #{certificate.category},
            #{certificate.level}, #{certificate.issuer}, #{certificate.certificateNo},
            #{certificate.issueDate}, #{certificate.expiryDate}, #{certificate.verificationStatus},
            #{certificate.materialStatus}, #{certificate.note}, #{certificate.createdAt},
            #{certificate.updatedAt}) RETURNING id
        """, affectData = true)
    Long insertCertificate(@Param("certificate") EmployeeCertificate certificate);

    @Delete("DELETE FROM employee_experiences WHERE employee_id = #{employeeId}")
    void deleteExperiences(long employeeId);

    @Select(value = """
        INSERT INTO employee_experiences (employee_id, type, organization, title, start_date,
            end_date, description, achievements, skills, created_at, updated_at)
        VALUES (#{experience.employeeId}, #{experience.type}, #{experience.organization},
            #{experience.title}, #{experience.startDate}, #{experience.endDate},
            #{experience.description}, #{experience.achievements}, #{experience.skills},
            #{experience.createdAt}, #{experience.updatedAt}) RETURNING id
        """, affectData = true)
    Long insertExperience(@Param("experience") EmployeeExperience experience);

    @Delete("DELETE FROM payroll_run_items WHERE employee_id = #{employeeId}")
    void deletePayrollRunItems(long employeeId);

    @Delete("DELETE FROM employees WHERE id = #{id}")
    void deleteEmployee(long id);

    @Select("""
        SELECT certificate.* FROM employee_certificates certificate
        JOIN employees employee ON employee.id = certificate.employee_id
        WHERE employee.company_id = #{companyId}
        ORDER BY certificate.employee_id, COALESCE(certificate.expiry_date, '9999-12-31'), certificate.id
        """)
    List<EmployeeCertificate> findCertificatesByCompany(long companyId);

    @Select("""
        SELECT experience.* FROM employee_experiences experience
        JOIN employees employee ON employee.id = experience.employee_id
        WHERE employee.company_id = #{companyId}
        ORDER BY experience.employee_id, COALESCE(experience.start_date, '0000-01-01') DESC, experience.id DESC
        """)
    List<EmployeeExperience> findExperiencesByCompany(long companyId);

    @Select("SELECT name FROM departments WHERE id = #{departmentId}")
    String departmentName(long departmentId);
}

