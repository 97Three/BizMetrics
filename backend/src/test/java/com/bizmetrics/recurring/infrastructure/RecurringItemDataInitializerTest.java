package com.bizmetrics.recurring.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizmetrics.platform.tenant.Company;
import com.bizmetrics.recurring.application.RecurringItemRepository;
import com.bizmetrics.recurring.domain.RecurringItem;
import com.bizmetrics.platform.tenant.CompanyRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecurringItemDataInitializerTest {
    @Test
    void demoModeCreatesCompanyScopedRuleOnlyOnce() {
        RecurringItemRepository recurringItems = mock(RecurringItemRepository.class);
        CompanyRepository companies = mock(CompanyRepository.class);
        Company company = new Company();
        company.id = 9;
        company.ownerId = 3;
        company.entityType = "company";
        RecurringItem existing = new RecurringItem();
        when(companies.findAll()).thenReturn(List.of(company));
        when(recurringItems.findByOwnerAndCompany(3, 9))
            .thenReturn(List.of())
            .thenReturn(List.of(existing));
        RecurringItemDataInitializer initializer = new RecurringItemDataInitializer(
            recurringItems,
            companies
        );

        initializer.initialize();
        initializer.initialize();

        ArgumentCaptor<RecurringItem> inserted = ArgumentCaptor.forClass(RecurringItem.class);
        verify(recurringItems, times(1)).insert(inserted.capture());
        RecurringItem item = inserted.getValue();
        assertNotNull(item.id);
        assertEquals(3, item.userId);
        assertEquals(9, item.companyId);
        assertEquals("办公室租金", item.name);
        assertEquals("3200.00", item.amount.toPlainString());
        assertEquals("monthly", item.frequency);
        assertEquals(5, item.dayOfMonth);
        assertEquals(LocalDate.parse(item.startDate).plusMonths(1).withDayOfMonth(5), LocalDate.parse(item.nextExecution));
    }

    @Test
    void demoModeLeavesExistingRulesUntouched() {
        RecurringItemRepository recurringItems = mock(RecurringItemRepository.class);
        CompanyRepository companies = mock(CompanyRepository.class);
        Company company = new Company();
        company.id = 9;
        company.ownerId = 3;
        company.entityType = "company";
        when(companies.findAll()).thenReturn(List.of(company));
        when(recurringItems.findByOwnerAndCompany(3, 9)).thenReturn(List.of(new RecurringItem()));
        RecurringItemDataInitializer initializer = new RecurringItemDataInitializer(
            recurringItems,
            companies
        );

        initializer.initialize();

        verify(recurringItems, never()).insert(any());
    }
}
