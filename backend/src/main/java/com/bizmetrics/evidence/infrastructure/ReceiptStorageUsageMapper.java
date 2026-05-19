package com.bizmetrics.evidence.infrastructure;

import com.bizmetrics.evidence.domain.ReceiptStorageUsage;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReceiptStorageUsageMapper {
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{lockName}, 0))) lock_result")
    int lock(@Param("lockName") String lockName);

    @ConstructorArgs({
        @Arg(column = "object_count", javaType = long.class),
        @Arg(column = "used_bytes", javaType = long.class)
    })
    @Select("""
        SELECT COUNT(*) AS object_count,
               COALESCE(SUM(COALESCE((
                   SELECT MAX(file_hash.file_size)
                   FROM receipt_file_hashes file_hash
                   WHERE file_hash.company_id = voucher.company_id
                     AND file_hash.voucher_id = voucher.id
               ), voucher.file_size)), 0) AS used_bytes
        FROM receipt_vouchers voucher
        WHERE voucher.company_id = #{companyId} AND voucher.file_storage_provider = 'minio'
        """)
    ReceiptStorageUsage findByCompany(@Param("companyId") long companyId);
}
