package com.bizmetrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizmetrics.platform.tenant.Company;
import com.bizmetrics.domain.Models.PayrollRun;
import com.bizmetrics.domain.Models.PayrollRunItem;
import com.bizmetrics.people.application.EmployeeRepository;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.audit.application.AuditTrailService;
import com.bizmetrics.service.support.AccessControlService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayrollServiceReadTest {

    @Test
    void listRunsLoadsAllItemsInOneBatchQuery() {
        PayrollRun first = run(11);
        PayrollRun second = run(12);
        PayrollRunItem firstItem = item(101, first.id);
        PayrollRunItem secondItem = item(102, second.id);
        PayrollMapper mapper = mock(PayrollMapper.class);
        when(mapper.listRuns(9)).thenReturn(List.of(first, second));
        when(mapper.itemsForRuns(List.of(11L, 12L))).thenReturn(List.of(firstItem, secondItem));
        AccessControlService accessControl = mock(AccessControlService.class);
        User user = new User();
        user.id = 7;
        Company company = new Company();
        company.id = 9;
        when(accessControl.requireUser("Bearer token")).thenReturn(user);
        when(accessControl.resolveCompany(user, 9L)).thenReturn(company);
        PayrollService service = new PayrollService(
            mapper,
            mock(AuditTrailService.class),
            mock(EmployeeRepository.class),
            accessControl,
            mock(OutboxEventService.class)
        );

        List<PayrollRun> result = service.listRuns("Bearer token", 9L, null);

        verify(mapper).itemsForRuns(List.of(11L, 12L));
        assertEquals(List.of(firstItem), result.get(0).items);
        assertEquals(List.of(secondItem), result.get(1).items);
    }

    private PayrollRun run(long id) {
        PayrollRun run = new PayrollRun();
        run.id = id;
        return run;
    }

    private PayrollRunItem item(long id, long runId) {
        PayrollRunItem item = new PayrollRunItem();
        item.id = id;
        item.runId = runId;
        return item;
    }

}
