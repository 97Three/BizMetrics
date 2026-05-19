package com.bizmetrics.evidence.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReceiptStorageReferenceMapper {
    record ReferenceRow(String bucket, String objectKey) {
    }

    @ConstructorArgs({
        @Arg(column = "file_bucket", javaType = String.class),
        @Arg(column = "file_object_key", javaType = String.class)
    })
    @Select("""
        SELECT file_bucket, file_object_key
        FROM receipt_vouchers
        WHERE file_storage_provider = 'minio'
        ORDER BY id
        LIMIT #{limit}
        """)
    List<ReferenceRow> findAll(@Param("limit") int limit);
}
