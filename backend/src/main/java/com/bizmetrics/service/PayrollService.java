package com.bizmetrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bizmetrics.platform.tenant.Company;
import com.bizmetrics.domain.Models.PayrollRun;
import com.bizmetrics.domain.Models.PayrollRunItem;
import com.bizmetrics.people.application.EmployeeRepository;
import com.bizmetrics.people.domain.Employee;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.audit.application.AuditTrailService;
import com.bizmetrics.service.support.AccessControlService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.bizmetrics.common.PayloadReader.optionalLong;
import static com.bizmetrics.common.PayloadReader.textOr;

@Service
public class PayrollService {
    private final PayrollMapper mapper;
    private final AuditTrailService auditTrail;
    private final EmployeeRepository employees;
    private final AccessControlService accessControl;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PayrollService(
        PayrollMapper mapper,
        AuditTrailService auditTrail,
        EmployeeRepository employees,
        AccessControlService accessControl,
        OutboxEventService outboxEventService
    ) {
        this.mapper = mapper;
        this.auditTrail = auditTrail;
        this.employees = employees;
        this.accessControl = accessControl;
        this.outboxEventService = outboxEventService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<PayrollRun> listRuns(String authorization, Long companyId, String period) {
        User user = accessControl.requireUser(authorization);
        Company company = accessControl.resolveCompany(user, companyId);
        accessControl.requirePayrollManager(user, company.id);
        String normalizedPeriod = period == null || period.isBlank() ? null : normalizedPeriod(period);
        List<PayrollRun> runs = normalizedPeriod == null
            ? mapper.listRuns(company.id) : mapper.listRunsByPeriod(company.id, normalizedPeriod);
        return attachItems(runs);
    }

    @Transactional
    public PayrollRun createRun(String authorization, Map<String, Object> body) {
        User user = accessControl.requireUser(authorization);
        Company company = accessControl.resolveCompany(user, optionalLong(body.get("companyId")).orElse(null));
        accessControl.requirePayrollManager(user, company.id);
        String period = normalizedPeriod(body.get("period"));
        if (payrollRunExists(company.id, period)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll run already exists for this period");
        }
        List<PayrollRunItem> items = employees.findByCompany(company.id, false).stream()
            .filter(employee -> List.of("active", "probation").contains(employee.status))
            .map(employee -> snapshotItem(company.id, period, employee))
            .toList();
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active employees for payroll run");
        }
        PayrollRun run = aggregateRun(company.id, period, user.id, items);
        try {
            mapper.insertRun(run);
        } catch (DuplicateKeyException ignored) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll run already exists for this period");
        }
        for (PayrollRunItem item : items) {
            item.runId = run.id;
            mapper.insertItem(item);
        }
        run.items = items;
        auditTrail.record(company.id, "payroll_run", run.id, "create", "生成薪酬月结批次: " + run.period, user.id, user.nickname);
        outboxEventService.publish("payroll.run.created", company.id, "payroll_run", run.id, user.id, Map.of(
            "period", run.period,
            "employeeCount", run.employeeCount,
            "salaryTotal", moneyText(run.salaryTotal),
            "netPayTotal", moneyText(run.netPayTotal),
            "companyCostTotal", moneyText(run.companyCostTotal)
        ));
        return run;
    }

    @Transactional
    public PayrollRun closeRun(String authorization, long id) {
        User user = accessControl.requireUser(authorization);
        PayrollRun run = findRunForUpdate(id);
        Company company = accessControl.resolveCompany(user, run.companyId);
        accessControl.requirePayrollManager(user, company.id);
        if ("closed".equals(run.status)) {
            return attachItems(run);
        }
        String now = OffsetDateTime.now().toString();
        mapper.closeRun(id, user.id, now);
        run.status = "closed";
        run.closedByUserId = user.id;
        run.closedAt = now;
        run.updatedAt = now;
        PayrollRun closed = attachItems(run);
        auditTrail.record(company.id, "payroll_run", closed.id, "close", "锁定薪酬月结批次: " + closed.period, user.id, user.nickname);
        outboxEventService.publish("payroll.run.closed", company.id, "payroll_run", closed.id, user.id, Map.of(
            "period", closed.period,
            "employeeCount", closed.employeeCount,
            "netPayTotal", moneyText(closed.netPayTotal),
            "companyCostTotal", moneyText(closed.companyCostTotal),
            "closedAt", closed.closedAt
        ));
        return closed;
    }

    private PayrollRun findRunForUpdate(long id) {
        PayrollRun run = mapper.findForUpdate(id);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll run not found");
        }
        return run;
    }

    private boolean payrollRunExists(long companyId, String period) {
        return mapper.countPeriod(companyId, period) > 0;
    }

    private PayrollRun aggregateRun(long companyId, String period, long createdByUserId, List<PayrollRunItem> items) {
        PayrollRun run = new PayrollRun();
        run.companyId = companyId;
        run.period = period;
        run.name = period + " 薪酬月结";
        run.status = "draft";
        run.employeeCount = items.size();
        run.salaryTotal = sum(items, item -> item.payableSalary);
        run.socialPersonalTotal = sum(items, item -> item.socialPersonalAmount);
        run.socialCompanyTotal = sum(items, item -> item.socialCompanyAmount);
        run.housingPersonalTotal = sum(items, item -> item.housingPersonalAmount);
        run.housingCompanyTotal = sum(items, item -> item.housingCompanyAmount);
        run.taxTotal = sum(items, item -> item.taxAmount);
        run.personalDeductionTotal = sum(items, item -> item.personalDeduction);
        run.netPayTotal = sum(items, item -> item.netPay);
        run.companyCostTotal = sum(items, item -> item.companyCost);
        run.createdByUserId = createdByUserId;
        run.createdAt = OffsetDateTime.now().toString();
        run.updatedAt = run.createdAt;
        return run;
    }

    private PayrollRunItem snapshotItem(long companyId, String period, Employee employee) {
        PayrollRunItem item = new PayrollRunItem();
        item.companyId = companyId;
        item.employeeId = employee.id;
        item.employeeName = employee.name;
        item.departmentName = employee.departmentName;
        item.period = period;
        item.salary = money(employee.salary);
        item.payableSalary = money(employee.salary).add(money(employee.overtimePay));
        item.socialPersonalAmount = money(employee.socialInsurancePersonalAmount);
        item.socialCompanyAmount = money(employee.socialInsuranceCompanyAmount);
        item.housingPersonalAmount = money(employee.housingFundPersonalAmount);
        item.housingCompanyAmount = money(employee.housingFundCompanyAmount);
        item.taxAmount = money(employee.taxEstimate);
        item.personalDeduction = money(employee.personalDeduction);
        item.netPay = money(employee.netPayEstimate);
        item.companyCost = money(employee.monthlyCost);
        item.createdAt = OffsetDateTime.now().toString();
        item.snapshotJson = snapshotJson(employee, item);
        return item;
    }

    private String snapshotJson(Employee employee, PayrollRunItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeNo", employee.employeeNo);
        snapshot.put("position", employee.position);
        snapshot.put("departmentName", item.departmentName);
        snapshot.put("socialInsuranceRegion", employee.socialInsuranceRegion);
        snapshot.put("hukouType", employee.hukouType);
        snapshot.put("medicalTier", employee.medicalTier);
        snapshot.put("pensionBase", moneyText(employee.pensionBase));
        snapshot.put("medicalBase", moneyText(employee.medicalBase));
        snapshot.put("unemploymentBase", moneyText(employee.unemploymentBase));
        snapshot.put("workInjuryBase", moneyText(employee.workInjuryBase));
        snapshot.put("maternityBase", moneyText(employee.maternityBase));
        snapshot.put("housingFundBase", moneyText(employee.housingFundBase));
        snapshot.put("housingFundPersonalRate", moneyText(employee.housingFundPersonalRate));
        snapshot.put("housingFundCompanyRate", moneyText(employee.housingFundCompanyRate));
        snapshot.put("salary", moneyText(item.salary));
        snapshot.put("overtimeBase", moneyText(employee.overtimeBase));
        snapshot.put("weekdayOvertimeHours", moneyText(employee.weekdayOvertimeHours));
        snapshot.put("restDayOvertimeHours", moneyText(employee.restDayOvertimeHours));
        snapshot.put("holidayOvertimeHours", moneyText(employee.holidayOvertimeHours));
        snapshot.put("overtimePay", moneyText(employee.overtimePay));
        snapshot.put("overtimePolicyNote", employee.overtimePolicyNote);
        snapshot.put("payableSalary", moneyText(item.payableSalary));
        snapshot.put("netPay", moneyText(item.netPay));
        snapshot.put("companyCost", moneyText(item.companyCost));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create payroll snapshot", e);
        }
    }

    private PayrollRun attachItems(PayrollRun run) {
        run.items = mapper.itemsForRun(run.id);
        return run;
    }

    private List<PayrollRun> attachItems(List<PayrollRun> runs) {
        if (runs.isEmpty()) {
            return runs;
        }
        List<PayrollRunItem> items = mapper.itemsForRuns(runs.stream().map(run -> run.id).toList());
        Map<Long, List<PayrollRunItem>> itemsByRun = new HashMap<>();
        for (PayrollRunItem item : items) {
            itemsByRun.computeIfAbsent(item.runId, ignored -> new ArrayList<>()).add(item);
        }
        for (PayrollRun run : runs) {
            run.items = List.copyOf(itemsByRun.getOrDefault(run.id, List.of()));
        }
        return runs;
    }

    private String normalizedPeriod(Object value) {
        String raw = textOr(value, YearMonth.now().toString());
        try {
            return YearMonth.parse(raw).toString();
        } catch (RuntimeException ignored) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payroll period");
        }
    }

    private static BigDecimal sum(List<PayrollRunItem> items, MoneyAccessor accessor) {
        return items.stream().map(accessor::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static String moneyText(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private interface MoneyAccessor {
        BigDecimal value(PayrollRunItem item);
    }
}
