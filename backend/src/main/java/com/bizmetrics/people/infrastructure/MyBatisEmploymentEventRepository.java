package com.bizmetrics.people.infrastructure;

import com.bizmetrics.people.application.EmploymentEventRepository;
import com.bizmetrics.people.domain.EmploymentEvent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisEmploymentEventRepository implements EmploymentEventRepository {
    private final EmploymentEventMapper mapper;

    public MyBatisEmploymentEventRepository(EmploymentEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<EmploymentEvent> findByCompany(long companyId) {
        return mapper.findByCompany(companyId).stream().map(this::map).toList();
    }

    @Override
    public EmploymentEvent append(EmploymentEvent event) {
        event.type = event.type.trim().toLowerCase(Locale.ROOT);
        event.note = event.note.trim();
        event.createdAt = OffsetDateTime.now().toString();
        Long id = mapper.append(event.companyId, event.employeeId, event.type, LocalDate.parse(event.effectiveDate),
            event.note, event.operatorUserId, OffsetDateTime.parse(event.createdAt));
        if (id == null) throw new IllegalStateException("Database did not return a generated employment event id");
        event.id = id;
        return event;
    }

    @Override
    public void deleteByEmployeeForDemoReset(long employeeId) {
        mapper.deleteByEmployeeForDemoReset(employeeId);
    }

    private EmploymentEvent map(EmploymentEventMapper.Row result) {
        EmploymentEvent event = new EmploymentEvent();
        event.id = result.id();
        event.companyId = result.companyId();
        event.employeeId = result.employeeId();
        event.type = result.type();
        event.effectiveDate = result.effectiveDate().toString();
        event.note = result.note();
        event.operatorUserId = result.operatorUserId();
        event.createdAt = result.createdAt().toString();
        return event;
    }
}
