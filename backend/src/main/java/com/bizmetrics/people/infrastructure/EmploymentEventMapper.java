package com.bizmetrics.people.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmploymentEventMapper {
    record Row(long id, long companyId, long employeeId, String type, LocalDate effectiveDate,
               String note, long operatorUserId, OffsetDateTime createdAt) {
    }

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class),
        @Arg(column = "company_id", javaType = long.class),
        @Arg(column = "employee_id", javaType = long.class),
        @Arg(column = "type", javaType = String.class),
        @Arg(column = "effective_date", javaType = LocalDate.class),
        @Arg(column = "note", javaType = String.class),
        @Arg(column = "operator_user_id", javaType = long.class),
        @Arg(column = "created_at", javaType = OffsetDateTime.class)
    })
    @Select("""
        SELECT id, company_id, employee_id, type, effective_date, note, operator_user_id, created_at
        FROM employment_events WHERE company_id = #{companyId} ORDER BY effective_date DESC, id
        """)
    List<Row> findByCompany(@Param("companyId") long companyId);

    @Select(value = """
        INSERT INTO employment_events (company_id, employee_id, type, effective_date, note, operator_user_id, created_at)
        VALUES (#{companyId}, #{employeeId}, #{type}, #{effectiveDate}, #{note}, #{operatorUserId}, #{createdAt})
        RETURNING id
        """, affectData = true)
    Long append(@Param("companyId") long companyId, @Param("employeeId") long employeeId,
                @Param("type") String type, @Param("effectiveDate") LocalDate effectiveDate,
                @Param("note") String note, @Param("operatorUserId") long operatorUserId,
                @Param("createdAt") OffsetDateTime createdAt);

    @Delete("DELETE FROM employment_events WHERE employee_id = #{employeeId}")
    int deleteByEmployeeForDemoReset(@Param("employeeId") long employeeId);
}
