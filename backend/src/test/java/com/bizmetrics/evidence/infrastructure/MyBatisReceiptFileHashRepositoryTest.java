package com.bizmetrics.evidence.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizmetrics.evidence.application.ReceiptFileRegistration;
import com.bizmetrics.evidence.domain.ReceiptFileDigest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MyBatisReceiptFileHashRepositoryTest {
    private static final ReceiptFileDigest DIGEST = new ReceiptFileDigest("a".repeat(64));

    @Test
    void locksTheCompanyHashInsideTheCurrentTransaction() {
        ReceiptFileHashMapper mapper = mock(ReceiptFileHashMapper.class);
        MyBatisReceiptFileHashRepository repository = new MyBatisReceiptFileHashRepository(mapper);

        repository.lock(7L, DIGEST);

        verify(mapper).lock("receipt-file:7:" + DIGEST.value());
    }

    @Test
    void findsACompanyScopedDuplicateVoucher() {
        ReceiptFileHashMapper mapper = mock(ReceiptFileHashMapper.class);
        when(mapper.findVoucherId(7L, DIGEST.value())).thenReturn(42L);
        MyBatisReceiptFileHashRepository repository = new MyBatisReceiptFileHashRepository(mapper);

        assertEquals(42L, repository.findVoucherId(7L, DIGEST).orElseThrow());
    }

    @Test
    void returnsEmptyWhenTheAttachmentHashIsNew() {
        ReceiptFileHashMapper mapper = mock(ReceiptFileHashMapper.class);
        when(mapper.findVoucherId(7L, DIGEST.value())).thenReturn(null);
        MyBatisReceiptFileHashRepository repository = new MyBatisReceiptFileHashRepository(mapper);

        assertTrue(repository.findVoucherId(7L, DIGEST).isEmpty());
    }

    @Test
    void registersTheVoucherHash() {
        ReceiptFileHashMapper mapper = mock(ReceiptFileHashMapper.class);
        MyBatisReceiptFileHashRepository repository = new MyBatisReceiptFileHashRepository(mapper);

        OffsetDateTime createdAt = OffsetDateTime.parse("2026-09-05T12:00:00Z");
        repository.register(new ReceiptFileRegistration(7L, 42L, DIGEST, "receipt.pdf", 128L, createdAt));

        verify(mapper).register(7L, 42L, DIGEST.value(), "receipt.pdf", 128L, createdAt);
    }
}
