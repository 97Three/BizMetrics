package com.bizmetrics.approval.domain;

/** Append-only action in an approval request's audit trail. */
public record ApprovalAction(
    long id,
    long requestId,
    long actorUserId,
    String action,
    String comment,
    String createdAt
) {
}
