package com.bizmetrics.bootstrap;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Small SQL boundary for bootstrap-only locking and demo reset. */
@Mapper
public interface BootstrapMaintenanceMapper {
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{lockName}, 0))) lock_result")
    int acquireLock(@Param("lockName") String lockName);

    @Delete("DELETE FROM payroll_run_items WHERE run_id IN (SELECT id FROM payroll_runs WHERE company_id = #{companyId})")
    int deletePayrollItems(@Param("companyId") long companyId);

    @Delete("DELETE FROM payroll_runs WHERE company_id = #{companyId}")
    int deletePayrollRuns(@Param("companyId") long companyId);
}
