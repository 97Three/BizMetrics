package com.bizmetrics.evidence.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bizmetrics.evidence.domain.ReceiptVoucher;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class MyBatisReceiptVoucherRepositoryTest {
    @Test
    void returnsDatabaseCount() {
        ReceiptVoucherMapper mapper = mock(ReceiptVoucherMapper.class);
        when(mapper.count()).thenReturn(7L);
        assertEquals(7, new MyBatisReceiptVoucherRepository(mapper).count());
    }

    @Test
    void rejectsAStaleVoucherUpdate() {
        ReceiptVoucherMapper mapper = mock(ReceiptVoucherMapper.class);
        MyBatisReceiptVoucherRepository repository = new MyBatisReceiptVoucherRepository(mapper);
        ReceiptVoucher voucher = new ReceiptVoucher();
        voucher.id = 42;
        voucher.version = 3;

        OptimisticLockingFailureException exception = assertThrows(
            OptimisticLockingFailureException.class, () -> repository.save(voucher));
        assertEquals("Receipt voucher was changed by another request: 42", exception.getMessage());
        assertEquals(3, voucher.version);
    }

    @Test
    void advancesVersionAfterAStoredUpdate() {
        ReceiptVoucherMapper mapper = mock(ReceiptVoucherMapper.class);
        MyBatisReceiptVoucherRepository repository = new MyBatisReceiptVoucherRepository(mapper);
        ReceiptVoucher voucher = new ReceiptVoucher();
        voucher.id = 42;
        voucher.version = 3;
        when(mapper.save(voucher)).thenReturn(1);

        repository.save(voucher);
        assertEquals(4, voucher.version);
    }
}
