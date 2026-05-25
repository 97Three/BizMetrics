package com.bizmetrics.approval.api;

import com.bizmetrics.approval.domain.ApprovalAction;
import com.bizmetrics.approval.domain.ApprovalRequest;
import java.util.List;

/** Public approval response with its append-only action trail. */
public record ApprovalDetail(ApprovalRequest request, List<ApprovalAction> actions) {
}
