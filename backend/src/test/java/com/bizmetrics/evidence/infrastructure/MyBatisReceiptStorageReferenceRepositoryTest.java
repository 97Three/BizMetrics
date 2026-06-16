package com.bizmetrics.evidence.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bizmetrics.evidence.application.ReceiptStorageReferenceSnapshot;
import com.bizmetrics.evidence.domain.ReceiptObjectLocation;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bizmetrics.evidence.infrastructure.ReceiptStorageReferenceMapper.ReferenceRow;

class MyBatisReceiptStorageReferenceRepositoryTest {

    @Test
    void readsMinioReferencesAndFallsBackToTheCurrentBucket() throws Exception {
        ReceiptStorageReferenceMapper mapper = mock(ReceiptStorageReferenceMapper.class);
        when(mapper.findAll(101)).thenReturn(List.of(
            new ReferenceRow(" ", "receipts/current.pdf"),
            new ReferenceRow("archive", "receipts/archive.pdf"),
            new ReferenceRow("archive", " ")
        ));
        MyBatisReceiptStorageReferenceRepository repository = new MyBatisReceiptStorageReferenceRepository(mapper);

        ReceiptStorageReferenceSnapshot snapshot = repository.findAll("bizmetrics", 100);

        assertEquals(List.of(
            new ReceiptObjectLocation("bizmetrics", "receipts/current.pdf"),
            new ReceiptObjectLocation("archive", "receipts/archive.pdf")
        ), snapshot.references());
        assertEquals(1, snapshot.invalidReferenceCount());
    }

    @Test
    void failsInsteadOfReturningATruncatedReferenceInventory() {
        ReceiptStorageReferenceMapper mapper = mock(ReceiptStorageReferenceMapper.class);
        when(mapper.findAll(2)).thenReturn(Arrays.asList(null, null));
        MyBatisReceiptStorageReferenceRepository repository = new MyBatisReceiptStorageReferenceRepository(mapper);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> repository.findAll("bizmetrics", 1)
        );

        assertTrue(exception.getMessage().contains("exceeded configured maximum"));
    }

}
