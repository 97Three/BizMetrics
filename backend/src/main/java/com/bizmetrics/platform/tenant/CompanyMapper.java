package com.bizmetrics.platform.tenant;

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
public interface CompanyMapper {
    @Results(id = "companyResult", value = {
        @Result(column="id", property="id", id=true),
        @Result(column="version", property="version"),
        @Result(column="name", property="name"),
        @Result(column="entity_type", property="entityType"),
        @Result(column="credit_code", property="creditCode"),
        @Result(column="industry", property="industry"),
        @Result(column="taxpayer_type", property="taxpayerType"),
        @Result(column="currency", property="currency"),
        @Result(column="country", property="country"),
        @Result(column="province", property="province"),
        @Result(column="city", property="city"),
        @Result(column="district", property="district"),
        @Result(column="registered_address", property="registeredAddress"),
        @Result(column="operating_region", property="operatingRegion"),
        @Result(column="tax_authority", property="taxAuthority"),
        @Result(column="policy_profile_key", property="policyProfileKey"),
        @Result(column="fiscal_year_start_month", property="fiscalYearStartMonth"),
        @Result(column="owner_id", property="ownerId"),
        @Result(column="created_at", property="createdAt"),
        @Result(column="updated_at", property="updatedAt")
    })
    @Select("""
        SELECT id, version, name, entity_type, credit_code, industry, taxpayer_type, currency,
            country, province, city, district, registered_address, operating_region,
            tax_authority, policy_profile_key, fiscal_year_start_month, owner_id, created_at, updated_at
        FROM companies ORDER BY id
        """)
    List<Company> findAll();

    @ResultMap("companyResult")
    @Select("""
        SELECT id, version, name, entity_type, credit_code, industry, taxpayer_type, currency,
            country, province, city, district, registered_address, operating_region,
            tax_authority, policy_profile_key, fiscal_year_start_month, owner_id, created_at, updated_at
        FROM companies WHERE id = #{id}
        """)
    Company findById(@Param("id") long id);

    @Select("SELECT EXISTS(SELECT 1 FROM companies)")
    boolean existsAny();

    @Select(value = """
        INSERT INTO companies (version, name, entity_type, credit_code, industry, taxpayer_type, currency,
            country, province, city, district, registered_address, operating_region,
            tax_authority, policy_profile_key, fiscal_year_start_month, owner_id, created_at, updated_at)
        VALUES (#{company.version}, #{company.name}, #{company.entityType}, #{company.creditCode},
            #{company.industry}, #{company.taxpayerType}, #{company.currency}, #{company.country},
            #{company.province}, #{company.city}, #{company.district}, #{company.registeredAddress},
            #{company.operatingRegion}, #{company.taxAuthority}, #{company.policyProfileKey},
            #{company.fiscalYearStartMonth}, #{company.ownerId}, #{createdAt}, #{updatedAt}) RETURNING id
        """, affectData = true)
    Long insert(@Param("company") Company company, @Param("createdAt") OffsetDateTime createdAt,
                @Param("updatedAt") OffsetDateTime updatedAt);

    @Update("""
        UPDATE companies SET version = version + 1, name = #{company.name},
            entity_type = #{company.entityType}, credit_code = #{company.creditCode},
            industry = #{company.industry}, taxpayer_type = #{company.taxpayerType},
            currency = #{company.currency}, country = #{company.country}, province = #{company.province},
            city = #{company.city}, district = #{company.district},
            registered_address = #{company.registeredAddress}, operating_region = #{company.operatingRegion},
            tax_authority = #{company.taxAuthority}, policy_profile_key = #{company.policyProfileKey},
            fiscal_year_start_month = #{company.fiscalYearStartMonth}, updated_at = #{updatedAt}
        WHERE id = #{company.id} AND version = #{company.version}
        """)
    int update(@Param("company") Company company, @Param("updatedAt") OffsetDateTime updatedAt);
}
