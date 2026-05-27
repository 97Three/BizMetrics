package com.bizmetrics.service;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface BackupMapper {
    @SelectProvider(type = BackupSqlProvider.class, method = "selectAll")
    List<Map<String, Object>> selectAll(@Param("table") String table, @Param("orderById") boolean orderById);

    @SelectProvider(type = BackupSqlProvider.class, method = "count")
    int count(@Param("table") String table);

    @UpdateProvider(type = BackupSqlProvider.class, method = "truncate")
    int truncate(@Param("tables") List<String> tables);

    @InsertProvider(type = BackupSqlProvider.class, method = "insertBatch")
    int insertBatch(@Param("table") String table, @Param("columns") List<String> columns,
                    @Param("columnTypes") Map<String, String> columnTypes,
                    @Param("rows") List<Map<String, Object>> rows);

    @Select("""
        SELECT column_name AS column_name, data_type AS data_type
        FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = #{table}
        ORDER BY ordinal_position
        """)
    List<Map<String, Object>> columnTypes(@Param("table") String table);

    @Select("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = #{table} AND column_name = #{column}
        """)
    int hasColumn(@Param("table") String table, @Param("column") String column);

    @Select("SELECT company_id FROM ledgers WHERE id = #{ledgerId}")
    Long ledgerCompanyId(@Param("ledgerId") long ledgerId);

    @Select("SELECT pg_get_serial_sequence(#{table}, 'id')")
    String serialSequence(@Param("table") String table);

    @SelectProvider(type = BackupSqlProvider.class, method = "resetSequence", affectData = true)
    Long resetSequence(@Param("table") String table);

    @Insert("""
        INSERT INTO company_memberships (company_id, user_id, role, scope, status)
        SELECT company.id, company.owner_id, 'founder', 'company', 'active'
        FROM companies company JOIN users owner_user ON owner_user.id = company.owner_id
        ON CONFLICT (company_id, user_id) DO NOTHING
        """)
    int rebuildFounderMemberships();

    @Insert("""
        INSERT INTO company_memberships (company_id, user_id, department_id, role, scope, status)
        SELECT employee.company_id, employee.user_id, employee.department_id,
            employee.access_role, employee.access_scope,
            CASE WHEN employee.status = 'departed' THEN 'inactive' ELSE 'active' END
        FROM employees employee JOIN users employee_user ON employee_user.id = employee.user_id
        WHERE employee.user_id IS NOT NULL
        ON CONFLICT (company_id, user_id) DO UPDATE SET
            department_id = EXCLUDED.department_id,
            role = CASE WHEN company_memberships.role = 'founder' THEN company_memberships.role ELSE EXCLUDED.role END,
            scope = CASE WHEN company_memberships.role = 'founder' THEN company_memberships.scope ELSE EXCLUDED.scope END,
            status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP
        """)
    int rebuildEmployeeMemberships();
}
