package com.bizmetrics.platform.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MyBatisEntityTransferRepositoryTest {
    @Test
    void appendNormalizesAndBindsTypedTransferValues() {
        EntityTransferMapper mapper = mock(EntityTransferMapper.class);
        when(mapper.insert(any(), any(), any(), any())).thenReturn(41L);
        when(mapper.findNames(3, 7)).thenReturn(Map.of("from_entity_name", "Source", "to_entity_name", "Target"));
        MyBatisEntityTransferRepository repository = new MyBatisEntityTransferRepository(mapper);

        EntityTransfer saved = repository.append(transfer());

        assertEquals(41, saved.id);
        assertEquals("shareholder_advance", saved.transferType);
        assertEquals(new BigDecimal("125.5"), saved.amount);
        assertEquals("CNY", saved.currency);
        assertEquals("working capital", saved.note);
        assertEquals("Source", saved.fromEntityName);
        assertEquals("Target", saved.toEntityName);
        ArgumentCaptor<LocalDate> date = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<OffsetDateTime> created = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper).insert(eq(saved), date.capture(), created.capture(), any(OffsetDateTime.class));
        assertEquals(LocalDate.parse("2026-09-04"), date.getValue());
        assertInstanceOf(OffsetDateTime.class, created.getValue());
    }

    @Test
    void accessibleHistoryIsScopedAndDeduplicatesIds() {
        EntityTransferMapper mapper = mock(EntityTransferMapper.class);
        when(mapper.findAccessible(List.of(3L, 7L), 7L)).thenReturn(List.of());
        MyBatisEntityTransferRepository repository = new MyBatisEntityTransferRepository(mapper);

        repository.findAccessible(List.of(3L, 7L, 3L), 7L);

        verify(mapper).findAccessible(List.of(3L, 7L), 7L);
    }

    @Test
    void emptyAccessSetDoesNotQueryTheDatabase() {
        EntityTransferMapper mapper = mock(EntityTransferMapper.class);
        MyBatisEntityTransferRepository repository = new MyBatisEntityTransferRepository(mapper);

        assertTrue(repository.findAccessible(List.of(), null).isEmpty());
        verify(mapper, never()).findAccessible(any(), any());
    }

    @Test
    void pairExistenceIsDirectionIndependent() {
        EntityTransferMapper mapper = mock(EntityTransferMapper.class);
        when(mapper.existsBetween(3, 7)).thenReturn(true);
        MyBatisEntityTransferRepository repository = new MyBatisEntityTransferRepository(mapper);

        assertTrue(repository.existsBetween(3, 7));
        verify(mapper).existsBetween(3, 7);
    }

    private EntityTransfer transfer() {
        EntityTransfer transfer = new EntityTransfer();
        transfer.fromEntityId = 3;
        transfer.toEntityId = 7;
        transfer.transferType = " SHAREHOLDER_ADVANCE ";
        transfer.amount = new BigDecimal("125.5000");
        transfer.currency = " cny ";
        transfer.transferDate = "2026-09-04";
        transfer.note = " working capital ";
        transfer.status = " RECORDED ";
        transfer.operatorUserId = 9;
        return transfer;
    }
}
