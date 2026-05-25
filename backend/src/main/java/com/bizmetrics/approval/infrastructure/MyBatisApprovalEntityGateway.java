package com.bizmetrics.approval.infrastructure;

import com.bizmetrics.approval.application.ApprovalEntityGateway;
import com.bizmetrics.evidence.domain.ReceiptVoucher;
import com.bizmetrics.evidence.application.ReceiptApprovalStatusService;
import com.bizmetrics.evidence.application.ReceiptVoucherRepository;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.service.support.AccessControlService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** PostgreSQL-backed adapter for entities referenced by the approval workflow. */
@Component
public class MyBatisApprovalEntityGateway implements ApprovalEntityGateway {
    private final ApprovalEntityReferenceMapper mapper;
    private final ReceiptVoucherRepository receiptVouchers;
    private final ReceiptApprovalStatusService receiptApprovalStatuses;
    private final AccessControlService accessControl;

    public MyBatisApprovalEntityGateway(
        ApprovalEntityReferenceMapper mapper,
        ReceiptVoucherRepository receiptVouchers,
        ReceiptApprovalStatusService receiptApprovalStatuses,
        AccessControlService accessControl
    ) {
        this.mapper = mapper;
        this.receiptVouchers = receiptVouchers;
        this.receiptApprovalStatuses = receiptApprovalStatuses;
        this.accessControl = accessControl;
    }

    @Override
    public void validateReference(User applicant, long companyId, String entityType, Long entityId) {
        if (entityId == null || "other".equals(entityType)) return;
        if ("receipt_voucher".equals(entityType)) {
            validateReceipt(applicant, companyId, entityId);
            return;
        }

        int matches = switch (entityType) {
            case "transaction" -> mapper.countTransactions(entityId, companyId);
            case "budget" -> mapper.countBudgets(entityId, companyId);
            case "employee" -> mapper.countEmployees(entityId, companyId);
            case "payroll_run" -> mapper.countPayrollRuns(entityId, companyId);
            default -> throw new IllegalArgumentException("Unsupported approval entity type: " + entityType);
        };
        if (matches == 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                entityLabel(entityType) + " is outside the selected company"
            );
        }
    }

    @Override
    public void synchronizeStatus(String authorization, String entityType, Long entityId, String status) {
        if ("receipt_voucher".equals(entityType) && entityId != null) {
            receiptApprovalStatuses.updateApprovalStatus(authorization, entityId, status);
        }
    }

    private void validateReceipt(User applicant, long companyId, long entityId) {
        ReceiptVoucher voucher = receiptVouchers.findById(entityId).orElse(null);
        if (voucher == null || voucher.companyId != companyId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt voucher is outside the selected company");
        }
        if (!accessControl.hasFinanceManagerRole(applicant, companyId) && voucher.operatorUserId != applicant.id) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only the submitter or a finance manager can submit this receipt"
            );
        }
    }

    private String entityLabel(String entityType) {
        return switch (entityType) {
            case "transaction" -> "Transaction";
            case "budget" -> "Budget";
            case "employee" -> "Employee";
            case "payroll_run" -> "Payroll run";
            default -> "Entity";
        };
    }
}
