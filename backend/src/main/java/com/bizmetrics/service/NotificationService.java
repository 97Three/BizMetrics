package com.bizmetrics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bizmetrics.common.PageRequest;
import com.bizmetrics.common.PagedResponse;
import com.bizmetrics.common.Roles;
import com.bizmetrics.platform.tenant.Company;
import com.bizmetrics.evidence.domain.ReceiptVoucher;
import com.bizmetrics.evidence.application.ReceiptVoucherRepository;
import com.bizmetrics.notification.domain.OutboxEvent;
import com.bizmetrics.people.application.EmployeeRepository;
import com.bizmetrics.people.domain.Employee;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.identity.account.application.UserDirectory;
import com.bizmetrics.platform.product.ProductModuleCatalog;
import com.bizmetrics.platform.scheduling.application.DistributedJobCoordinator;
import com.bizmetrics.platform.tenant.CompanyMembershipRepository;
import com.bizmetrics.platform.tenant.CompanyRepository;
import com.bizmetrics.service.support.AccessControlService;
import com.bizmetrics.service.support.WebhookUrlValidator;
import com.bizmetrics.tax.application.TaxItemRepository;
import com.bizmetrics.tax.domain.TaxItem;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private static final String REMINDER_JOB_NAME = "notification-reminders";

    private final NotificationMapper mapper;
    private final UserDirectory userDirectory;
    private final CompanyRepository companies;
    private final EmployeeRepository employees;
    private final TaxItemRepository taxItems;
    private final ReceiptVoucherRepository receiptVouchers;
    private final AccessControlService accessControl;
    private final CompanyMembershipRepository memberships;
    private final ProductModuleCatalog modules;
    private final NotificationDeliveryService deliveryService;
    private final WebhookUrlValidator webhookUrlValidator;
    private final DistributedJobCoordinator scheduledJobs;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;
    private final boolean reminderEnabled;
    private final int taxLookaheadDays;
    private final int peopleLookaheadDays;
    private final int receiptLookaheadDays;
    private final long reminderCadenceMillis;
    private final long reminderLeaseMillis;

    public NotificationService(
        NotificationMapper mapper,
        UserDirectory userDirectory,
        CompanyRepository companies,
        EmployeeRepository employees,
        TaxItemRepository taxItems,
        ReceiptVoucherRepository receiptVouchers,
        AccessControlService accessControl,
        CompanyMembershipRepository memberships,
        ProductModuleCatalog modules,
        NotificationDeliveryService deliveryService,
        WebhookUrlValidator webhookUrlValidator,
        DistributedJobCoordinator scheduledJobs,
        @Value("${bizmetrics.notifications.enabled:true}") boolean enabled,
        @Value("${bizmetrics.notifications.reminder.enabled:true}") boolean reminderEnabled,
        @Value("${bizmetrics.notifications.reminder.tax-lookahead-days:7}") int taxLookaheadDays,
        @Value("${bizmetrics.notifications.reminder.people-lookahead-days:14}") int peopleLookaheadDays,
        @Value("${bizmetrics.notifications.reminder.receipt-lookahead-days:7}") int receiptLookaheadDays,
        @Value("${bizmetrics.notifications.reminder.fixed-delay-ms:60000}") long reminderCadenceMillis,
        @Value("${bizmetrics.notifications.reminder.lease-ms:600000}") long reminderLeaseMillis
    ) {
        this.mapper = mapper;
        this.userDirectory = userDirectory;
        this.companies = companies;
        this.employees = employees;
        this.taxItems = taxItems;
        this.receiptVouchers = receiptVouchers;
        this.accessControl = accessControl;
        this.memberships = memberships;
        this.modules = modules;
        this.deliveryService = deliveryService;
        this.webhookUrlValidator = webhookUrlValidator;
        this.scheduledJobs = scheduledJobs;
        this.enabled = enabled;
        this.reminderEnabled = reminderEnabled;
        this.taxLookaheadDays = Math.max(1, taxLookaheadDays);
        this.peopleLookaheadDays = Math.max(1, peopleLookaheadDays);
        this.receiptLookaheadDays = Math.max(1, receiptLookaheadDays);
        this.reminderCadenceMillis = Math.max(1000, reminderCadenceMillis);
        this.reminderLeaseMillis = Math.max(1000, reminderLeaseMillis);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PagedResponse<NotificationView> list(String authorization, Map<String, String> params) {
        User user = accessControl.requireUser(authorization);
        boolean unreadOnly = bool(params.get("unreadOnly"));
        PageRequest pageRequest = PageRequest.from(params);
        long totalElements = mapper.count(user.id, unreadOnly);
        List<NotificationView> notifications = mapper.list(user.id, unreadOnly, pageRequest.size(),
            (long) pageRequest.page() * pageRequest.size()).stream().map(this::notificationView).toList();
        int totalPages = (int) Math.ceil((double) totalElements / pageRequest.size());
        return new PagedResponse<>(notifications, totalElements, totalPages, pageRequest.size(), pageRequest.page());
    }

    public Map<String, Object> summary(String authorization) {
        User user = accessControl.requireUser(authorization);
        NotificationMapper.SummaryRow row = mapper.summary(user.id);
        return Map.of("unreadCount", row.unreadCount, "pendingDeliveryCount", row.pendingDeliveryCount,
            "failedDeliveryCount", row.failedDeliveryCount);
    }

    public NotificationPreference preference(String authorization) {
        User user = accessControl.requireUser(authorization);
        return preferenceFor(user.id);
    }

    public NotificationPreference updatePreference(String authorization, Map<String, Object> body) {
        User user = accessControl.requireUser(authorization);
        NotificationPreference current = preferenceFor(user.id);
        boolean enabled = boolValue(body.get("enabled"), current.enabled());
        boolean webhookEnabled = boolValue(body.get("webhookEnabled"), current.webhookEnabled());
        String webhookProvider = normalizeProvider(text(body.get("webhookProvider"), current.webhookProvider()));
        String webhookUrl = body.containsKey("webhookUrl")
            ? blankToNull(text(body.get("webhookUrl"), ""))
            : current.webhookUrl();
        String minSeverity = normalizeSeverity(text(body.get("minSeverity"), current.minSeverity()));
        List<String> mutedTypes = body.containsKey("mutedTypes") ? parseTypes(body.get("mutedTypes")) : current.mutedTypes();
        if (webhookUrl != null && (webhookEnabled || body.containsKey("webhookUrl"))) {
            webhookUrl = safeWebhookUrl(webhookUrl);
        }
        if (webhookEnabled && webhookUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook URL is required when webhook delivery is enabled");
        }
        String now = OffsetDateTime.now().toString();
        mapper.upsertPreference(user.id, enabled, webhookEnabled, webhookProvider, webhookUrl,
            minSeverity, String.join(",", mutedTypes), now);
        return preferenceFor(user.id);
    }

    public Map<String, Object> testWebhook(String authorization) {
        User user = accessControl.requireUser(authorization);
        NotificationPreference preference = preferenceFor(user.id);
        if (!preference.webhookEnabled() || preference.webhookUrl() == null || preference.webhookUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook is not configured");
        }
        safeWebhookUrl(preference.webhookUrl());
        deliveryService.sendTestWebhook(preference);
        return Map.of("success", true);
    }

    public NotificationView markRead(String authorization, long id) {
        User user = accessControl.requireUser(authorization);
        NotificationView existing = findForUser(user.id, id);
        if (existing.readAt() == null || existing.readAt().isBlank()) {
            String now = OffsetDateTime.now().toString();
            mapper.markRead(id, user.id, now);
        }
        return findForUser(user.id, id);
    }

    public Map<String, Object> markAllRead(String authorization) {
        User user = accessControl.requireUser(authorization);
        String now = OffsetDateTime.now().toString();
        int updated = mapper.markAllRead(user.id, now);
        return Map.of("updated", updated);
    }

    public void handleOutboxEvent(OutboxEvent event) {
        if (!enabled) {
            return;
        }
        Map<String, Object> payload = payload(event);
        String summary = text(payload.get("summary"), event.eventType);
        String dedupeKey = "outbox:" + event.eventId;
        if (event.eventType.startsWith("payroll.run.")) {
            String period = text(payload.get("period"), "");
            boolean closed = event.eventType.endsWith(".closed");
            notifyUsers(payrollRecipients(event.companyId, event.actorUserId), draft(
                event.companyId,
                "payroll",
                closed ? "success" : "info",
                closed ? "薪酬月结已锁定" : "薪酬月结已生成",
                period.isBlank() ? summary : "期间 " + period + "：" + summary,
                "/admin/compensation",
                event.aggregateType,
                event.aggregateId,
                dedupeKey
            ));
            return;
        }
        if (event.eventType.startsWith("receipt_voucher.")) {
            notifyUsers(financeRecipients(event.companyId, event.actorUserId), draft(
                event.companyId,
                "receipt",
                receiptSeverity(payload),
                "票据凭证有新进展",
                summary,
                "/receipts",
                event.aggregateType,
                event.aggregateId,
                dedupeKey
            ));
            return;
        }
        if (event.eventType.startsWith("enterprise.tax_item.")) {
            notifyUsers(financeRecipients(event.companyId, event.actorUserId), draft(
                event.companyId,
                "tax",
                event.eventType.endsWith(".delete") ? "warning" : "info",
                "税务事项已更新",
                summary,
                "/tax",
                event.aggregateType,
                event.aggregateId,
                dedupeKey
            ));
            return;
        }
        if (event.eventType.startsWith("enterprise.employee.") || event.eventType.startsWith("enterprise.department.")) {
            notifyUsers(peopleRecipients(event.companyId, event.actorUserId), draft(
                event.companyId,
                "people",
                "info",
                event.eventType.startsWith("enterprise.employee.") ? "员工档案已更新" : "组织架构已更新",
                summary,
                "/hr/organization",
                event.aggregateType,
                event.aggregateId,
                dedupeKey
            ));
            return;
        }
        if (event.eventType.startsWith("enterprise.entity_transfer.") || event.eventType.startsWith("accounting.account.")) {
            notifyUsers(financeRecipients(event.companyId, event.actorUserId), draft(
                event.companyId,
                "finance",
                "info",
                event.eventType.startsWith("enterprise.entity_transfer.") ? "主体往来已记录" : "资金账户已更新",
                summary,
                event.eventType.startsWith("enterprise.entity_transfer.") ? "/dashboard" : "/accounts",
                event.aggregateType,
                event.aggregateId,
                dedupeKey
            ));
            return;
        }
        if ("auth.registration_invite.created".equals(event.eventType)) {
            notifyUsers(actorOnly(event.actorUserId), draft(
                0,
                "system",
                "success",
                "注册邀请已创建",
                "邀请已生成：" + text(payload.get("email"), "新用户"),
                "/admin/users",
                event.aggregateType,
                event.aggregateId,
                dedupeKey
            ));
            return;
        }
        if ("auth.user.registered".equals(event.eventType)) {
            notifyUsers(adminRecipients(), draft(
                0,
                "system",
                "info",
                "新用户已注册",
                text(payload.get("email"), "新用户") + " 已完成注册",
                "/admin/users",
                event.aggregateType,
                event.aggregateId,
                dedupeKey
            ));
            notifyUsers(actorOnly(event.actorUserId), draft(
                0,
                "system",
                "success",
                "欢迎使用 BizMetrics",
                "你的账号已创建，可以开始维护经营数据",
                "/dashboard",
                event.aggregateType,
                event.aggregateId,
                dedupeKey + ":welcome"
            ));
            return;
        }
        if (event.actorUserId > 0) {
            notifyUsers(actorOnly(event.actorUserId), draft(
                event.companyId,
                "system",
                "info",
                "操作已完成",
                summary,
                "/dashboard",
                event.aggregateType,
                event.aggregateId,
                dedupeKey
            ));
        }
    }

    @Scheduled(fixedDelayString = "${bizmetrics.notifications.reminder.fixed-delay-ms:60000}")
    public void generateDueReminders() {
        if (!enabled || !reminderEnabled) {
            return;
        }
        scheduledJobs.runIfDue(
            REMINDER_JOB_NAME,
            reminderCadenceMillis,
            reminderLeaseMillis,
            this::generateDueRemindersWithLease
        );
    }

    private void generateDueRemindersWithLease() {
        LocalDate today = LocalDate.now();
        if (modules.isEnabled("tax")) {
            generateTaxReminders(today);
        }
        if (modules.isEnabled("people-core")) {
            generatePeopleReminders(today);
        }
        generateReceiptReminders(today);
    }

    private void generateTaxReminders(LocalDate today) {
        LocalDate latest = today.plusDays(taxLookaheadDays);
        for (TaxItem item : taxItems.findAll()) {
            if (taxSettled(item)) {
                continue;
            }
            LocalDate dueDate = parseDate(item.dueDate);
            if (dueDate == null || dueDate.isAfter(latest)) {
                continue;
            }
            boolean overdue = dueDate.isBefore(today);
            notifyUsers(financeRecipients(item.companyId, 0), draft(
                item.companyId,
                "tax",
                overdue ? "critical" : "warning",
                overdue ? "税务事项已逾期" : "税务事项即将到期",
                item.name + " " + item.period + " 截止日 " + item.dueDate,
                "/tax",
                "tax_item",
                item.id,
                "reminder:tax:" + item.id + ":" + today
            ));
        }
    }

    private void generatePeopleReminders(LocalDate today) {
        LocalDate latest = today.plusDays(peopleLookaheadDays);
        for (Employee employee : employees.findAll()) {
            if ("departed".equals(employee.status)) {
                continue;
            }
            remindPeopleDate(today, latest, employee, "contract", employee.contractEndDate, "劳动合同到期提醒");
            remindPeopleDate(today, latest, employee, "probation", employee.probationEndDate, "试用期到期提醒");
        }
    }

    private void remindPeopleDate(LocalDate today, LocalDate latest, Employee employee, String kind, String dateText, String title) {
        LocalDate date = parseDate(dateText);
        if (date == null || date.isAfter(latest)) {
            return;
        }
        boolean overdue = date.isBefore(today);
        notifyUsers(peopleRecipients(employee.companyId, 0), draft(
            employee.companyId,
            "people",
            overdue ? "critical" : "warning",
            overdue ? title.replace("提醒", "已逾期") : title,
            employee.name + "：" + dateText,
            "/hr/organization",
            "employee",
            employee.id,
            "reminder:people:" + kind + ":" + employee.id + ":" + today
        ));
    }

    private void generateReceiptReminders(LocalDate today) {
        LocalDate latest = today.plusDays(receiptLookaheadDays);
        for (ReceiptVoucher voucher : receiptVouchers.findAll()) {
            if (receiptClosed(voucher)) {
                continue;
            }
            LocalDate dueDate = parseDate(voucher.dueDate);
            if (dueDate == null || dueDate.isAfter(latest)) {
                continue;
            }
            boolean overdue = dueDate.isBefore(today);
            notifyUsers(financeRecipients(voucher.companyId, voucher.operatorUserId), draft(
                voucher.companyId,
                "receipt",
                overdue ? "critical" : "warning",
                overdue ? "票据凭证已逾期" : "票据凭证即将到期",
                voucher.title + " 截止日 " + voucher.dueDate,
                "/receipts",
                "receipt_voucher",
                voucher.id,
                "reminder:receipt:" + voucher.id + ":" + today
            ));
        }
    }

    private NotificationView findForUser(long userId, long id) {
        NotificationMapper.NotificationRow row = mapper.findForUser(id, userId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return notificationView(row);
    }

    private void notifyUsers(Collection<Long> userIds, NotificationDraft draft) {
        if (!enabled || userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds.stream().filter(Objects::nonNull).distinct().toList()) {
            notifyUser(userId, draft);
        }
    }

    private void notifyUser(long userId, NotificationDraft draft) {
        if (userDirectory.findById(userId).isEmpty()) {
            return;
        }
        NotificationPreference preference = preferenceFor(userId);
        if (!preference.enabled() || preference.mutedTypes().contains(draft.type()) || !severityEnabled(draft.severity(), preference.minSeverity())) {
            return;
        }
        String now = OffsetDateTime.now().toString();
        Long insertedId = mapper.insertNotification(userId, draft.companyId(), normalize(draft.type(), "system"),
            normalize(draft.severity(), "info"), normalize(draft.title(), "新通知"),
            normalize(draft.content(), ""), blankToNull(draft.targetUrl()),
            blankToNull(draft.sourceType()), draft.sourceId(), blankToNull(draft.dedupeKey()), now);
        if (insertedId != null && preference.webhookEnabled() && preference.webhookUrl() != null && !preference.webhookUrl().isBlank()) {
            deliveryService.enqueueWebhook(insertedId, userId, preference.webhookProvider());
        }
    }

    private Set<Long> payrollRecipients(long companyId, long actorUserId) {
        return withActor(companyRoleRecipients(companyId, "founder", "finance_admin", "hr_admin"), actorUserId);
    }

    private Set<Long> financeRecipients(long companyId, long actorUserId) {
        return withActor(companyRoleRecipients(companyId, "founder", "finance_admin"), actorUserId);
    }

    private Set<Long> peopleRecipients(long companyId, long actorUserId) {
        return withActor(companyRoleRecipients(companyId, "founder", "hr_admin"), actorUserId);
    }

    private Set<Long> companyRoleRecipients(long companyId, String... roles) {
        Set<Long> recipients = adminRecipients();
        if (companyId <= 0) {
            return recipients;
        }
        Set<String> roleSet = Set.of(roles);
        Company company = companies.findById(companyId).orElse(null);
        if (company != null && roleSet.contains("founder")) {
            recipients.add(company.ownerId);
        }
        memberships.findActiveByCompany(companyId).stream()
            .filter(membership -> roleSet.contains(membership.role()))
            .map(membership -> membership.userId())
            .forEach(recipients::add);
        return recipients;
    }

    private Set<Long> adminRecipients() {
        Set<Long> recipients = new LinkedHashSet<>();
        userDirectory.findAll().stream()
            .filter(user -> user.role() == Roles.ADMIN)
            .map(UserDirectory.Entry::id)
            .forEach(recipients::add);
        return recipients;
    }

    private Set<Long> actorOnly(long actorUserId) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (actorUserId > 0 && userDirectory.findById(actorUserId).isPresent()) {
            recipients.add(actorUserId);
        }
        return recipients;
    }

    private Set<Long> withActor(Set<Long> recipients, long actorUserId) {
        Set<Long> all = new LinkedHashSet<>(recipients);
        if (actorUserId > 0 && userDirectory.findById(actorUserId).isPresent()) {
            all.add(actorUserId);
        }
        return all;
    }

    private NotificationDraft draft(
        long companyId,
        String type,
        String severity,
        String title,
        String content,
        String targetUrl,
        String sourceType,
        Long sourceId,
        String dedupeKey
    ) {
        return new NotificationDraft(companyId, type, severity, title, content, targetUrl, sourceType, sourceId, dedupeKey);
    }

    private NotificationDraft draft(
        long companyId,
        String type,
        String severity,
        String title,
        String content,
        String targetUrl,
        String sourceType,
        long sourceId,
        String dedupeKey
    ) {
        return draft(companyId, type, severity, title, content, targetUrl, sourceType, Long.valueOf(sourceId), dedupeKey);
    }

    private NotificationView notificationView(NotificationMapper.NotificationRow row) {
        return new NotificationView(
            row.id, row.userId, row.companyId, row.type, row.severity, row.title,
            row.content, row.targetUrl, row.sourceType, row.sourceId,
            row.readAt, row.createdAt, row.updatedAt
        );
    }

    private Map<String, Object> payload(OutboxEvent event) {
        if (event.payloadJson == null || event.payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(event.payloadJson, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private NotificationPreference preferenceFor(long userId) {
        NotificationMapper.PreferenceRow row = mapper.preference(userId);
        if (row != null) {
            return preferenceFrom(row);
        }
        String now = OffsetDateTime.now().toString();
        mapper.createDefaultPreference(userId, now);
        return new NotificationPreference(userId, true, false, "generic", null, "info", List.of(), now, now);
    }

    private NotificationPreference preferenceFrom(NotificationMapper.PreferenceRow row) {
        return new NotificationPreference(
            row.userId, row.enabled, row.webhookEnabled,
            normalizeProvider(row.webhookProvider), row.webhookUrl,
            normalizeSeverity(row.minSeverity), parseTypeCsv(row.mutedTypes),
            row.createdAt, row.updatedAt
        );
    }

    private String receiptSeverity(Map<String, Object> payload) {
        String status = text(payload.get("status"), "").toLowerCase(Locale.ROOT);
        if ("rejected".equals(status)) {
            return "critical";
        }
        if ("verified".equals(status) || "archived".equals(status) || "linked".equals(status)) {
            return "success";
        }
        return "info";
    }

    private boolean taxSettled(TaxItem item) {
        return "paid".equals(item.status) || "paid".equals(item.paymentStatus);
    }

    private boolean receiptClosed(ReceiptVoucher voucher) {
        return "archived".equals(voucher.status) || "linked".equals(voucher.status) || "verified".equals(voucher.status);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean bool(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private boolean boolValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private boolean severityEnabled(String severity, String minSeverity) {
        return severityRank(normalizeSeverity(severity)) >= severityRank(normalizeSeverity(minSeverity));
    }

    private int severityRank(String severity) {
        return switch (normalizeSeverity(severity)) {
            case "success", "info" -> 1;
            case "warning" -> 2;
            case "critical" -> 3;
            default -> 1;
        };
    }

    private String normalizeSeverity(String severity) {
        return switch (severity == null ? "" : severity) {
            case "success", "info", "warning", "critical" -> severity;
            default -> "info";
        };
    }

    private String normalizeProvider(String provider) {
        return switch (provider == null ? "" : provider) {
            case "feishu", "wecom" -> provider;
            default -> "generic";
        };
    }

    private List<String> parseTypes(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        }
        return parseTypeCsv(value == null ? "" : String.valueOf(value));
    }

    private List<String> parseTypeCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safeWebhookUrl(String value) {
        try {
            return webhookUrlValidator.requireSafeUrl(value).toASCIIString();
        } catch (WebhookUrlValidator.UnsafeWebhookUrlException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private record NotificationDraft(
        long companyId,
        String type,
        String severity,
        String title,
        String content,
        String targetUrl,
        String sourceType,
        Long sourceId,
        String dedupeKey
    ) {
    }

    public record NotificationView(
        long id,
        long userId,
        long companyId,
        String type,
        String severity,
        String title,
        String content,
        String targetUrl,
        String sourceType,
        Long sourceId,
        String readAt,
        String createdAt,
        String updatedAt
    ) {
    }

    public record NotificationPreference(
        long userId,
        boolean enabled,
        boolean webhookEnabled,
        String webhookProvider,
        String webhookUrl,
        String minSeverity,
        List<String> mutedTypes,
        String createdAt,
        String updatedAt
    ) {
    }
}
