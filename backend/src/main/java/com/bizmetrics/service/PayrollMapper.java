package com.bizmetrics.service;

import com.bizmetrics.domain.Models.PayrollRun;
import com.bizmetrics.domain.Models.PayrollRunItem;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PayrollMapper {
    @Select("""
        SELECT * FROM payroll_runs WHERE company_id = #{companyId}
        ORDER BY period DESC, id DESC
        """)
    List<PayrollRun> listRuns(@Param("companyId") long companyId);

    @Select("""
        SELECT * FROM payroll_runs WHERE company_id = #{companyId} AND period = #{period}
        ORDER BY id DESC
        """)
    List<PayrollRun> listRunsByPeriod(@Param("companyId") long companyId, @Param("period") String period);

    @Select("SELECT * FROM payroll_runs WHERE id = #{id} FOR UPDATE")
    PayrollRun findForUpdate(@Param("id") long id);

    @Select("SELECT COUNT(1) FROM payroll_runs WHERE company_id = #{companyId} AND period = #{period}")
    int countPeriod(@Param("companyId") long companyId, @Param("period") String period);

    @Insert("""
        INSERT INTO payroll_runs (
            company_id, period, name, status, employee_count, salary_total, social_personal_total,
            social_company_total, housing_personal_total, housing_company_total, tax_total,
            personal_deduction_total, net_pay_total, company_cost_total, created_by_user_id,
            closed_by_user_id, closed_at, created_at, updated_at
        ) VALUES (#{companyId}, #{period}, #{name}, #{status}, #{employeeCount}, #{salaryTotal},
            #{socialPersonalTotal}, #{socialCompanyTotal}, #{housingPersonalTotal}, #{housingCompanyTotal},
            #{taxTotal}, #{personalDeductionTotal}, #{netPayTotal}, #{companyCostTotal},
            #{createdByUserId}, #{closedByUserId}, #{closedAt}, #{createdAt}, #{updatedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertRun(PayrollRun run);

    @Insert("""
        INSERT INTO payroll_run_items (
            run_id, company_id, employee_id, employee_name, department_name, period, salary, payable_salary,
            social_personal_amount, social_company_amount, housing_personal_amount, housing_company_amount,
            tax_amount, personal_deduction, net_pay, company_cost, snapshot_json, created_at
        ) VALUES (#{runId}, #{companyId}, #{employeeId}, #{employeeName}, #{departmentName}, #{period},
            #{salary}, #{payableSalary}, #{socialPersonalAmount}, #{socialCompanyAmount},
            #{housingPersonalAmount}, #{housingCompanyAmount}, #{taxAmount}, #{personalDeduction},
            #{netPay}, #{companyCost}, #{snapshotJson}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertItem(PayrollRunItem item);

    @Update("""
        UPDATE payroll_runs SET status = 'closed', closed_by_user_id = #{userId},
            closed_at = #{now}, updated_at = #{now} WHERE id = #{id}
        """)
    int closeRun(@Param("id") long id, @Param("userId") long userId, @Param("now") String now);

    @Select("SELECT * FROM payroll_run_items WHERE run_id = #{runId} ORDER BY employee_name, id")
    List<PayrollRunItem> itemsForRun(@Param("runId") long runId);

    @Select("""
        <script>
        SELECT * FROM payroll_run_items WHERE run_id IN
        <foreach collection='runIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
        ORDER BY run_id, employee_name, id
        </script>
        """)
    List<PayrollRunItem> itemsForRuns(@Param("runIds") List<Long> runIds);
}
