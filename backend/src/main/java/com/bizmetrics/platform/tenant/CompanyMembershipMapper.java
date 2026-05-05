package com.bizmetrics.platform.tenant;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyMembershipMapper {
    @Select("""
        SELECT company_id, user_id, department_id, role, scope, status
        FROM company_memberships WHERE user_id = #{userId} AND company_id = #{companyId}
        """)
    CompanyMembership find(@Param("userId") long userId, @Param("companyId") long companyId);

    @Select("""
        SELECT company_id, user_id, department_id, role, scope, status
        FROM company_memberships WHERE user_id = #{userId} AND status = 'active'
        ORDER BY company_id
        """)
    List<CompanyMembership> findActiveByUser(long userId);

    @Select("""
        SELECT company_id, user_id, department_id, role, scope, status
        FROM company_memberships WHERE company_id = #{companyId} AND status = 'active'
        ORDER BY user_id
        """)
    List<CompanyMembership> findActiveByCompany(long companyId);

    @Insert("""
        INSERT INTO company_memberships (
            company_id, user_id, department_id, role, scope, status, created_at, updated_at
        ) VALUES (#{companyId}, #{userId}, #{departmentId}, #{role}, #{scope}, #{status},
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (company_id, user_id) DO UPDATE SET
            department_id = CASE WHEN #{forceOwner} THEN company_memberships.department_id ELSE EXCLUDED.department_id END,
            role = CASE WHEN #{forceOwner} OR company_memberships.role = 'founder' THEN 'founder' ELSE EXCLUDED.role END,
            scope = CASE WHEN #{forceOwner} OR company_memberships.role = 'founder' THEN 'company' ELSE EXCLUDED.scope END,
            status = EXCLUDED.status,
            updated_at = CURRENT_TIMESTAMP
        """)
    void upsert(@Param("companyId") long companyId, @Param("userId") long userId,
                @Param("departmentId") Long departmentId, @Param("role") String role,
                @Param("scope") String scope, @Param("status") String status,
                @Param("forceOwner") boolean forceOwner);
}
