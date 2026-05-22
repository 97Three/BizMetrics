package com.bizmetrics.recurring.infrastructure;

import com.bizmetrics.platform.tenant.Company;
import com.bizmetrics.recurring.application.RecurringItemRepository;
import com.bizmetrics.recurring.domain.RecurringItem;
import com.bizmetrics.recurring.domain.RecurringSchedule;
import com.bizmetrics.platform.tenant.CompanyRepository;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/** Owns optional recurring-rule demo data after enterprise subjects exist. */
@Component
@ConditionalOnProperty(name = "bizmetrics.bootstrap.mode", havingValue = "demo", matchIfMissing = true)
@DependsOn("enterpriseDataInitializer")
public class RecurringItemDataInitializer {
    private final RecurringItemRepository recurringItems;
    private final CompanyRepository companies;

    public RecurringItemDataInitializer(RecurringItemRepository recurringItems, CompanyRepository companies) {
        this.recurringItems = recurringItems;
        this.companies = companies;
    }

    @PostConstruct
    void initialize() {
        Optional<Company> company = companies.findAll().stream()
            .filter(candidate -> "company".equals(candidate.entityType))
            .min(Comparator.comparing(candidate -> candidate.id));
        if (company.isEmpty()) {
            return;
        }
        Company subject = company.get();
        if (!recurringItems.findByOwnerAndCompany(subject.ownerId, subject.id).isEmpty()) {
            return;
        }

        RecurringItem officeRent = new RecurringItem();
        officeRent.id = UUID.randomUUID().toString();
        officeRent.userId = subject.ownerId;
        officeRent.companyId = subject.id;
        officeRent.name = "办公室租金";
        officeRent.type = 2;
        officeRent.amount = new BigDecimal("3200.00");
        officeRent.frequency = "monthly";
        officeRent.interval = 1;
        officeRent.dayOfMonth = 5;
        officeRent.startDate = LocalDate.now().withDayOfMonth(1).toString();
        officeRent.nextExecution = RecurringSchedule.next(officeRent).toString();
        officeRent.status = 1;
        officeRent.executionCount = 0;
        officeRent.note = "每月办公室租金";
        recurringItems.insert(officeRent);
    }
}
