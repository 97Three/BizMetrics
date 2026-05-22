package com.bizmetrics.people.infrastructure;

import com.bizmetrics.people.domain.Department;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentMapper {
    @Results(id = "departmentResult", value = {
        @Result(column = "id", property = "id", id = true),
        @Result(column = "company_id", property = "companyId"),
        @Result(column = "name", property = "name"),
        @Result(column = "cost_center", property = "costCenter"),
        @Result(column = "manager_employee_id", property = "managerEmployeeId"),
        @Result(column = "budget", property = "budget"),
        @Result(column = "status", property = "status"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "updated_at", property = "updatedAt")
    })
    @Select("""
        SELECT id, company_id, name, cost_center, manager_employee_id, budget, status, created_at, updated_at
        FROM departments WHERE company_id = #{companyId} ORDER BY id
        """)
    List<Department> findByCompany(long companyId);

    @ResultMap("departmentResult")
    @Select("""
        SELECT id, company_id, name, cost_center, manager_employee_id, budget, status, created_at, updated_at
        FROM departments WHERE id = #{id}
        """)
    Department findById(long id);

    @ResultMap("departmentResult")
    @Select("""
        SELECT id, company_id, name, cost_center, manager_employee_id, budget, status, created_at, updated_at
        FROM departments WHERE id = #{id} FOR UPDATE
        """)
    Department findByIdForUpdate(long id);

    @Select(value = """
        INSERT INTO departments (company_id, name, cost_center, manager_employee_id,
            budget, status, created_at, updated_at)
        VALUES (#{department.companyId}, #{department.name}, #{department.costCenter},
            #{department.managerEmployeeId}, #{department.budget}, #{department.status},
            #{createdAt}, #{updatedAt}) RETURNING id
        """, affectData = true)
    Long insert(@Param("department") Department department, @Param("createdAt") OffsetDateTime createdAt,
                @Param("updatedAt") OffsetDateTime updatedAt);

    @Update("""
        UPDATE departments SET name = #{department.name}, cost_center = #{department.costCenter},
            manager_employee_id = #{department.managerEmployeeId}, budget = #{department.budget},
            status = #{department.status}, updated_at = #{updatedAt}
        WHERE id = #{department.id} AND company_id = #{department.companyId}
        """)
    int update(@Param("department") Department department, @Param("updatedAt") OffsetDateTime updatedAt);

    @Select("SELECT COUNT(*) FROM employees WHERE id = #{employeeId} AND company_id = #{companyId}")
    int employeeCount(@Param("employeeId") long employeeId, @Param("companyId") long companyId);
}
