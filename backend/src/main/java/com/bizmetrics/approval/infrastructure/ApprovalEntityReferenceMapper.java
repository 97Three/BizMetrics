package com.bizmetrics.approval.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalEntityReferenceMapper {
    @Select("SELECT COUNT(*) FROM transactions WHERE id = #{id} AND company_id = #{companyId}")
    int countTransactions(@Param("id") long id, @Param("companyId") long companyId);

    @Select("SELECT COUNT(*) FROM budgets WHERE id = #{id} AND company_id = #{companyId}")
    int countBudgets(@Param("id") long id, @Param("companyId") long companyId);

    @Select("SELECT COUNT(*) FROM employees WHERE id = #{id} AND company_id = #{companyId}")
    int countEmployees(@Param("id") long id, @Param("companyId") long companyId);

    @Select("SELECT COUNT(*) FROM payroll_runs WHERE id = #{id} AND company_id = #{companyId}")
    int countPayrollRuns(@Param("id") long id, @Param("companyId") long companyId);
}
