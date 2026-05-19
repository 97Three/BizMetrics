package com.bizmetrics.evidence.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.OffsetDateTime;

@Mapper
public interface ReceiptFileHashMapper {
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{lockName}, 0))) lock_result")
    int lock(@Param("lockName") String lockName);

    @Select("""
        SELECT voucher_id FROM receipt_file_hashes
        WHERE company_id = #{companyId} AND sha256 = #{digest} LIMIT 1
        """)
    Long findVoucherId(@Param("companyId") long companyId, @Param("digest") String digest);

    @Insert("""
        INSERT INTO receipt_file_hashes (company_id, voucher_id, sha256, file_name, file_size, created_at)
        VALUES (#{companyId}, #{voucherId}, #{digest}, #{fileName}, #{fileSize}, #{createdAt})
        """)
    int register(
        @Param("companyId") long companyId,
        @Param("voucherId") long voucherId,
        @Param("digest") String digest,
        @Param("fileName") String fileName,
        @Param("fileSize") long fileSize,
        @Param("createdAt") OffsetDateTime createdAt
    );
}
