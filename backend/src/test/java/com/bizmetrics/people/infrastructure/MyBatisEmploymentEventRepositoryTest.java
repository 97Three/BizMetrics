package com.bizmetrics.people.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizmetrics.people.domain.EmploymentEvent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class MyBatisEmploymentEventRepositoryTest {
    @Test
    void appendNormalizesAndBindsTypedLifecycleValues() {
        EmploymentEventMapper mapper = mock(EmploymentEventMapper.class);
        when(mapper.append(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyString(),
            ArgumentMatchers.any(LocalDate.class), ArgumentMatchers.anyString(), ArgumentMatchers.anyLong(),
            ArgumentMatchers.any(OffsetDateTime.class))).thenReturn(41L);
        MyBatisEmploymentEventRepository repository = new MyBatisEmploymentEventRepository(mapper);
        EmploymentEvent event = event();

        EmploymentEvent saved = repository.append(event);

        assertEquals(41, saved.id);
        assertEquals("status_change", saved.type);
        assertEquals("员工转正", saved.note);
        assertNotNull(saved.createdAt);
        ArgumentCaptor<LocalDate> date = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<OffsetDateTime> createdAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper).append(ArgumentMatchers.eq(7L), ArgumentMatchers.eq(13L),
            ArgumentMatchers.eq("status_change"), date.capture(), ArgumentMatchers.eq("员工转正"),
            ArgumentMatchers.eq(5L), createdAt.capture());
        assertInstanceOf(LocalDate.class, date.getValue());
        assertInstanceOf(OffsetDateTime.class, createdAt.getValue());
    }

    @Test
    void companyHistoryUsesOneCompanyScopedQuery() {
        EmploymentEventMapper mapper = mock(EmploymentEventMapper.class);
        when(mapper.findByCompany(7L)).thenReturn(List.of(new EmploymentEventMapper.Row(
            41L, 7L, 13L, "status_change", LocalDate.parse("2026-09-04"), "员工转正", 5L,
            OffsetDateTime.parse("2026-09-04T12:00:00Z"))));
        MyBatisEmploymentEventRepository repository = new MyBatisEmploymentEventRepository(mapper);

        List<EmploymentEvent> events = repository.findByCompany(7);

        assertEquals(1, events.size());
        assertEquals(7L, events.getFirst().companyId);
        verify(mapper).findByCompany(7L);
    }

    private EmploymentEvent event() {
        EmploymentEvent event = new EmploymentEvent();
        event.companyId = 7;
        event.employeeId = 13;
        event.type = " STATUS_CHANGE ";
        event.effectiveDate = "2026-09-04";
        event.note = " 员工转正 ";
        event.operatorUserId = 5;
        return event;
    }

}
