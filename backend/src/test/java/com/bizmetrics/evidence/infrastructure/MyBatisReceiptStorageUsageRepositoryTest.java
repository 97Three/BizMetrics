package com.bizmetrics.evidence.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizmetrics.evidence.domain.ReceiptStorageUsage;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Select;

class MyBatisReceiptStorageUsageRepositoryTest {
    @Test
    void locksTheCompanyCapacityInsideTheCurrentTransaction() {
        ReceiptStorageUsageMapper mapper = mock(ReceiptStorageUsageMapper.class);
        MyBatisReceiptStorageUsageRepository repository = new MyBatisReceiptStorageUsageRepository(mapper);

        repository.lockCompany(7L);

        verify(mapper).lock("receipt-storage:7");
    }

    @Test
    void sumsOnlyCommittedMinioReceiptMetadataForTheCompany() {
        ReceiptStorageUsageMapper mapper = mock(ReceiptStorageUsageMapper.class);
        ReceiptStorageUsage expected = new ReceiptStorageUsage(3L, 4_096L);
        when(mapper.findByCompany(7L)).thenReturn(expected);
        MyBatisReceiptStorageUsageRepository repository = new MyBatisReceiptStorageUsageRepository(mapper);

        assertEquals(expected, repository.findByCompany(7L));

        verify(mapper).findByCompany(7L);
        String sql = String.join(" ", findUsageSql());
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("voucher.company_id = #{companyId}"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("voucher.file_storage_provider = 'minio'"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("receipt_file_hashes"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("MAX(file_hash.file_size)"));
    }

    private String[] findUsageSql() {
        try {
            return ReceiptStorageUsageMapper.class.getMethod("findByCompany", long.class).getAnnotation(Select.class).value();
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
